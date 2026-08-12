package com.simge.adminbackend.erp;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

/**
 * Mikro ERP'ye yazan <b>tek</b> sınıf (ADR D-127).
 *
 * <p>
 * Bu sınıf {@link ReadOnlyRepository}'yi <b>bozmuyor</b>. O arayüz olduğu gibi
 * duruyor ve ERP tarafındaki her JPA repository'si hâlâ ondan türüyor — yani
 * {@code cariHesaplarRepository.save(...)} bugün de derleme hatası. Yazma yolu
 * bilerek JPA'nın <b>dışında</b>: "hangi kod ERP'ye yazabiliyor" sorusunun
 * cevabı tek bir dosya olsun diye. Buraya yeni bir yazma metodu eklemek, o
 * cevabı değiştirmek demektir; ADR yazmadan yapılmamalı.
 * </p>
 *
 * <p>
 * <b>Yapabildiği yalnızca iki şey var:</b> yeni cari açmak ve var olan bir
 * carinin <b>boş</b> e-posta alanını doldurmak. Silme yok, başka alan
 * güncelleme yok, toplu işlem yok.
 * </p>
 *
 * <h2>Neden 182 sütunun hepsi yazılıyor</h2>
 * <p>
 * Şemada 183 sütunun yalnızca ikisi {@code NOT NULL} ve hiçbirinin varsayılanı
 * yok; teknik olarak beş sütunluk bir INSERT de çalışır. Ama mevcut 2439 cari
 * satırının hiçbirinde tek bir NULL yok — Mikro boş alana {@code ''} ya da
 * {@code 0} yazıyor. Eksik INSERT, arkasında 170'ten fazla NULL sütun bırakıp
 * Mikro istemcisinde ve raporlarda beklenmedik davranan bir cari üretirdi.
 * Sabitler tahmin değil, ölçüm: her sütun için mevcut satırlarda en çok geçen
 * değer (yöntem D-127'de).
 * </p>
 *
 * <h2>Kimlik çifti</h2>
 * <p>
 * {@code cari_RECno} IDENTITY; {@code cari_RECid_RECno} ise mevcut 2440 satırın
 * tamamında ona eşit ve ikisi birlikte benzersiz indeksli. INSERT anında
 * IDENTITY değeri bilinemediği için 0 yazılıp <b>aynı işlemde</b>
 * güncelleniyor. Mevcut hiçbir satırda 0 yok, dolayısıyla eşzamanlı ikinci bir
 * ekleme benzersiz indekste bloklanır ve ilk işlem bitene kadar bekler.
 * </p>
 */
@Component
public class CariWriter {

    private static final Logger log = LoggerFactory.getLogger(CariWriter.class);

    /**
     * UTF-8 byte order mark. Sayıyla yazılıyor: karakterin kendisi kaynakta
     * görünmez ve gözden kaçar, kaçış dizisi de araçlar arasında bozulabiliyor.
     */
    private static final char BOM = (char) 0xFEFF;

    private final NamedParameterJdbcTemplate jdbc;
    private final int mikroKullanici;

    /**
     * Ölçülerek üretilmiş INSERT metinleri. Statik alanda değil burada
     * okunuyorlar: statik başlatıcıda patlasalardı hata
     * {@code ExceptionInInitializerError} olarak, asıl sebebi gizleyerek
     * çıkardı. Kurucuda okununca uygulama açılışta net bir mesajla durur.
     */
    private final String insertCari;
    private final String insertAdres;

    public CariWriter(@Qualifier("mikroDataSource") javax.sql.DataSource mikroDataSource,
            @Value("${simge.erp.mikro-user-id:2}") int mikroKullanici) {
        this.jdbc = new NamedParameterJdbcTemplate(mikroDataSource);
        this.mikroKullanici = mikroKullanici;
        this.insertCari = oku("erp/insert-cari.sql");
        this.insertAdres = oku("erp/insert-cari-adres.sql");
    }

