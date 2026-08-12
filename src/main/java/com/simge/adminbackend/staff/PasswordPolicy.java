package com.simge.adminbackend.staff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Parola kuralları — vitrin backend'indeki {@code PasswordPolicy}'nin kopyası
 * (orada ADR D-113; burada D-123).
 *
 * <p>
 * Kasıtlı kopya: iki servisin parola kuralı <b>aynı</b> olmalı ama biri
 * diğerinin sürümüne bağlı olmamalı. Kural değişirse iki dosya da elle
 * güncellenir; bunu paylaşılan bir kütüphaneye çıkarmak, iki servisi tek bir
 * sürüm takvimine bağlardı.
 * </p>
 *
 * <p>
 * <b>Karakter türü zorunluluğu (büyük harf + rakam + sembol) BİLEREK YOK.</b>
 * Sezgiye ters gelse de bu, güvenliği artırmıyor: NIST SP 800-63B ve OWASP ASVS
 * ikisi de bu kuralları önermekten vazgeçti. Sebebi, insanların kurala uyarken
 * hep aynı kalıba düşmesi — ilk harf büyük, sonuna rakam, en sona ünlem:
 * {@code Parola1!}, {@code Simge2024!}. Bu kalıplar saldırganın sözlüğünde ilk
 * sıralarda; kural, parolayı tahmin edilebilir yapıyor ve kullanıcıyı da
 * parolayı bir yere yazmaya itiyor.
 * </p>
 *
 * <p>
 * Gerçekten işe yarayanlar burada uygulanıyor:
 * </p>
 * <ol>
 *   <li><b>Uzunluk</b> — entropiyi asıl belirleyen bu. Alt sınır {@value #MIN_LENGTH}.</li>
 *   <li><b>Yaygın parola listesi</b> — saldırgan sözlükten başlar; sözlükte olanı
 *       baştan reddetmek, karakter türü kuralından çok daha etkili.</li>
 *   <li><b>Kişisel bilgi kontrolü</b> — e-posta ya da ad soyaddan türetilmiş
 *       parolalar hedefli saldırıda ilk denenenlerdir.</li>
 *   <li><b>Basit kalıp kontrolü</b> — tek karakterin tekrarı, ardışık diziler.</li>
 * </ol>
 *
 * <p>
 * Bunların üstüne çevrimiçi deneme zaten sınırlı: 5 hatalı girişte hesap 15 dk
 * kilitleniyor ({@code StaffLoginAttemptService}) ve parolalar BCrypt ile
 * saklanıyor.
 * </p>
 */
@Component
public class PasswordPolicy {

    private static final Logger log = LoggerFactory.getLogger(PasswordPolicy.class);

    /** Alt sınır. 8 yaygın bir varsayılan ama artık zayıf kabul ediliyor. */
    public static final int MIN_LENGTH = 10;

    /** Üst sınır: parola yöneticisi kullananlar uzun parola üretir, engellemeyelim. */
    public static final int MAX_LENGTH = 100;

    /** Kişisel bilgi kontrolünde bu uzunluktan kısa parçalar yok sayılır. */
    private static final int MIN_PERSONAL_TOKEN = 4;

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this.commonPasswords = loadCommonPasswords();
    }

    /** İhlal türleri; arayüz kullanıcıya ne yapması gerektiğini söyleyebilsin diye ayrı. */
    public enum Violation {
        TOO_SHORT,
        TOO_LONG,
        TOO_COMMON,
        CONTAINS_PERSONAL_INFO,
        TOO_SIMPLE
    }

    /**
     * Parolayı denetler.
     *
     * @param password parola
     * @param email    hesabın kimliği (null olabilir). Panelde bu, e-posta
     *                 değil <b>kullanıcı adıdır</b>; {@code @} içermediği için
     *                 dizenin tamamı kişisel bilgi olarak değerlendirilir —
     *                 yani "depo1" kullanıcısı "depo12345" parolasını seçemez.
     * @param fullName ad soyad (null olabilir)
     * @return ilk bulunan ihlal, sorun yoksa {@code null}
     */
    public Violation validate(String password, String email, String fullName) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Violation.TOO_SHORT;
        }
        if (password.length() > MAX_LENGTH) {
            return Violation.TOO_LONG;
        }

        String normalized = fold(password);

        if (commonPasswords.contains(normalized)) {
            return Violation.TOO_COMMON;
        }
        // "parola123" gibi sonuna rakam eklenmiş varyantlar da yakalansın.
        String stripped = normalized.replaceAll("[0-9!.'*\\-_]+$", "");
        if (stripped.length() >= 4 && commonPasswords.contains(stripped)) {
            return Violation.TOO_COMMON;
        }

        if (containsPersonalInfo(normalized, email, fullName)) {
            return Violation.CONTAINS_PERSONAL_INFO;
        }

        if (isTooSimple(normalized)) {
            return Violation.TOO_SIMPLE;
        }

        return null;
    }

    /**
     * Karşılaştırma için normalleştirme: küçük harf + Türkçe harfleri ASCII karşılığı.
     *
     * <p>
     * Türkçe katlaması şart. "Yılmaz" soyadlı biri parolasına klavyeden çıkması
     * kolay olan "yilmaz"ı yazar (noktasız ı yerine i); katlama olmadan bu iki
     * dize eşleşmez ve kişisel bilgi kontrolü sessizce boşa çıkar. Aynı şey
     * yaygın parola listesi için de geçerli: "şifre" ile "sifre" tek kayıtla
     * yakalanır.
     * </p>
     *
     * <p>
     * Küçültme öncesi elle değiştiriliyor çünkü {@code "İ".toLowerCase(ROOT)}
     * birleşik nokta içeren iki karakterlik bir dize üretiyor.
     * </p>
     */
    private String fold(String value) {
        String s = value
                .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
                .replace('Ş', 's').replace('ş', 's')
                .replace('Ğ', 'g').replace('ğ', 'g')
                .replace('Ü', 'u').replace('ü', 'u')
                .replace('Ö', 'o').replace('ö', 'o')
                .replace('Ç', 'c').replace('ç', 'c');
        return s.toLowerCase(Locale.ROOT);
    }

    /** E-postanın yerel kısmı ya da isimdeki parçalar parolada geçiyor mu. */
    private boolean containsPersonalInfo(String normalized, String email, String fullName) {
        if (email != null && !email.isBlank()) {
            int at = email.indexOf('@');
            String local = fold(at > 0 ? email.substring(0, at) : email);
            if (local.length() >= MIN_PERSONAL_TOKEN && normalized.contains(local)) {
                return true;
            }
        }

        if (fullName != null && !fullName.isBlank()) {
            for (String part : fold(fullName).split("\\s+")) {
                if (part.length() >= MIN_PERSONAL_TOKEN && normalized.contains(part)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Tek karakterin tekrarı ya da tamamen ardışık dizi. */
    private boolean isTooSimple(String normalized) {
        if (normalized.chars().distinct().count() <= 2) {
            return true;
        }

        boolean artan = true;
        boolean azalan = true;
        for (int i = 1; i < normalized.length(); i++) {
            int fark = normalized.charAt(i) - normalized.charAt(i - 1);
            if (fark != 1) {
                artan = false;
            }
            if (fark != -1) {
                azalan = false;
            }
        }
        return artan || azalan;
    }

    /**
     * Yaygın parola listesini kaynak dosyadan okur.
     *
     * <p>
     * Liste kodda değil dosyada: büyütmek için yeniden derleme gerekmesin.
     * Dosya bulunamazsa uygulama açılmaya devam eder — parola kontrolünün
     * kalan üç kuralı yine çalışır, ama durum log'a yazılır.
     * </p>
     */
    private Set<String> loadCommonPasswords() {
        Set<String> set = new HashSet<>();
        ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");

        if (!resource.exists()) {
            log.warn("security/common-passwords.txt bulunamadı; yaygın parola kontrolü devre dışı.");
            return set;
        }

        try (InputStream in = resource.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = fold(line.trim());
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    set.add(trimmed);
                }
            }
        } catch (IOException e) {
            log.warn("Yaygın parola listesi okunamadı; kontrol devre dışı.", e);
        }

        log.info("Yaygın parola listesi yüklendi: {} kayıt", set.size());
        return set;
    }
}
