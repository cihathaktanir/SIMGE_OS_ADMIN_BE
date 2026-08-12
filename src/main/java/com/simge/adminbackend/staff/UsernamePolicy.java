package com.simge.adminbackend.staff;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Kullanıcı adı kuralları (ADR D-123).
 *
 * <p>
 * Personel hesabı e-postayla değil kısa bir kullanıcı adıyla açılıyor — panel
 * yöneticisi "depo1", "ayse", "muhasebe.merve" yazıp hesabı anında kurabilsin
 * diye. Kural kümesi bilerek dar:
 * </p>
 *
 * <ul>
 *   <li><b>Yalnızca ASCII.</b> Türkçe karakter kabul edilmiyor. Kullanıcı adı
 *       giriş ekranında elle yazılan bir alan; "şevval" ile "sevval" ya da
 *       noktasız/noktalı i ayrımı, kimsenin hatırlamadığı bir destek yükünden
 *       başka bir şey üretmiyor. Ad soyad zaten ayrı alanda ve orada Türkçe
 *       serbest.</li>
 *   <li><b>Küçük harfe indirgenir.</b> "Depo1" ile "depo1" aynı hesap olsun;
 *       giriş sırasında büyük harf hatası yüzünden "kullanıcı yok" almasın.</li>
 *   <li><b>Ayrılmış adlar</b> reddedilir: {@code admin} dışındaki
 *       {@code root}/{@code system} gibi adlar, log okurken gerçek bir kişiyle
 *       karışmasın.</li>
 * </ul>
 */
@Component
public class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    /** Harf/rakamla başlar, aralarda nokta-tire-alt çizgi, harf/rakamla biter. */
    private static final Pattern SHAPE =
            Pattern.compile("^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$");

    /** Sistem/rol çağrıştıran, kişiye verilmemesi gereken adlar. */
    private static final Set<String> RESERVED =
            Set.of("root", "system", "sistem", "postmaster", "hostmaster", "webmaster",
                    "null", "undefined", "anonymous", "guest", "misafir");

    public enum Violation {
        TOO_SHORT,
        TOO_LONG,
        INVALID_CHARS,
        RESERVED
    }

    /**
     * Girdiyi saklanacak biçime indirger: kırpılmış ve küçük harf.
     *
     * <p>
     * {@link Locale#ROOT} şart — Türkçe locale'de {@code "I".toLowerCase()}
     * noktasız {@code ı} üretir ve "ADMIN" yazan yönetici "admın" adlı bir
     * hesap açardı.
     * </p>
     */
    public String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** @return ilk bulunan ihlal, sorun yoksa {@code null}. */
    public Violation validate(String normalized) {
        if (normalized == null || normalized.length() < MIN_LENGTH) {
            return Violation.TOO_SHORT;
        }
        if (normalized.length() > MAX_LENGTH) {
            return Violation.TOO_LONG;
        }
        if (!SHAPE.matcher(normalized).matches()) {
            return Violation.INVALID_CHARS;
        }
        if (RESERVED.contains(normalized)) {
            return Violation.RESERVED;
        }
        return null;
    }
}
