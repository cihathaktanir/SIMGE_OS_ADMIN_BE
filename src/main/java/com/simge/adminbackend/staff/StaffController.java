package com.simge.adminbackend.staff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simge.adminbackend.appdb.model.StaffUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Simge personelinin yönetimi (ADR D-123). Tamamı {@code ROLE_ADMIN}.
 *
 * <p>
 * <b>Geçici parola yalnızca oluşturma/sıfırlama yanıtında, bir kez döner.</b>
 * Listeleme uçlarında parola alanı yok; veritabanında da yalnızca BCrypt özeti
 * duruyor. Yönetici parolayı kaybederse tek yol sıfırlamak — bu bilinçli, aksi
 * halde parolayı okunabilir bir yerde tutmamız gerekirdi.
 * </p>
 */
@Tag(name = "Personel", description = "Simge çalışanlarının hesapları ve rolleri. ROLE_ADMIN gerektirir.")
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('ADMIN')")
public class StaffController {

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    public record CreateRequest(
            @NotBlank @Size(max = UsernamePolicy.MAX_LENGTH) String username,
            @Size(max = 150) String full_name,
            @Size(max = 190) String email,
            @NotEmpty Set<String> roles) {
    }

    public record RolesRequest(@NotEmpty Set<String> roles) {
    }

    public record ProfileRequest(
            @Size(max = 150) String full_name,
            @Size(max = 190) String email) {
    }

    public record StatusRequest(boolean active) {
    }

    @Operation(summary = "Personel listesi")
    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (StaffUser user : staffService.list()) {
            data.add(toDto(user));
        }
        return ResponseEntity.ok(Map.of("data", data, "total", data.size()));
    }

    @Operation(summary = "Tanımlı roller",
            description = "Arayüzün rol listesini kodda sabitlememesi için.")
    @GetMapping("/roles")
    public ResponseEntity<Map<String, Object>> roles() {
        return ResponseEntity.ok(Map.of("data", List.copyOf(StaffUser.ALL_ROLES)));
    }

    @Operation(summary = "Yeni personel hesabı",
            description = """
                    Hesabı anında açar ve **geçici parolayı bir kez** döner
                    (`temporary_password`). E-posta gönderilmez; parolayı kişiye
                    yönetici iletir. Kullanıcı ilk girişinde parolasını değiştirmeden
                    panelde hiçbir şey yapamaz.

                    Yanıt kodları:
                    - **201** açıldı
                    - **400** kullanıcı adı kurallara uymuyor / rol listesi geçersiz
                    - **409** bu kullanıcı adı alınmış""")
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(Authentication authentication,
            @Valid @RequestBody CreateRequest body) {

        StaffPrincipal admin = (StaffPrincipal) authentication.getPrincipal();
        try {
            StaffService.CreatedAccount created = staffService.create(
                    admin.getId(), body.username(), body.full_name(), body.email(), body.roles());

            Map<String, Object> dto = toDto(created.user());
            dto.put("temporary_password", created.temporaryPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (StaffService.UsernameTakenException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "username_taken"));
        } catch (StaffService.InvalidUsernameException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_username",
                    "violation", e.getViolation().name().toLowerCase(Locale.ROOT)));
        } catch (StaffService.NoRolesException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_roles"));
        } catch (StaffService.UnknownRoleException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "unknown_role", "role", e.getRole()));
        }
    }

    @Operation(summary = "Rolleri değiştir",
            description = "Son aktif yöneticinin ADMIN rolü alınamaz (**409 last_admin**).")
    @PostMapping("/{id}/roles")
    public ResponseEntity<Map<String, Object>> updateRoles(@PathVariable("id") Long id,
            @Valid @RequestBody RolesRequest body) {
        try {
            return ResponseEntity.ok(toDto(staffService.updateRoles(id, body.roles())));
        } catch (StaffService.NotFoundException e) {
            return notFound();
        } catch (StaffService.LastAdminException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "last_admin"));
        } catch (StaffService.NoRolesException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "no_roles"));
        } catch (StaffService.UnknownRoleException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "unknown_role", "role", e.getRole()));
        }
    }

    @Operation(summary = "Ad / e-posta güncelle",
            description = "Kullanıcı adı değişmez — giriş kimliği odur.")
    @PostMapping("/{id}/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(@PathVariable("id") Long id,
            @Valid @RequestBody ProfileRequest body) {
        try {
            return ResponseEntity.ok(
                    toDto(staffService.updateProfile(id, body.full_name(), body.email())));
        } catch (StaffService.NotFoundException e) {
            return notFound();
        }
    }

    @Operation(summary = "Hesabı aç / kapat",
            description = """
                    Kapatmak açık oturumları da düşürür. Hesap **silinmez**: kim ne
                    yaptı kaydı, hesap kaybolunca anlamını yitirir.

                    Son aktif yönetici kapatılamaz (**409 last_admin**).""")
    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> setStatus(@PathVariable("id") Long id,
            @RequestBody StatusRequest body) {
        try {
            return ResponseEntity.ok(toDto(staffService.setStatus(id, body.active())));
        } catch (StaffService.NotFoundException e) {
            return notFound();
        } catch (StaffService.LastAdminException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "last_admin"));
        }
    }

    @Operation(summary = "Parolayı sıfırla",
            description = """
                    Yeni **geçici parolayı bir kez** döner. Personelin e-posta adresi
                    olmak zorunda olmadığı için "bana bağlantı gönder" akışı yoktur;
                    sıfırlamayı yönetici yapar ve parolayı kişiye kendisi iletir.

                    Hesabın açık oturumları düşer.""")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable("id") Long id) {
        try {
            StaffService.CreatedAccount reset = staffService.resetPassword(id);
            Map<String, Object> dto = toDto(reset.user());
            dto.put("temporary_password", reset.temporaryPassword());
            return ResponseEntity.ok(dto);
        } catch (StaffService.NotFoundException e) {
            return notFound();
        }
    }

    private ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
    }

    private Map<String, Object> toDto(StaffUser user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("full_name", user.getFullName());
        dto.put("email", user.getEmail());
        dto.put("roles", user.getRoles());
        dto.put("status", user.getStatus());
        dto.put("must_change_password", user.isMustChangePassword());
        dto.put("last_login_at", user.getLastLoginAt());
        dto.put("created_at", user.getCreatedAt());
        return dto;
    }
}
