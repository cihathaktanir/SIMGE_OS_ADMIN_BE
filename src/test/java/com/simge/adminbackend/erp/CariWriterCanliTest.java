package com.simge.adminbackend.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CariWriter}'ı <b>gerçek Mikro şemasında</b> çalıştırır ve geri alır.
 *
 * <p>
 * Neden gerekiyor: birim testleri {@code CariWriter}'ı mock'luyor, yani 182
 * sütunluk INSERT'in şemayla uyumlu olduğunu <i>hiç</i> denemiyorlar. Sütun adı
 * hatası, tip uyuşmazlığı ya da IDENTITY'nin geri dönmemesi ancak burada
 * görülür — ve ERP'ye yanlış satır yazmak, yazmamaktan çok daha pahalı.
 * </p>
 *
 * <p>
 * <b>Kalıcı hiçbir şey yazmaz.</b> Spring'in test {@code @Transactional}'ı
 * varsayılan olarak geri alır; {@link Rollback} bunu ayrıca açıkça söylüyor ki
 * biri {@code @Commit} eklemeye kalkarsa niyet ortada olsun.
 * </p>
 *
 * <p>
 * <b>Varsayılan olarak kapalı</b> — canlı veritabanı ve parola ister:
 * </p>
 *
 * <pre>
 * $env:SIMGE_MIKRO_PASSWORD  = '...'
 * $env:SIMGE_APP_DB_PASSWORD = '...'
 * .\mvnw.cmd test -Dsimge.erp.canli-test=true -Dtest=CariWriterCanliTest
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "simge.erp.canli-test", matches = "true")
class CariWriterCanliTest {

    private static final String DENEME_KOD = "ZZ-TEST-ROLLBACK";

    @Autowired
    private CariWriter writer;

    @Autowired
    @Qualifier("mikroDataSource")
    private javax.sql.DataSource mikroDataSource;

    private CariWriter.YeniCari veri() {
        return new CariWriter.YeniCari(DENEME_KOD, "ROLLBACK DENEME LTD", "ÇANKAYA",
                "9999999999", "deneme@ornek.test", false, "1. Cad. No:1", "Merkez Mah.",
                "Çankaya", "Ankara", "TÜRKİYE", "06100", "5551112233");
    }

    @Test
    @DisplayName("Yeni cari yazılır ve TEK BİR sütun bile NULL kalmaz")
    @Transactional(transactionManager = "mikroTransactionManager")
    @Rollback
    void hicNullKalmaz() {
        long recNo = writer.yeniCari(veri());
        assertTrue(recNo > 0, "IDENTITY dönmedi");

        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(mikroDataSource);

        // Mikro'nun 2439 satırının hiçbirinde NULL yok; bizimkinde de olmamalı.
        List<String> nullSutunlar = nullKalanlar(jdbc, "CARI_HESAPLAR", "cari_RECno", recNo);
        assertEquals(List.of(), nullSutunlar, "NULL kalan sütunlar var");

        // Kimlik çifti: RECid_RECno = RECno, tüm tabloda geçerli değişmez.
        Integer recIdRecNo = jdbc.queryForObject(
                "SELECT cari_RECid_RECno FROM CARI_HESAPLAR WHERE cari_RECno = :r",
                Map.of("r", recNo), Integer.class);
        assertEquals((int) recNo, recIdRecNo,
                "cari_RECid_RECno, cari_RECno'ya eşitlenmemiş");

        // Ana adres satırı da yazılmış olmalı: yazılmazsa cari açılır ama
        // faturada adres boş kalır ve bunu kimse fark etmez.
        Integer adresSayisi = jdbc.queryForObject(
                "SELECT COUNT(*) FROM CARI_HESAP_ADRESLERI"
                        + " WHERE adr_cari_kod = :k AND adr_adres_no = 1",
                Map.of("k", DENEME_KOD), Integer.class);
        assertEquals(1, adresSayisi, "ana adres satırı yazılmamış");
    }

    @Test
    @DisplayName("E-posta yalnızca BOŞKEN yazılır; dolu adresin üzerine yazılmaz")
    @Transactional(transactionManager = "mikroTransactionManager")
    @Rollback
    void doluEpostaninUzerineYazilmaz() {
        // Yeni cari e-postasız açılıyor: ilk yazma başarılı olmalı.
        writer.yeniCari(new CariWriter.YeniCari(DENEME_KOD, "ROLLBACK DENEME LTD", "ÇANKAYA",
                "9999999999", "", false, "1. Cad.", "Merkez", "Çankaya", "Ankara",
                "TÜRKİYE", "06100", "5551112233"));

        assertTrue(writer.epostaYaz(DENEME_KOD, "ilk@ornek.test"),
                "boş e-posta alanına yazılamadı");

        // İkinci deneme, adres artık dolu olduğu için reddedilmeli. Bu kural
        // Mikro'yu doğru kabul eden fatura/mutabakat akışlarını koruyor.
        assertTrue(!writer.epostaYaz(DENEME_KOD, "ikinci@ornek.test"),
                "dolu e-posta adresinin üzerine yazıldı");
    }

    private List<String> nullKalanlar(NamedParameterJdbcTemplate jdbc, String tablo,
            String anahtarSutun, long anahtar) {
        List<String> sutunlar = jdbc.queryForList(
                "SELECT c.name FROM sys.columns c"
                        + " WHERE c.object_id = OBJECT_ID(:t) AND c.is_computed = 0",
                Map.of("t", tablo), String.class);

        return sutunlar.stream().filter(s -> {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + tablo + " WHERE " + anahtarSutun + " = :a"
                            + " AND [" + s + "] IS NULL",
                    Map.of("a", anahtar), Integer.class);
            return n != null && n > 0;
        }).toList();
    }
}
