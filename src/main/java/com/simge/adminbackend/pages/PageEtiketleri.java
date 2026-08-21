package com.simge.adminbackend.pages;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blok anahtarlarının insanca karşılıkları (ADR D-172).
 *
 * <p>
 * Veritabanındaki {@code block_key} değerleri şablonun iç adları:
 * {@code intro}, {@code feature_1}. Panelde bunları çıplak göstermek,
 * operatörden vitrin şablonunun kaynak kodunu bilmesini istemek olurdu.
 * </p>
 *
 * <p>
 * Etiketler ayrıca <b>nerede göründüğünü</b> söylüyor: operatörün panelde
 * yaptığı değişikliği vitrinde bulabilmesi buna bağlı.
 * </p>
 *
 * <p>
 * Listede olmayan bir anahtar hata değil — o durumda anahtarın kendisi
 * gösteriliyor.
 * </p>
 */
final class PageEtiketleri {

    private record Etiket(String ad, String nerede) {
    }

    private static final Map<String, Etiket> ETIKETLER = new LinkedHashMap<>();

    static {
        ETIKETLER.put("intro", new Etiket("Tanıtım metni",
                "Hakkımızda sayfasının üstü — geniş görsel, altında başlık ve "
                        + "paragraflar. Paragraflar boş satırla ayrılır."));
        ETIKETLER.put("feature_1", new Etiket("Hizmet kutusu 1",
                "Tanıtım metninin altındaki dörtlü şeridin ilk kutusu."));
        ETIKETLER.put("feature_2", new Etiket("Hizmet kutusu 2",
                "Dörtlü şeridin ikinci kutusu."));
        ETIKETLER.put("feature_3", new Etiket("Hizmet kutusu 3",
                "Dörtlü şeridin üçüncü kutusu."));
        ETIKETLER.put("feature_4", new Etiket("Hizmet kutusu 4",
                "Dörtlü şeridin son kutusu."));
    }

    static String ad(String blockKey) {
        Etiket e = ETIKETLER.get(blockKey);
        return e != null ? e.ad() : blockKey;
    }

    static String nerede(String blockKey) {
        Etiket e = ETIKETLER.get(blockKey);
        return e != null ? e.nerede() : null;
    }

    private PageEtiketleri() {
    }
}
