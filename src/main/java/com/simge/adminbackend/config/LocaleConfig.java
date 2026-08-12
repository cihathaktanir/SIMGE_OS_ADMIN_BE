package com.simge.adminbackend.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * İstek dilinin çözümlenmesi.
 *
 * <p>
 * Panelin kendi arayüzü yalnızca Türkçe, ama <b>gönderdiği e-posta</b> müşteriye
 * gidiyor ve onun dilinde olmalı — bu yüzden dil çözümlemesi burada da gerekli.
 * </p>
 *
 * <p>
 * <b>Desteklenen diller açıkça sınırlı.</b> Varsayılan
 * {@link AcceptHeaderLocaleResolver} tarayıcının gönderdiği her şeyi kabul eder;
 * liste verilmezse Almanca bir tarayıcıdan gelen istek {@code de} çözümlenir,
 * çeviri bulunamaz ve mesaj anahtarları ham haliyle e-postaya girerdi.
 * Tanınmayan dil Türkçeye düşer.
 * </p>
 */
@Configuration
public class LocaleConfig {

    public static final Locale TURKISH = Locale.forLanguageTag("tr");
    public static final Locale ENGLISH = Locale.ENGLISH;

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(TURKISH, ENGLISH));
        resolver.setDefaultLocale(TURKISH);
        return resolver;
    }
}
