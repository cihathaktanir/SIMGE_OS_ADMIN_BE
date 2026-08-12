package com.simge.adminbackend.staff;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Panel girişi / çıkışı / oturum bilgisi.
 *
 * <p>
 * <b>Giriş kimliği kullanıcı adıdır, e-posta değil</b> (ADR D-123). Panel
 * kullanıcıları Simge çalışanları; hepsinin kurumsal e-posta adresi olmak
 * zorunda değil ve hesabı olmayan bir adrese bağlamak kimseye fayda sağlamıyor.
 * </p>
 *
 * <p>
 * Token dönülmez. Kimlik, tarayıcıya {@code HttpOnly} oturum cookie'si olarak
 * verilir ({@code SIMGE_ADMIN_SESSION}); JavaScript okuyamaz.
 * </p>
 */
@Tag(name = "Panel kimlik doğrulama",
        description = "Kullanıcı adı + parola ile giriş, çıkış, oturum bilgisi ve parola değiştirme.")
@RestController
@RequestMapping("/api/auth")
public class StaffAuthController {

    private final StaffAuthService authService;
    private final StaffPasswordService passwordService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public StaffAuthController(StaffAuthService authService,
            StaffPasswordService passwordService) {
        this.authService = authService;
        this.passwordService = passwordService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record PasswordChangeRequest(
            @NotBlank String current_password,
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String new_password) {
    }

    @Operation(summary = "Giriş yap",
            description = """
                    Yanıt kodları:
                    - **200** giriş başarılı. `must_change_password: true` ise kullanıcı
                      geçici parolayla girmiştir ve parolasını değiştirene kadar
                      panelin geri kalanı 403 döner.
                    - **401** kullanıcı adı veya parola hatalı / hesap pasif / hesabın
                      hiç rolü yok (hangisi olduğu bilerek ayırt edilmez)
                    - **429** arka arkaya 5 hatalı denemeden sonra hesap 15 dk kilitli""")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            StaffPrincipal principal =
                    authService.authenticate(body.username(), body.password());

            // Oturum sabitleme saldırısına karşı: varsa eski oturumu düşür.
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }
            request.getSession(true);

            principal.eraseCredentials();
            saveContext(principal, request, response);

            return ResponseEntity.ok(toDto(principal));

        } catch (StaffAuthService.AccountLockedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "account_locked", "until", String.valueOf(e.getUntil())));
        } catch (StaffAuthService.InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_credentials"));
        }
    }

    @Operation(summary = "Çıkış yap", description = "Oturumu sunucuda sonlandırır. Oturum yoksa da 204 döner.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Oturumdaki personel",
            description = "Giriş yapılmışsa kullanıcı bilgisini ve rollerini, yapılmamışsa 401 döner.")
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        StaffPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toDto(principal));
    }

    @Operation(summary = "Parolamı değiştir",
            description = """
                    İlk girişteki zorunlu değişiklik de buradan yapılır. Başarılı olunca
                    `must_change_password` iner ve **diğer cihazlardaki oturumlar düşer**;
                    işlemi yapan oturum açık kalır.

                    Yanıt kodları:
                    - **200** değiştirildi, güncel kullanıcı bilgisi döner
                    - **400** yeni parola kurallara uymuyor (`violation` alanında sebep)
                    - **401** oturum yok
                    - **409** mevcut parola hatalı, ya da yeni parola eskisiyle aynı""")
    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest body,
            HttpServletRequest request, HttpServletResponse response) {

        StaffPrincipal principal = principal(authentication);
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        HttpSession session = request.getSession(false);
        String keepSessionId = session == null ? null : session.getId();

        try {
            StaffPrincipal refreshed = passwordService.change(
                    principal.getId(), body.current_password(), body.new_password(),
                    keepSessionId);

            // Oturumdaki nesne tazelenmezse must_change_password bayrağı açık
            // kalır ve kullanıcı parolasını değiştirdiği hâlde panele giremez.
            refreshed.eraseCredentials();
            saveContext(refreshed, request, response);

            return ResponseEntity.ok(toDto(refreshed));

        } catch (StaffPasswordService.WrongCurrentPasswordException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "wrong_current_password"));
        } catch (StaffPasswordService.SamePasswordException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "same_password"));
        } catch (StaffPasswordService.WeakPasswordException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "weak_password",
                    "violation", e.getViolation().name().toLowerCase(Locale.ROOT)));
        } catch (StaffPasswordService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private void saveContext(StaffPrincipal principal, HttpServletRequest request,
            HttpServletResponse response) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private StaffPrincipal principal(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private Map<String, Object> toDto(StaffPrincipal principal) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", principal.getId());
        dto.put("username", principal.getUsername());
        dto.put("full_name", principal.getFullName());
        dto.put("roles", principal.getRoles());
        dto.put("must_change_password", principal.isMustChangePassword());
        return dto;
    }
}
