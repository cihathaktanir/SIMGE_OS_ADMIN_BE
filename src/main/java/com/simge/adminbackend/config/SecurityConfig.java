package com.simge.adminbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.simge.adminbackend.staff.StaffPasswordChangeGate;

/**
 * Panel güvenliği (ADR D-122 / D-123).
 *
 * <p>
 * <b>Panel tamamen girişe kapalı.</b> Açık olan tek uçlar: giriş, sağlık ve
 * (yapılandırma açıksa) API dokümantasyonu. Yetkilendirme rol bazlı ve
 * sunucuda: {@code @PreAuthorize} ile uç uç uygulanıyor, arayüzde menü
 * gizlemek yeterli sayılmıyor.
 * </p>
 *
 * <p>
 * <b>Bu servis intranette çalışır</b> ve ileride ERP'ye yazacak. Buradaki
 * kontroller, ağ seviyesindeki kısıtın yerine geçmez — onu tamamlar. Panel
 * internete açılırsa bu dosyadaki hiçbir kural o kararı güvenli hale getirmez.
 * </p>
 *
 * <p>
 * Oturum sunucuda (Spring Session JDBC, {@code SIMGE_STAFF_SESSION}), JWT
 * değil: bir personelin yetkisi alındığında ya da hesabı kapatıldığında bir
 * sonraki istekte düşsün diye.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // Spring Security 6'da CSRF token'ı varsayılan olarak tembel çözülür ve
        // SPA'ler için BREACH koruması istekte ekstra işlem ister; SPA senaryosunda
        // öznitelik adını null bırakmak dokümante edilen çözümdür.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            // Angular HttpClient XSRF-TOKEN cookie'sini okuyup X-XSRF-TOKEN başlığı
            // gönderir; withHttpOnlyFalse tam olarak bunun için.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))

            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))

            .authorizeHttpRequests(auth -> auth
                // Kamuya açık tek yüzey: giriş ve sağlık ucu.
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // API dokümantasyonu. Uçların kendisi korumalı kalmaya devam
                // ediyor; açık olan yalnızca şema ve arayüz. Canlıda tamamen
                // kapatmak için springdoc.api-docs.enabled=false.
                .requestMatchers("/", "/swagger-ui.html", "/swagger-ui/**",
                        "/v3/api-docs", "/v3/api-docs/**").permitAll()
                // Geri kalan her şey oturum ister; rol denetimi @PreAuthorize'da.
                .anyRequest().authenticated())

            // Geçici parolayla giren kullanıcı, parolasını değiştirene kadar
            // yalnızca /api/auth/** kullanabilir. Yetkilendirmeden SONRA
            // takılıyor: o noktada kimlik çözülmüş ve SecurityContext dolu.
            .addFilterAfter(new StaffPasswordChangeGate(), AuthorizationFilter.class)

            // API istemcisi: giriş sayfasına yönlendirme yok, 401 dön.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable()); // çıkış StaffAuthController'da

        return http.build();
    }
}
