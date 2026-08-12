package com.simge.adminbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * API dokümantasyonu (Swagger UI) — {@code http://localhost:8081/}.
 *
 * <p>
 * <b>Canlıda kapatın:</b> {@code springdoc.api-docs.enabled=false}. Panel zaten
 * intranette ama tam API haritasını orada bile açıkta bırakmanın bir faydası
 * yok.
 * </p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.servlet.session.cookie.name:SIMGE_ADMIN_SESSION}")
    private String sessionCookieName;

    @Bean
    public OpenAPI simgeAdminOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Simge Yönetim API")
                        .version("v1")
                        .description("""
                                Simge Online Selling yönetim paneli API'si. **İntranet** — \
                                internete açılmaz (ADR D-122).

                                Panel tamamen girişe kapalıdır: `/api/auth/login` ve \
                                `/actuator/health` dışındaki her uç oturum ister.

                                Denemek için sırayla:
                                1. `POST /api/auth/login` — gövde: `{"username": "...", "password": "..."}`
                                2. Geçici parolayla girdiyseniz yanıtta `must_change_password: true` gelir \
                                ve diğer uçlar **403 password_change_required** döner; önce \
                                `POST /api/auth/password` ile parolanızı değiştirin.
                                3. `POST /api/auth/logout` ile oturumu kapatın.

                                ⚠️ Mikro ERP veritabanına şu an **hiçbir uç yazmaz** — repository'ler \
                                `ReadOnlyRepository` üzerinden türüyor, yazmak derleme hatası. \
                                Sınırlı yazma ileride, tablo tablo açılacak."""))
                .components(new Components()
                        .addSecuritySchemes("oturum", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(sessionCookieName)
                                .description("""
                                        Giriş sonrası sunucunun verdiği HttpOnly oturum cookie'si. \
                                        Tarayıcı kendiliğinden gönderdiği için buraya elle bir değer \
                                        girmenize gerek yok — sadece /api/auth/login'i çalıştırın.""")));
    }
}
