package com.simge.adminbackend.staff;

import java.security.SecureRandom;

/**
 * Yeni hesap açılırken üretilen geçici parola (ADR D-123).
 *
 * <p>
 * <b>Neden yönetici kendi yazmıyor:</b> elle yazılan geçici parolalar her yerde
 * aynı kalıba düşüyor — {@code Simge2026}, {@code 123456}, firma adı + yıl. Bu
 * parola bir süre boyunca gerçek bir parola olarak duruyor, dolayısıyla üretimi
 * makineye bırakmak hem daha hızlı hem daha güvenli. Yöneticinin tek işi
 * ekranda beliren dizeyi kişiye söylemek.
 * </p>
 *
 * <p>
 * <b>Neden bu alfabe:</b> parola telefonda ya da yüz yüze <i>sözlü</i>
 * aktarılacak. Karışan karakterler ({@code 0/O}, {@code 1/l/I}) çıkarıldı;
 * kalan 31 karakterlik alfabe, 12 haneyle yaklaşık 59 bit entropi veriyor —
 * geçici ve tek kullanımlık bir parola için fazlasıyla yeterli. Okunurluk için
 * dörderli gruplanıp tire ile ayrılıyor ({@code hkm7-2qtp-9wvx}); tireler
 * parolanın parçası.
 * </p>
 *
 * <p>
 * Bu parola veritabanına <b>BCrypt özeti olarak</b> yazılır ve hiçbir yerde
 * açık saklanmaz. Yönetici ekranda bir kez görür; kaybederse yeniden üretilir.
 * </p>
 */
public final class TempPassword {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Karışabilen karakterler yok: i, l, o, 0, 1. */
    private static final char[] ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    private static final int GROUPS = 3;
    private static final int GROUP_SIZE = 4;

    private TempPassword() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(GROUPS * GROUP_SIZE + GROUPS - 1);
        for (int g = 0; g < GROUPS; g++) {
            if (g > 0) {
                sb.append('-');
            }
            for (int i = 0; i < GROUP_SIZE; i++) {
                sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
            }
        }
        return sb.toString();
    }
}
