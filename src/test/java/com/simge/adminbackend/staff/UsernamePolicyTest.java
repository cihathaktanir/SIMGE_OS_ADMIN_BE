package com.simge.adminbackend.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Kullanıcı adı kuralları (ADR D-123). */
class UsernamePolicyTest {

    private final UsernamePolicy policy = new UsernamePolicy();

    @Test
    @DisplayName("Büyük harf küçüğe iner — 'Depo1' ile 'depo1' aynı hesap")
    void buyukHarfKucugeIner() {
        assertEquals("depo1", policy.normalize("Depo1"));
        assertEquals("depo1", policy.normalize("  DEPO1  "));
    }

    @Test
    @DisplayName("Büyük I noktasız ı'ya dönüşmez — Türkçe locale tuzağı")
    void buyukIHarfiNoktaliKalir() {
        // Türkçe locale'de "ADMIN".toLowerCase() -> "admın" olurdu ve
        // yönetici, girmeye çalıştığı hesabı bir daha bulamazdı.
        assertEquals("admin", policy.normalize("ADMIN"));
    }

    @Test
    @DisplayName("Geçerli adlar kabul edilir")
    void gecerliAdlar() {
        assertNull(policy.validate("depo1"));
        assertNull(policy.validate("ayse"));
        assertNull(policy.validate("muhasebe.merve"));
        assertNull(policy.validate("depo-2"));
        assertNull(policy.validate("satis_ankara"));
    }

    @Test
    @DisplayName("Türkçe karakter reddedilir — giriş ekranında ı/i ayrımı destek yükü")
    void turkceKarakterReddedilir() {
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("şef"));
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("depoşef"));
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("çağrı"));
    }

    @Test
    @DisplayName("Boşluk ve @ reddedilir — kullanıcı adı e-posta değil")
    void bosluklarVeEpostaReddedilir() {
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("ali veli"));
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("ali@simge.com"));
    }

    @Test
    @DisplayName("Noktalama ile başlayan/biten ad reddedilir")
    void kenarlardaNoktalamaReddedilir() {
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate(".depo"));
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("depo-"));
        assertEquals(UsernamePolicy.Violation.INVALID_CHARS, policy.validate("_depo"));
    }

    @Test
    @DisplayName("Uzunluk sınırları")
    void uzunlukSinirlari() {
        assertEquals(UsernamePolicy.Violation.TOO_SHORT, policy.validate("ab"));
        assertEquals(UsernamePolicy.Violation.TOO_SHORT, policy.validate(""));
        assertEquals(UsernamePolicy.Violation.TOO_SHORT, policy.validate(null));
        assertEquals(UsernamePolicy.Violation.TOO_LONG, policy.validate("a".repeat(31)));
        assertNull(policy.validate("a".repeat(30)));
    }

    @Test
    @DisplayName("Sistem adları kişiye verilemez")
    void ayrilmisAdlar() {
        assertEquals(UsernamePolicy.Violation.RESERVED, policy.validate("root"));
        assertEquals(UsernamePolicy.Violation.RESERVED, policy.validate("system"));
        assertEquals(UsernamePolicy.Violation.RESERVED, policy.validate("guest"));
        // 'admin' ayrılmış DEĞİL: ilk yönetici hesabı bu adla açılıyor.
        assertNull(policy.validate("admin"));
    }
}
