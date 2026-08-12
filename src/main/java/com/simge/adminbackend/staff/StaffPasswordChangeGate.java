package com.simge.adminbackend.staff;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Geçici parolayla açılan hesap, parolasını değiştirene kadar panelin geri
 * kalanına giremez (ADR D-123).
 *
 * <p>
 * <b>Neden sunucuda, arayüzde değil:</b> "ilk girişte parola değiştir" ekranı
 * arayüzde yönlendirmeyle de yapılabilirdi ama o yalnızca tarayıcıyı ikna eder.
 * Geçici parola en az iki kişinin bildiği bir paroladır (hesabı açan yönetici
 * ve kullanıcı); o parolayla API'ye doğrudan istek atılabildiği sürece bayrak
 * süs olurdu.
 * </p>
 *
 * <p>
 * Açık bırakılan tek yüzey {@code /api/auth/**}: kullanıcı kim olduğunu
 * sorabilmeli ({@code /me}), parolasını değiştirebilmeli ({@code /password}) ve
 * çıkabilmeli ({@code /logout}). Parola değişince
 * {@link StaffPasswordService} oturumdaki nesneyi tazeliyor ve bayrak iniyor.
 * </p>
 *
 * <p>
 * {@code @Component} <b>değil</b>: bean olsaydı Spring Boot bunu servlet
 * zincirine de kaydeder ve filtre iki kez çalışırdı. Yerine
 * {@code SecurityConfig} içinde yetkilendirme filtresinden hemen sonra elle
 * takılıyor — kimlik doğrulanmadan {@code SecurityContextHolder} zaten boş
 * olurdu.
 * </p>
 */
public class StaffPasswordChangeGate extends OncePerRequestFilter {

    private static final String BODY =
            "{\"error\":\"password_change_required\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof StaffPrincipal principal
                && principal.isMustChangePassword()
                && !isAllowedWhilePasswordChangePending(request)) {

            // Gövde önemli: arayüz "parola değiştirmen gerekiyor" ile "yetkin
            // yok" ayrımını buradan yapıyor. İkisi de 403 döndüğü için durum
            // kodu tek başına yetmez.
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(BODY);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedWhilePasswordChangePending(HttpServletRequest request) {
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        return path.startsWith("/api/auth/");
    }
}
