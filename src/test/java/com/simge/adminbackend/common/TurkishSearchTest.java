package com.simge.adminbackend.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Panel ürün aramasının kelime ve desen üretimi (ADR D-151).
 *
 * <p>
 * Kullanıcı ürünün tam adını vitrinden kopyalayıp yapıştırıyor — akış bu.
 * Testler o gerçek girdiyle yazıldı: {@code "TOZ SEKER 50KG"} ve Türkçe
 * yazımı {@code "TOZ ŞEKER 50KG"}.
 * </p>
 */
class TurkishSearchTest {

    @Test
    @DisplayName("Tam ad yapıştırılınca ÜÇ kelimeye bölünür")
    void tamAdUcKelimeyeBolunur() {
        // Eskiden yalnızca ilk kelime kullanılıyordu; ürün 107 'TOZ' kaydının
        // arasında kayboluyordu.
        assertEquals(List.of("TOZ", "SEKER", "50KG"),
                TurkishSearch.tokenize("TOZ SEKER 50KG"));
    }

    @Test
    @DisplayName("Fazla boşluk ve baş/son boşluğu kelimeyi bozmaz")
    void fazlaBoslukBozmaz() {
        assertEquals(List.of("TOZ", "SEKER", "50KG"),
                TurkishSearch.tokenize("  TOZ   SEKER  50KG "));
    }

    @Test
    @DisplayName("ASCII yazım Türkçe kaydı bulur: 'SEKER' deseni 'ŞEKER'i kapsar")
    void asciiYazimTurkceyiBulur() {
        Pattern p = likeToRegex(TurkishSearch.containsPattern("SEKER"));
        assertTrue(p.matcher("TOZ ŞEKER 50KG").matches(), "Türkçe kayıt bulunmalı");
        assertTrue(p.matcher("TOZ SEKER 50KG").matches(), "ASCII kayıt bulunmalı");
    }

    @Test
    @DisplayName("Türkçe yazım ASCII kaydı bulur: 'ŞEKER' deseni 'SEKER'i kapsar")
    void turkceYazimAsciiyiBulur() {
        // Mikro'daki kayıt ASCII ('TOZ SEKER 50KG'), kullanıcı Türkçe yazabilir.
        // Bu yön kontrol edilmezse Türkçe klavyeyle arayan hiçbir şey bulamaz.
        Pattern p = likeToRegex(TurkishSearch.containsPattern("ŞEKER"));
        assertTrue(p.matcher("TOZ SEKER 50KG").matches(), "ASCII kayıt bulunmalı");
        assertTrue(p.matcher("TOZ ŞEKER 50KG").matches(), "Türkçe kayıt bulunmalı");
    }

    @Test
    @DisplayName("Noktalı/noktasız i her iki yönde eşleşir")
    void iHarfiIkiYonde() {
        assertTrue(likeToRegex(TurkishSearch.containsPattern("BIBER"))
                .matcher("TATLI BİBER").matches());
        assertTrue(likeToRegex(TurkishSearch.containsPattern("BİBER"))
                .matcher("TATLI BIBER").matches());
    }

    @Test
    @DisplayName("Joker karakterler kaçırılır — '%' arama sonucunu patlatmaz")
    void jokerKacirilir() {
        String desen = TurkishSearch.containsPattern("%50");
        assertTrue(desen.contains("[%]"), "yüzde işareti kaçırılmalı: " + desen);
    }

    /**
     * T-SQL LIKE desenini kabaca regex'e çevirir — testin DB'ye ihtiyaç
     * duymaması için. Yalnızca burada kullanılan iki yapıyı biliyor:
     * {@code %} (herhangi bir şey) ve {@code [...]} (karakter kümesi).
     */
    private static Pattern likeToRegex(String like) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < like.length(); i++) {
            char c = like.charAt(i);
            if (c == '%') {
                re.append(".*");
            } else if (c == '[') {
                int son = like.indexOf(']', i);
                re.append(like, i, son + 1);
                i = son;
            } else {
                re.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(re.toString(), Pattern.DOTALL);
    }
}
