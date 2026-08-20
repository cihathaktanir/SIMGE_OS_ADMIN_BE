package com.simge.adminbackend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.simge.adminbackend.settings.WarehouseService;
import com.simge.adminbackend.storefront.StorefrontAdminService;

/**
 * Panelin gördüğü JSON alan adları (ADR D-155).
 *
 * <h2>Bu test neden var</h2>
 * <p>
 * {@code application.properties} içinde
 * {@code spring.jackson.property-naming-strategy=SNAKE_CASE} var. Yani Java
 * tarafındaki {@code fiyatliUrun} kabloya <b>{@code fiyatli_urun}</b> olarak
 * çıkıyor ve {@code baslikTr} istek gövdesinde
 * <b>{@code baslik_tr}</b> olarak <b>bekleniyor</b>.
 * </p>
 *
 * <p>
 * Bu gözden kaçtı ve iki ayrı şekilde patladı:
 * </p>
 * <ul>
 *   <li><b>Okumada</b> — panel {@code d.fiyatliUrun} okuyordu, alan
 *       {@code undefined} geliyordu ve depo ekranı
 *       {@code Cannot read properties of undefined (reading 'toLocaleString')}
 *       ile düşüyordu.</li>
 *   <li><b>Yazmada</b> — daha sinsi. Panel {@code baslikTr} gönderiyordu,
 *       Jackson {@code baslik_tr} bekliyordu, alan {@code null} kalıyordu ve
 *       "null bırakılan alan değişmez" kuralı gereği kayıt <b>sessizce hiçbir
 *       şey yapmıyordu</b>. Hata da vermiyordu.</li>
 * </ul>
 *
 * <p>
 * Uçtan uca doğrulamada yakalanmama sebebi: gönderilen gövdelerin hepsi tek
 * kelimelik alanlardı ({@code aktif}, {@code depo}, {@code ogeler}) ve tek
 * kelimede SNAKE_CASE hiçbir şeyi değiştirmiyor. Bu test çok kelimeli alanları
 * hedefliyor.
 * </p>
 */
class JsonAlanAdlariTest {

    /**
     * Spring Boot'un kurduğu mapper'ın aynısı.
     *
     * <p>
     * İki ayar da önemli: {@code SNAKE_CASE} property dosyasından geliyor,
     * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} ise Spring Boot'un
     * <b>varsayılanı</b>. İkincisi olmadan bu test yalan söyler: çıplak bir
     * {@code ObjectMapper} bilinmeyen alanda istisna fırlatır, oysa uygulama
     * onu <b>sessizce yok sayar</b> — ve sessiz yok sayma bu hatanın ta
     * kendisiydi.
     * </p>
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    // --- Okuma ------------------------------------------------------------

    @Test
    @DisplayName("Depo satırı fiyatli_urun / stoklu_urun olarak çıkar")
    void depoSatiriAlanAdlari() throws Exception {
        var satir = new WarehouseService.DepoSatiri(
                4, "ELMADAG 3", 7338, 5990, true, true, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(mapper.writeValueAsString(satir), Map.class);

        // Panelin okuduğu adlar. camelCase yazmak ekranı düşürüyordu.
        assertEquals(7338, json.get("fiyatli_urun"));
        assertEquals(5990, json.get("stoklu_urun"));
        assertEquals(4, json.get("no"));
        assertEquals("ELMADAG 3", json.get("ad"));
        assertEquals(true, json.get("secili"));
        assertEquals(true, json.get("uygun"));

        assertTrue(json.containsKey("uyari"), "uyari alanı null da olsa gönderilmeli");
    }

    // --- Yazma ------------------------------------------------------------

    @Test
    @DisplayName("Öğe isteği snake_case gövdeden okunur")
    void ogeIstegiSnakeCase() throws Exception {
        String govde = """
                {
                  "ref_id": "5481",
                  "aktif": true,
                  "sira": 2,
                  "baslik_tr": "Başlık",
                  "baslik_en": "Title",
                  "alt_baslik_tr": "Alt başlık",
                  "etiket_tr": "Etiket",
                  "buton_tr": "Buton",
                  "bag_turu": "collection",
                  "bag_degeri": "temel-gida"
                }
                """;

        var istek = mapper.readValue(govde, StorefrontAdminService.OgeIstegi.class);

        // Hepsi tek tek: biri null kalırsa o alan sessizce kaydedilmez.
        assertEquals("5481", istek.refId());
        assertEquals(Boolean.TRUE, istek.aktif());
        assertEquals(2, istek.sira());
        assertEquals("Başlık", istek.baslikTr());
        assertEquals("Title", istek.baslikEn());
        assertEquals("Alt başlık", istek.altBaslikTr());
        assertEquals("Etiket", istek.etiketTr());
        assertEquals("Buton", istek.butonTr());
        assertEquals("collection", istek.bagTuru());
        assertEquals("temel-gida", istek.bagDegeri());
    }

    @Test
    @DisplayName("camelCase gövde alanları DOLDURMAZ — sessiz kayıp buradan geliyordu")
    void camelCaseGovdeCalismaz() throws Exception {
        // Panelin eskiden gönderdiği biçim. Uygulama bilinmeyen alanda İSTİSNA
        // FIRLATMIYOR (Spring Boot varsayılanı); alanı null bırakıyor.
        // "null bırakılan alan değişmez" kuralıyla birleşince kayıt hiçbir şey
        // yapmıyordu. Bu testin amacı davranışı savunmak değil, BELGELEMEK.
        var istek = mapper.readValue("{\"baslikTr\":\"Başlık\"}",
                StorefrontAdminService.OgeIstegi.class);

        assertEquals(null, istek.baslikTr(),
                "camelCase alan okunmuyor; panel snake_case göndermek zorunda");
    }

    @Test
    @DisplayName("Bölüm güncellemesi snake_case gövdeden okunur")
    void bolumGuncelleSnakeCase() throws Exception {
        String govde = """
                {
                  "aktif": false,
                  "sira": 30,
                  "baslik_tr": "Taze Ürünler",
                  "baslik_en": "Fresh Products",
                  "alt_baslik_tr": "Her gün taze",
                  "alt_baslik_en": "Fresh daily"
                }
                """;

        var istek = mapper.readValue(govde, StorefrontAdminService.BolumGuncelle.class);

        assertEquals(Boolean.FALSE, istek.aktif());
        assertEquals(30, istek.sira());
        assertEquals("Taze Ürünler", istek.baslikTr());
        assertEquals("Fresh Products", istek.baslikEn());
        assertEquals("Her gün taze", istek.altBaslikTr());
        assertEquals("Fresh daily", istek.altBaslikEn());
    }

    @Test
    @DisplayName("Tek kelimelik alanlar SNAKE_CASE'den etkilenmez")
    void tekKelimeAlanlar() throws Exception {
        // Uçtan uca doğrulamanın hatayı kaçırma sebebi: gönderilen gövdelerin
        // hepsi böyleydi.
        var istek = mapper.readValue("{\"depo\":17}",
                com.simge.adminbackend.settings.SettingsAdminController.DepoIstegi.class);
        assertNotNull(istek.depo());
        assertEquals(17, istek.depo());
    }
}