    /**
     * Yeni cari açar ve ana adres satırını yazar.
     *
     * <p>
     * İkisi <b>tek işlemde</b>: adres yazılamazsa cari de geri alınır, yoksa
     * faturada adresi boş bir cari kalırdı ve bunu kimse fark etmezdi.
     * </p>
     *
     * @return oluşan {@code cari_RECno}
     * @throws CariKoduKullanimda aynı {@code cari_kod} zaten varsa
     */
    @Transactional(transactionManager = "mikroTransactionManager")
    public long yeniCari(YeniCari veri) {
        LocalDateTime simdi = LocalDateTime.now();

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cariKod", veri.cariKod())
                .addValue("unvan", veri.unvan())
                .addValue("vergiDairesi", bos(veri.vergiDairesi()))
                .addValue("vergiNo", bos(veri.vergiNo()))
                .addValue("email", bos(veri.email()))
                .addValue("efatura", veri.efaturaMukellefi())
                .addValue("mikroKullanici", mikroKullanici)
                .addValue("simdi", Timestamp.valueOf(simdi));

        KeyHolder anahtar = new GeneratedKeyHolder();
        try {
            jdbc.update(insertCari, p, anahtar, new String[] { "cari_RECno" });
        } catch (DuplicateKeyException e) {
            throw new CariKoduKullanimda(veri.cariKod());
        }

        long recNo = uretilenAnahtar(anahtar, "CARI_HESAPLAR");
        kimlikCiftiniTamamla("CARI_HESAPLAR", "cari_RECno", "cari_RECid_RECno", recNo);

        MapSqlParameterSource a = new MapSqlParameterSource()
                .addValue("cariKod", veri.cariKod())
                .addValue("adres", bos(veri.adres()))
                .addValue("mahalle", bos(veri.mahalle()))
                .addValue("ilce", bos(veri.ilce()))
                .addValue("il", bos(veri.il()))
                .addValue("ulke", bos(veri.ulke()))
                .addValue("postaKodu", bos(veri.postaKodu()))
                .addValue("telefon", bos(veri.telefon()))
                .addValue("mikroKullanici", mikroKullanici)
                .addValue("simdi", Timestamp.valueOf(simdi));

        KeyHolder adresAnahtar = new GeneratedKeyHolder();
        jdbc.update(insertAdres, a, adresAnahtar, new String[] { "adr_RECno" });
        kimlikCiftiniTamamla("CARI_HESAP_ADRESLERI", "adr_RECno", "adr_RECid_RECno",
                uretilenAnahtar(adresAnahtar, "CARI_HESAP_ADRESLERI"));

        log.info("Mikro'ya yeni cari yazıldı: kod={} recno={}", veri.cariKod(), recNo);
        return recNo;
    }

    /**
     * Var olan carinin e-posta alanını doldurur.
     *
     * <p>
     * <b>Yalnızca boşken.</b> {@code WHERE} koşulundaki
     * {@code LTRIM(RTRIM(cari_EMail)) = ''} kasıtlı: Mikro'da elle girilmiş bir
     * adresin üzerine yazmak, ERP'yi doğru kabul eden her akışı (fatura, mutabakat)
     * sessizce bozardı. Doluysa 0 satır etkilenir ve çağıran bunu görür.
     * </p>
     *
     * @return e-posta gerçekten yazıldıysa {@code true}
     */
    @Transactional(transactionManager = "mikroTransactionManager")
    public boolean epostaYaz(String cariKod, String email) {
        Map<String, Object> p = new HashMap<>();
        p.put("cariKod", cariKod);
        p.put("email", email);
        p.put("kullanici", mikroKullanici);
        p.put("simdi", Timestamp.valueOf(LocalDateTime.now()));

        int etkilenen = jdbc.update("""
                UPDATE CARI_HESAPLAR
                   SET cari_EMail = :email,
                       cari_lastup_user = :kullanici,
                       cari_lastup_date = :simdi
                 WHERE cari_kod = :cariKod
                   AND LTRIM(RTRIM(cari_EMail)) = ''
                """, p);

        if (etkilenen == 0) {
            log.warn("Cari e-postası yazılamadı (kod bulunamadı ya da adres zaten dolu): {}", cariKod);
            return false;
        }
        log.info("Mikro'da cari e-postası dolduruldu: {}", cariKod);
        return true;
    }

