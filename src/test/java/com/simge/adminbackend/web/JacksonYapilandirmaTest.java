package com.simge.adminbackend.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simge.adminbackend.settings.WarehouseService;
import com.simge.adminbackend.storefront.StorefrontAdminService;

/**
 * Uygulamanın <b>gerçek</b> Jackson yapılandırması (ADR D-155).
 *
 * <p>
 * {@link JsonAlanAdlariTest} mapper'ı elle kuruyor; bu test ise Spring Boot'un
 * {@code application.properties}'ten ürettiği mapper'ı enjekte ediyor. İkisi
 * ayrı sorulara cevap veriyor:
 * </p>
 *
 * <ul>
 *   <li>Oradaki: "SNAKE_CASE bu record'larda hangi adları üretir?"</li>
 *   <li>Buradaki: "<b>Bu uygulamada</b> SNAKE_CASE gerçekten açık mı?"</li>
 * </ul>
 *
 * <p>
 * İkincisine ihtiyaç duyuldu çünkü springdoc'un ürettiği OpenAPI şeması
 * {@code baslikTr} diyor — springdoc adlandırma stratejisini uygulamıyor.
 * Yani <b>API dokümantasyonu bu konuda yanlış</b>; kabloya çıkan ad Jackson'ın
 * ürettiği addır. Bir sonraki geliştirici şemaya bakıp camelCase gönderirse
 * alan sessizce yok sayılır.
 * </p>
 */
@JsonTest
class JacksonYapilandirmaTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    @DisplayName("Uygulamanın mapper'ı yanıtı snake_case üretiyor")
    void yanitSnakeCase() throws Exception {
        var satir = new WarehouseService.DepoSatiri(
                4, "ELMADAG 3", 7338, 5990, true, null);

        String json = mapper.writeValueAsString(satir);

        assertTrue(json.contains("\"fiyatli_urun\""), "beklenen alan yok: " + json);
        assertTrue(json.contains("\"stoklu_urun\""), "beklenen alan yok: " + json);
        // Panelin eskiden okuduğu ad. Ekranı düşüren buydu.
        assertTrue(!json.contains("fiyatliUrun"), "camelCase ad hâlâ üretiliyor: " + json);
    }

    @Test
    @DisplayName("Uygulamanın mapper'ı isteği snake_case bekliyor")
    void istekSnakeCase() throws Exception {
        var istek = mapper.readValue("""
                {"ref_id":"5481","baslik_tr":"Başlık","bag_turu":"collection"}
                """, StorefrontAdminService.OgeIstegi.class);

        assertEquals("5481", istek.refId());
        assertEquals("Başlık", istek.baslikTr());
        assertEquals("collection", istek.bagTuru());
    }

    @Test
    @DisplayName("camelCase istek sessizce yok sayılıyor — hata da vermiyor")
    void camelCaseSessizceYokSayiliyor() throws Exception {
        // Sessiz kaybın kanıtı: istisna yok, alan null. Panel bunu gönderdiği
        // sürece "kaydedildi" der ama hiçbir şey değişmezdi.
        var istek = mapper.readValue("{\"baslikTr\":\"Başlık\",\"refId\":\"5481\"}",
                StorefrontAdminService.OgeIstegi.class);

        assertNull(istek.baslikTr());
        assertNull(istek.refId());
    }
}
