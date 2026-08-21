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
 * <b>Yapabildiği şeyler (D-173 ile genişledi):</b>
 * </p>
 * <ol>
 *   <li>yeni cari açmak (ana adres satırıyla birlikte),</li>
 *   <li>var olan bir carinin <b>boş</b> e-posta alanını doldurmak,</li>
 *   <li>var olan bir cariye <b>ek adres</b> yazmak ({@link #yeniAdres}),</li>
 *   <li>carinin unvan / telefon / e-posta alanlarını ve fatura adresi
 *       işaretçisini güncellemek ({@link #cariGuncelle}).</li>
 * </ol>
 *
 * <p>
 * <b>Silme yok, toplu işlem yok.</b> Ve önemli bir sınır: var olan bir
 * <b>adres satırının içeriği değiştirilmiyor</b>. Sebep geçmiş —
 * {@code SIPARISLER.sip_adresno} ve {@code STOK_HAREKETLERI.sth_adres_no} o
 * satırı numarayla gösteriyor; metnini değiştirmek üç yıl önceki irsaliyenin
 * de adresini değiştirirdi. Taşınan bir firma için doğru işlem <b>yeni adres
 * ekleyip fatura işaretçisini ona çevirmek</b>. Böylece
 * {@code CARI_HESAP_ADRESLERI} yalnızca INSERT alıyor.
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

    /**
     * Var olan cariye EK adres yazan betik (D-173).
     *
     * <p>
     * {@link #insertAdres}'ten farkı: orada {@code adr_adres_no} sabit 1,
     * burada parametre. Ayrıca telefon üç alana bölünmüş geliyor.
     * </p>
     */
    private final String insertAdresEk;

    public CariWriter(@Qualifier("mikroDataSource") javax.sql.DataSource mikroDataSource,
            @Value("${simge.erp.mikro-user-id:2}") int mikroKullanici) {
        this.jdbc = new NamedParameterJdbcTemplate(mikroDataSource);
        this.mikroKullanici = mikroKullanici;
        this.insertCari = oku("erp/insert-cari.sql");
        this.insertAdres = oku("erp/insert-cari-adres.sql");
        this.insertAdresEk = oku("erp/insert-cari-adres-ek.sql");
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

        TelefonAyirici.Telefon telefon = TelefonAyirici.ayir(veri.telefon());

        MapSqlParameterSource a = new MapSqlParameterSource()
                .addValue("cariKod", veri.cariKod())
                .addValue("adres", bos(veri.adres()))
                .addValue("mahalle", bos(veri.mahalle()))
                .addValue("ilce", bos(veri.ilce()))
                .addValue("il", bos(veri.il()))
                .addValue("ulke", bos(veri.ulke()))
                .addValue("postaKodu", bos(veri.postaKodu()))
                // Telefon ÜÇ ALANA bölünüyor (D-173). Öncesinde numaranın
                // tamamı adr_tel_no1'e yazılıyordu ve o sütun 10 karakter:
                // normal bir cep numarası (11 hane) INSERT'i truncation
                // hatasıyla düşürürdü. Ek adres yolu da aynı yardımcıyı
                // kullanıyor — kural tek yerde.
                .addValue("telUlke", telefon.ulkeKodu())
                .addValue("telBolge", telefon.bolgeKodu())
                .addValue("telNo", telefon.numara())
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

    // ------------------------------------------------------------------
    // D-173 — cari güncelleme kuyruğunun ERP tarafı
    // ------------------------------------------------------------------

    /**
     * Var olan bir cariye <b>ek adres</b> yazar.
     *
     * <p>
     * Adres numarası burada hesaplanıyor: {@code MAX(adr_adres_no) + 1}.
     * Sabit bir başlangıç varsayılmıyor — ölçüldü, 1.982 aktif satırın
     * 205'inde numara <b>0</b>, yani "yeni adres = 2" varsayımı yanlış olurdu.
     * </p>
     *
     * <p>
     * <b>Yarış durumu veritabanında kesiliyor.</b> {@code (cari_kod,
     * adres_no)} çifti Mikro'da UNIQUE indeksli
     * ({@code NDX_CARI_HESAP_ADRESLERI_02}). İki operatör aynı anda aktarırsa
     * ikincisi sessizce bozmaz, {@link AdresNumarasiCakisti} fırlatır ve
     * panel "tekrar deneyin" der. Bilerek işlem içinde yeniden denemiyoruz:
     * başarısız bir ifadeden sonra aynı işlemde devam etmek SQL Server'da
     * güvenilir değil.
     * </p>
     *
     * @return Mikro'da oluşan {@code adr_adres_no}
     */
    @Transactional(transactionManager = "mikroTransactionManager")
    public int yeniAdres(YeniAdres veri) {
        if (!cariVar(veri.cariKod())) {
            throw new CariBulunamadi(veri.cariKod());
        }

        int adresNo = sonrakiAdresNo(veri.cariKod());
        LocalDateTime simdi = LocalDateTime.now();

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("cariKod", veri.cariKod())
                .addValue("adresNo", adresNo)
                .addValue("adresKodu", bos(veri.adresKodu()))
                .addValue("cadde", bos(veri.cadde()))
                .addValue("mahalle", bos(veri.mahalle()))
                .addValue("sokak", bos(veri.sokak()))
                .addValue("semt", bos(veri.semt()))
                .addValue("aptNo", bos(veri.aptNo()))
                .addValue("daireNo", bos(veri.daireNo()))
                .addValue("ilce", bos(veri.ilce()))
                .addValue("il", bos(veri.il()))
                .addValue("ulke", bos(veri.ulke()))
                .addValue("postaKodu", bos(veri.postaKodu()))
                .addValue("telUlke", bos(veri.telUlkeKodu()))
                .addValue("telBolge", bos(veri.telBolgeKodu()))
                .addValue("telNo", bos(veri.telNumara()))
                .addValue("mikroKullanici", mikroKullanici)
                .addValue("simdi", Timestamp.valueOf(simdi));

        KeyHolder anahtar = new GeneratedKeyHolder();
        try {
            jdbc.update(insertAdresEk, p, anahtar, new String[] { "adr_RECno" });
        } catch (DuplicateKeyException e) {
            throw new AdresNumarasiCakisti(veri.cariKod(), adresNo);
        }

        kimlikCiftiniTamamla("CARI_HESAP_ADRESLERI", "adr_RECno", "adr_RECid_RECno",
                uretilenAnahtar(anahtar, "CARI_HESAP_ADRESLERI"));

        log.info("Mikro'ya ek adres yazıldı: cari={} adresNo={}", veri.cariKod(), adresNo);
        return adresNo;
    }

    /**
     * Carinin bilgi alanlarını ve/veya fatura adresi işaretçisini günceller.
     *
     * <p>
     * <b>Null bırakılan alan değişmez.</b> Hiçbir alan verilmezse hiç sorgu
     * çalışmaz.
     * </p>
     *
     * <p>
     * {@code faturaAdresNo} verilirse önce o numaranın <b>gerçekten var
     * olduğu</b> doğrulanıyor. Mikro'da adres tablosuna hiç foreign key yok
     * (0 kısıt): olmayan bir numara yazmak hata vermez, sipariş kaydedilir
     * ama irsaliyede adres boş çıkar. Sessiz bozulmayı burada kesiyoruz.
     * </p>
     *
     * @return gerçekten güncellenen satır sayısı (0 = cari bulunamadı)
     */
    @Transactional(transactionManager = "mikroTransactionManager")
    public int cariGuncelle(CariGuncelle veri) {
        StringBuilder set = new StringBuilder();
        Map<String, Object> p = new HashMap<>();
        p.put("cariKod", veri.cariKod());

        if (veri.unvan() != null) {
            set.append("cari_unvan1 = :unvan, ");
            p.put("unvan", bos(veri.unvan()));
        }
        if (veri.telefon() != null) {
            set.append("cari_CepTel = :telefon, ");
            p.put("telefon", bos(veri.telefon()));
        }
        if (veri.email() != null) {
            set.append("cari_EMail = :email, ");
            p.put("email", bos(veri.email()));
        }
        if (veri.faturaAdresNo() != null) {
            if (!adresVar(veri.cariKod(), veri.faturaAdresNo())) {
                throw new AdresBulunamadi(veri.cariKod(), veri.faturaAdresNo());
            }
            set.append("cari_fatura_adres_no = :faturaAdresNo, ");
            p.put("faturaAdresNo", veri.faturaAdresNo());
        }

        if (set.isEmpty()) {
            return 0;
        }

        p.put("kullanici", mikroKullanici);
        p.put("simdi", Timestamp.valueOf(LocalDateTime.now()));

        int etkilenen = jdbc.update("UPDATE CARI_HESAPLAR SET " + set
                + " cari_lastup_user = :kullanici, cari_lastup_date = :simdi"
                + " WHERE cari_kod = :cariKod", p);

        if (etkilenen == 0) {
            log.warn("Cari güncellenemedi, kod bulunamadı: {}", veri.cariKod());
        } else {
            log.info("Mikro'da cari güncellendi: {} (alanlar: {})",
                    veri.cariKod(), p.keySet());
        }
        return etkilenen;
    }

    /**
     * Bir carinin sonraki adres numarası.
     *
     * <p>
     * Hiç adresi yoksa 1'den başlıyor: yeni cari açılışında yazılan ana adres
     * de 1 numaralı ({@code insert-cari-adres.sql}).
     * </p>
     */
    private int sonrakiAdresNo(String cariKod) {
        Integer enYuksek = jdbc.queryForObject(
                "SELECT MAX(adr_adres_no) FROM CARI_HESAP_ADRESLERI WHERE adr_cari_kod = :cariKod",
                Map.of("cariKod", cariKod), Integer.class);
        return enYuksek == null ? 1 : enYuksek + 1;
    }

    private boolean cariVar(String cariKod) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM CARI_HESAPLAR WHERE cari_kod = :cariKod",
                Map.of("cariKod", cariKod), Integer.class);
        return n != null && n > 0;
    }

    private boolean adresVar(String cariKod, int adresNo) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM CARI_HESAP_ADRESLERI
                 WHERE adr_cari_kod = :cariKod AND adr_adres_no = :adresNo
                   AND (adr_iptal IS NULL OR adr_iptal = 0)
                """, Map.of("cariKod", cariKod, "adresNo", adresNo), Integer.class);
        return n != null && n > 0;
    }

    /**
     * Ek adres için gereken alanlar.
     *
     * <p>
     * Telefon <b>bölünmüş</b> geliyor ({@link TelefonAyirici}): Mikro üç ayrı
     * sütunda tutuyor ve {@code adr_tel_no1} yalnızca 10 karakter.
     * </p>
     */
    public record YeniAdres(
            String cariKod,
            String adresKodu,
            String cadde,
            String mahalle,
            String sokak,
            String semt,
            String aptNo,
            String daireNo,
            String ilce,
            String il,
            String ulke,
            String postaKodu,
            String telUlkeKodu,
            String telBolgeKodu,
            String telNumara) {
    }

    /** Cari güncellemesi; {@code null} alan değişmez. */
    public record CariGuncelle(
            String cariKod,
            String unvan,
            String telefon,
            String email,
            Integer faturaAdresNo) {
    }

    /** Aktarılmak istenen cari Mikro'da yok. */
    public static class CariBulunamadi extends RuntimeException {
        public CariBulunamadi(String cariKod) {
            super("Mikro'da böyle bir cari yok: " + cariKod);
        }
    }

    /** Fatura adresi olarak işaretlenmek istenen numara o caride yok. */
    public static class AdresBulunamadi extends RuntimeException {
        public AdresBulunamadi(String cariKod, int adresNo) {
            super("Bu carinin " + adresNo + " numaralı adresi yok: " + cariKod);
        }
    }

    /** Aynı anda başka bir aktarma yapıldı; numara kapılmış. */
    public static class AdresNumarasiCakisti extends RuntimeException {
        public AdresNumarasiCakisti(String cariKod, int adresNo) {
            super("Adres numarası " + adresNo + " bu sırada başkası tarafından kullanıldı ("
                    + cariKod + "). Tekrar deneyin.");
        }
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
