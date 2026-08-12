package com.simge.adminbackend.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Davet token'ı üretimi ve özetlemesi.
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın kopyası. <b>Özetleme algoritması iki
 * tarafta birebir aynı olmak zorunda</b>: token'ı panel üretiyor ve
 * {@code SIMGE_COMPANY_INVITATION.token_hash} sütununa yazıyor, kabul akışında
 * vitrin aynı özeti hesaplayıp satırı buluyor. Buradaki bir değişiklik,
 * gönderilmiş bütün davet bağlantılarını sessizce geçersiz kılar.
 * </p>
 *
 * <p>
 * <b>Neden BCrypt değil:</b> parolalar için BCrypt kullanıyoruz çünkü insan
 * seçimi olduklarından tahmin edilebilirler ve yavaş özet gerekir. Buradaki
 * değerler makine üretimi ve yüksek entropili (token 256 bit, OTP 6 hane +
 * 5 deneme sınırı + 10 dk ömür), dolayısıyla sözlük saldırısı anlamsız. SHA-256
 * yeterli ve doğrulama sabit maliyetli — kayıt uçları girişe kapalı olmadığı
 * için her isteğe BCrypt maliyeti bindirmek DoS yüzeyi açardı.
 * </p>
 */
public final class SecretCodes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private SecretCodes() {
    }

    /** 6 haneli sayısal kod; baştaki sıfırlar korunur. */
    public static String numericOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** URL'de taşınabilir 256 bitlik token. */
    public static String urlToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** SHA-256, küçük harf hex (64 karakter — şemadaki CHAR(64) ile birebir). */
    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JVM'de zorunlu; buraya düşmek imkânsız.
            throw new IllegalStateException("SHA-256 yok", e);
        }
    }

    /**
     * Sabit süreli karşılaştırma.
     *
     * <p>
     * Özetler gizli sayılmasa da erken çıkan {@code equals} zamanlama sızıntısına
     * kapı aralar; maliyeti sıfır olan bir önlem.
     * </p>
     */
    public static boolean matches(String rawValue, String expectedHash) {
        if (rawValue == null || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawValue).getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