    /**
     * IDENTITY değerini alır.
     *
     * <p>
     * Sürücü anahtarı döndürmezse burada durmak şart: dönmediğini fark etmeden
     * devam etseydik {@code RECid_RECno} 0 kalmış, yani Mikro'nun kimlik
     * değişmezini bozmuş bir satır bırakırdık. İşlem geri alınsın diye
     * fırlatıyoruz.
     * </p>
     */
    private long uretilenAnahtar(KeyHolder anahtar, String tablo) {
        Number key = anahtar.getKey();
        if (key == null) {
            throw new IllegalStateException(
                    "INSERT sonrası IDENTITY değeri alınamadı: " + tablo);
        }
        return key.longValue();
    }

    /**
     * {@code RECid_RECno = RECno}. Mikro'nun tüm satırlarda tuttuğu değişmez.
     */
    private void kimlikCiftiniTamamla(String tablo, String recNoSutun, String recIdSutun, long recNo) {
        jdbc.update("UPDATE " + tablo + " SET " + recIdSutun + " = :recNo"
                + " WHERE " + recNoSutun + " = :recNo", Map.of("recNo", recNo));
    }

    /** Mikro NULL kullanmıyor; boş alan {@code ''} olarak yazılır. */
    private String bos(String deger) {
        return deger == null ? "" : deger.trim();
    }

    /**
     * Betiği okur ve <b>BOM'u atar</b>.
     *
     * <p>
     * Bu dosyalar ölçümden üretiliyor ve üreten araç (PowerShell
     * {@code Set-Content -Encoding utf8}) başa BOM koyuyor. SQL Server o
     * görünmez karakteri sözdizimi hatası sayıyor:
     * {@code Incorrect syntax near '?'}. Hata mesajı sebebi hiç ele vermiyor —
     * dosya editörde tamamen normal görünüyor. Kaynağı düzeltmek yetmez,
     * çünkü dosyalar yeniden üretilebilir; kalıcı çözüm burada temizlemek.
     * </p>
     */
    private static String oku(String yol) {
        try {
            String metin = new String(FileCopyUtils.copyToByteArray(
                    new ClassPathResource(yol).getInputStream()), StandardCharsets.UTF_8);
            return !metin.isEmpty() && metin.charAt(0) == BOM ? metin.substring(1) : metin;
        } catch (Exception e) {
            throw new IllegalStateException("ERP yazma betiği okunamadı: " + yol, e);
        }
    }

    /**
     * Yeni cari için gereken alanlar.
     *
     * @param efaturaMukellefi VKN'ye bağlı bir iş kuralı; tahmin edilmiyor,
     *        panelde açıktan seçiliyor. Mevcut satırlarda dağılım %59/%41 —
     *        varsayılanı yanlış koymak fatura kesilemez hâle getirirdi.
     */
    public record YeniCari(
            String cariKod,
            String unvan,
            String vergiDairesi,
            String vergiNo,
            String email,
            boolean efaturaMukellefi,
            String adres,
            String mahalle,
            String ilce,
            String il,
            String ulke,
            String postaKodu,
            String telefon) {
    }

    /** {@code cari_kod} benzersiz indeksli; aynı kodla ikinci cari açılamaz. */
    public static class CariKoduKullanimda extends RuntimeException {
        private final String cariKod;

        public CariKoduKullanimda(String cariKod) {
            super("Bu cari kodu zaten kullanımda: " + cariKod);
            this.cariKod = cariKod;
        }

        public String getCariKod() {
            return cariKod;
        }
    }
}
