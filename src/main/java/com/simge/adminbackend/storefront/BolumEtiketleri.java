package com.simge.adminbackend.storefront;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bölüm anahtarlarının insanca karşılıkları (ADR D-154).
 *
 * <p>
 * Veritabanındaki {@code section_key} değerleri şablonun iç adları:
 * {@code offer_banner_1}, {@code products_list_3}. Panelde bunları çıplak
 * göstermek, operatörden vitrin şablonunun kaynak kodunu bilmesini istemek
 * olurdu — özellikle {@code offer_banner_1} / {@code offer_banner_2} ikilisinde,
 * çünkü ikisi de "banner" ve sayfanın çok farklı yerlerinde duruyorlar.
 * </p>
 *
 * <p>
 * Etiketler ayrıca <b>nerede göründüğünü</b> söylüyor. Operatörün panelde
 * yaptığı değişikliği vitrinde bulabilmesi buna bağlı: "Ürün tanıtım
 * banner'ları" yazan kutu, ana sayfada kategori şeridinin hemen altındaki
 * üçlü şerit.
 * </p>
 *
 * <p>
 * Listede olmayan bir anahtar hata değil: bölümler veriden geliyor ve yeni
 * bir yuva eklenebilir. O durumda anahtarın kendisi gösteriliyor.
 * </p>
 */
final class BolumEtiketleri {

    private record Etiket(String ad, String nerede) {
    }

    private static final Map<String, Etiket> ETIKETLER = new LinkedHashMap<>();

    static {
        ETIKETLER.put("home_banner", new Etiket("Üst hero banner",
                "Ana sayfanın en üstü — tek büyük görsel, üzerinde başlık ve buton."));
        ETIKETLER.put("categories", new Etiket("Kategori şeridi",
                "Hero banner'ın altındaki yuvarlak kategori ikonları."));
        ETIKETLER.put("offer_banner_1", new Etiket("Ürün tanıtım banner'ları",
                "Kategori şeridinin altındaki banner şeridi (banner-section)."));
        ETIKETLER.put("offer_banner_2", new Etiket("Alt tanıtım banner'ları",
                "İkinci ürün listesinin altındaki kutulu banner şeridi (gift-card-section)."));
        ETIKETLER.put("products_list_1", new Etiket("Ürün listesi 1",
                "Banner şeridinin altındaki ilk ürün karuseli."));
        ETIKETLER.put("products_list_2", new Etiket("Ürün listesi 2",
                "İkinci ürün karuseli."));
        ETIKETLER.put("products_list_3", new Etiket("Ürün listesi 3",
                "Alt banner şeridinin altındaki ürün karuseli."));
        ETIKETLER.put("products_list_4", new Etiket("Ürün listesi 4",
                "Sayfanın altındaki son ürün karuseli."));
        ETIKETLER.put("services", new Etiket("Hizmet kutuları",
                "İkon + başlık + açıklama şeridi (ücretsiz teslimat, güvenli ödeme, ...)."));
        ETIKETLER.put("featured_blogs", new Etiket("Blog şeridi",
                "Şu an kapalı — blog içeriği yok."));
        ETIKETLER.put("brand", new Etiket("Marka şeridi",
                "Şu an kapalı — marka logoları yüklenmedi."));
    }

    static String ad(String sectionKey) {
        Etiket e = ETIKETLER.get(sectionKey);
        return e != null ? e.ad() : sectionKey;
    }

    static String nerede(String sectionKey) {
        Etiket e = ETIKETLER.get(sectionKey);
        return e != null ? e.nerede() : null;
    }

    private BolumEtiketleri() {
    }
}
