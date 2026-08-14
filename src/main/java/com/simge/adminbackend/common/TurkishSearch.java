package com.simge.adminbackend.common;

import java.util.ArrayList;
import java.util.List;

/**
 * Türkçe ürün adları için LIKE deseni üretir.
 *
 * <p>
 * <b>Neden gerekli:</b> Mikro'daki ürün adları Türkçe karakterlerle yazılı
 * ("AYÇİÇEK YAĞI"), ama kullanıcı klavyeden çoğu zaman ASCII yazıyor ("aycicek
 * yagi"). Düz bir {@code LIKE '%yagi%'} bunu bulamaz — 'g' ile 'ğ' farklı
 * karakterler. Kullanıcının ilk şikâyeti tam buydu: "yag" arandığında adında
 * YAĞI geçen ürünler çıkmıyordu.
 * </p>
 *
 * <p>
 * <b>Çözüm:</b> girilen her harf, MSSQL LIKE'ın karakter kümesi sözdizimiyle
 * ({@code [gğGĞ]}) iki yöne birden açılıyor. Yani "yag" da "yağ" da aynı deseni
 * üretiyor ve ikisi de eşleşiyor. Karakter kümesi desenin İÇİNDE olduğu için
 * sorgu değişmiyor — desen sıradan bir parametre olarak geçiyor, JPQL'e
 * dokunmuyoruz.
 * </p>
 *
 * <p>
 * <b>Bağımlılık:</b> köşeli parantez kümeleri T-SQL LIKE'a özgüdür. Bu proje
 * zaten yalnızca MSSQL'e (Mikro) bağlı, başka bir veritabanı hedefi yok.
 * </p>
 *
 * <p>
 * Büyük/küçük harfin ikisi de kümeye konuyor; böylece veritabanı
 * harf duyarlı (CS) bir collation ile kurulmuş olsa bile arama çalışır.
 * </p>
 */
public final class TurkishSearch {

    /** Tek bir aramada dikkate alınacak en fazla kelime — aşırı uzun girdi taraması engellensin. */
    private static final int MAX_TOKENS = 6;

    /** Tek bir kelimenin en fazla uzunluğu. */
    private static final int MAX_TOKEN_LENGTH = 40;

    private TurkishSearch() {
    }

    /**
     * Arama metnini kelimelere böler. Her kelime ayrı bir LIKE koşulu olur ve
     * koşullar AND'lenir: "komili yag" hem "komili" hem "yag" içeren ürünü bulur,
     * kelimelerin adda hangi sırayla geçtiği önemli değildir.
     *
     * @return kelime listesi; anlamlı girdi yoksa boş liste
     */
    public static List<String> tokenize(String search) {
        List<String> tokens = new ArrayList<>();
        if (search == null || search.isBlank()) {
            return tokens;
        }
        for (String part : search.trim().split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            tokens.add(part.length() > MAX_TOKEN_LENGTH ? part.substring(0, MAX_TOKEN_LENGTH) : part);
            if (tokens.size() == MAX_TOKENS) {
                break;
            }
        }
        return tokens;
    }

    /** {@code %kelime%} — adın herhangi bir yerinde geçsin. */
    public static String containsPattern(String token) {
        return "%" + toPattern(token) + "%";
    }

    /** {@code kelime%} — adın başında geçsin. Sıralamada öne almak için kullanılır. */
    public static String startsWithPattern(String token) {
        return toPattern(token) + "%";
    }

    /**
     * Kelimeyi LIKE desenine çevirir.
     *
     * <p>
     * İki iş yapılıyor, sırası önemli:
     * </p>
     * <ol>
     * <li><b>Kaçış:</b> kullanıcının yazdığı {@code %}, {@code _} ve {@code [}
     * joker karakter olarak yorumlanmamalı. T-SQL'de bunlar köşeli parantez
     * içine alınarak kaçırılabiliyor ({@code [%]}), böylece ayrı bir
     * {@code ESCAPE} yan tümcesine gerek kalmıyor.</li>
     * <li><b>Genişletme:</b> harfler büyük/küçük ve Türkçe/ASCII karşılıklarını
     * kapsayan kümeye açılıyor.</li>
     * </ol>
     * Genişletme yalnızca harflere dokunduğu için birinci adımda üretilen
     * {@code [%]} gibi kaçış dizilerini bozmaz.
     */
    private static String toPattern(String token) {
        StringBuilder out = new StringBuilder(token.length() * 5);
        for (char c : token.toCharArray()) {
            switch (c) {
                // Joker karakterler: köşeli parantezle kaçırılıyor.
                case '%' -> out.append("[%]");
                case '_' -> out.append("[_]");
                case '[' -> out.append("[[]");
                default -> out.append(expand(c));
            }
        }
        return out.toString();
    }

    /**
     * Bir harfi eşdeğerleri kümesine açar. Harf değilse (rakam, tire, boşluk)
     * olduğu gibi bırakılır — ürün kodları "STK-001" gibi olabiliyor.
     */
    private static String expand(char c) {
        return switch (Character.toLowerCase(c)) {
            // Türkçe/ASCII çiftleri: kullanıcı hangisini yazarsa yazsın ikisi de bulunur.
            case 'c', 'ç' -> "[cçCÇ]";
            case 'g', 'ğ' -> "[gğGĞ]";
            case 'o', 'ö' -> "[oöOÖ]";
            case 's', 'ş' -> "[sşSŞ]";
            case 'u', 'ü' -> "[uüUÜ]";
            // Nokta meselesi: Türkçe'de I/ı ve İ/i ayrı harfler, ama kullanıcı
            // ASCII 'i' yazıp "İÇECEK" bulmayı bekliyor. Dördü tek kümede.
            // ('İ' buraya 'i' olarak düşer; Character.toLowerCase onu U+0069'a çevirir.)
            case 'i', 'ı' -> "[iıIİ]";
            default -> expandPlain(c);
        };
    }

    /**
     * Türkçe'ye özgü olmayan harfler için yalnızca büyük/küçük kümesi. Harf
     * duyarsız (CI) collation'da gereksiz ama zararsız; harf duyarlı bir
     * kurulumda aramayı ayakta tutar.
     */
    private static String expandPlain(char c) {
        if (!Character.isLetter(c)) {
            return String.valueOf(c);
        }
        char lower = Character.toLowerCase(c);
        char upper = Character.toUpperCase(c);
        return lower == upper ? String.valueOf(c) : "[" + lower + upper + "]";
    }
}
