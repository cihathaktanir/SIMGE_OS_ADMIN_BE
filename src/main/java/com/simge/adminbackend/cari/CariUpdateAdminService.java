package com.simge.adminbackend.cari;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.CariUpdateRequest;
import com.simge.adminbackend.appdb.repository.CariUpdateRequestRepository;
import com.simge.adminbackend.erp.CariWriter;
import com.simge.adminbackend.erp.TelefonAyirici;
import com.simge.adminbackend.erp.model.CariAdres;
import com.simge.adminbackend.erp.model.CariHesap;
import com.simge.adminbackend.erp.repository.CariAdresRepository;
import com.simge.adminbackend.erp.repository.CariHesaplarRepository;

/**
 * Cari güncelleme kuyruğu — panel tarafı (ADR D-173).
 *
 * <p>
 * Vitrin talebi kendi veritabanımıza yazıyor; ERP'ye aktarma kararı burada
 * veriliyor ve yazma {@link CariWriter} üzerinden yapılıyor.
 * </p>
 *
 * <h2>Aktarmadan önce düzenlenebilir</h2>
 * <p>
 * Operatör "onayla" düğmesine körlemesine basmıyor: alanlar düzenlenebilir ve
 * yazım hatası <b>ERP'ye gitmeden önce</b> düzeltiliyor. Kuyruğun asıl değeri
 * burada — insan onayı tek başına çöp veriyi engellemez, düzeltme imkânı
 * engeller.
 * </p>
 *
 * <h2>Benzerlik uyarısı</h2>
 * <p>
 * Bekleyen adres, carinin mevcut adresleriyle karşılaştırılıyor. Aynı adresin
 * ikinci kez girilmesi bu ekranın en olası hatası; operatöre göstermek,
 * onaylatmaktan daha etkili.
 * </p>
 */
@Service
public class CariUpdateAdminService {

    private static final Logger log = LoggerFactory.getLogger(CariUpdateAdminService.class);

    /** Bu oranın üstündeki benzerlik operatöre uyarı olarak gösteriliyor. */
    private static final int UYARI_ESIGI = 70;

    private final CariUpdateRequestRepository requestRepository;
    private final CariHesaplarRepository cariRepository;
    private final CariAdresRepository adresRepository;
    private final CariWriter cariWriter;

    public CariUpdateAdminService(CariUpdateRequestRepository requestRepository,
            CariHesaplarRepository cariRepository,
            CariAdresRepository adresRepository,
            CariWriter cariWriter) {
        this.requestRepository = requestRepository;
        this.cariRepository = cariRepository;
        this.adresRepository = adresRepository;
        this.cariWriter = cariWriter;
    }

    /** Reddedilen istekler; mesaj operatöre olduğu gibi gösteriliyor. */
    public static class GecersizIstek extends RuntimeException {
        public GecersizIstek(String mesaj) {
            super(mesaj);
        }
    }

    // ------------------------------------------------------------------
    // Okuma
    // ------------------------------------------------------------------

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public Map<String, Object> kuyruk(String durum, int limit) {
        List<CariUpdateRequest> talepler =
                requestRepository.listele(bosuNull(durum), PageRequest.of(0, Math.min(limit, 200)));

        List<Map<String, Object>> data = new ArrayList<>();
        for (CariUpdateRequest t : talepler) {
            data.add(talepDto(t, true));
        }

        Map<String, Object> cevap = new LinkedHashMap<>();
        cevap.put("data", data);
        cevap.put("bekleyen", requestRepository.countByStatus(CariUpdateRequest.STATUS_BEKLIYOR));
        return cevap;
    }

    /** Menüdeki rozet. */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public long bekleyenSayisi() {
        return requestRepository.countByStatus(CariUpdateRequest.STATUS_BEKLIYOR);
    }

    /** Bir carinin Mikro'daki mevcut adresleri — "bu cariye adres ekle" ekranı için. */
    @Transactional(transactionManager = "mikroTransactionManager", readOnly = true)
    public Map<String, Object> cariAdresleri(String cariKod) {
        CariHesap cari = cariRepository.findActiveByCariKod(cariKod)
                .orElseThrow(() -> new GecersizIstek("Mikro'da böyle bir cari yok: " + cariKod));

        Map<String, Object> cevap = new LinkedHashMap<>();
        cevap.put("cari_kod", cari.getCariKod());
        cevap.put("unvan", cari.getCariUnvan1());
        cevap.put("adresler", mevcutAdresler(cariKod));
        return cevap;
    }

    // ------------------------------------------------------------------
    // Yazma
    // ------------------------------------------------------------------

    /** Aktarmadan önce operatörün yaptığı düzeltmeler; null alan değişmez. */
    public record TalepYamasi(
            String adres_baslik,
            String adres_cadde,
            String adres_mahalle,
            String adres_sokak,
            String adres_semt,
            String adres_apt_no,
            String adres_daire_no,
            String adres_ilce,
            String adres_il,
            String adres_posta_kodu,
            String adres_telefon,
            Integer hedef_adres_no,
            String cari_unvan,
            String cari_telefon,
            String cari_email) {
    }

    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> duzenle(Long id, TalepYamasi yama) {
        CariUpdateRequest t = bekleyenTalep(id);

        if (yama != null) {
            if (yama.adres_baslik() != null)   t.setAdresBaslik(sinirla(yama.adres_baslik(), 10, "Adres başlığı"));
            if (yama.adres_cadde() != null)    t.setAdresCadde(sinirla(yama.adres_cadde(), 50, "Adres"));
            if (yama.adres_mahalle() != null)  t.setAdresMahalle(sinirla(yama.adres_mahalle(), 50, "Mahalle"));
            if (yama.adres_sokak() != null)    t.setAdresSokak(sinirla(yama.adres_sokak(), 50, "Sokak"));
            if (yama.adres_semt() != null)     t.setAdresSemt(sinirla(yama.adres_semt(), 25, "Semt"));
            if (yama.adres_apt_no() != null)   t.setAdresAptNo(sinirla(yama.adres_apt_no(), 10, "Apartman no"));
            if (yama.adres_daire_no() != null) t.setAdresDaireNo(sinirla(yama.adres_daire_no(), 10, "Daire no"));
            if (yama.adres_ilce() != null)     t.setAdresIlce(sinirla(yama.adres_ilce(), 15, "İlçe"));
            if (yama.adres_il() != null)       t.setAdresIl(sinirla(yama.adres_il(), 15, "İl"));
            if (yama.adres_posta_kodu() != null) t.setAdresPostaKodu(sinirla(yama.adres_posta_kodu(), 8, "Posta kodu"));

            if (yama.adres_telefon() != null) {
                TelefonAyirici.Telefon tel = TelefonAyirici.ayir(yama.adres_telefon());
                t.setAdresTelUlke(bosuNull(tel.ulkeKodu()));
                t.setAdresTelBolge(bosuNull(tel.bolgeKodu()));
                t.setAdresTelNo(bosuNull(tel.numara()));
            }

            if (yama.hedef_adres_no() != null) t.setHedefAdresNo(yama.hedef_adres_no());
            if (yama.cari_unvan() != null)     t.setCariUnvan(sinirla(yama.cari_unvan(), 50, "Unvan"));
            if (yama.cari_telefon() != null)   t.setCariTelefon(sinirla(yama.cari_telefon(), 20, "Telefon"));
            if (yama.cari_email() != null)     t.setCariEmail(sinirla(yama.cari_email(), 80, "E-posta"));
        }

        requestRepository.save(t);
        return talepDto(t, true);
    }

    /**
     * Talebi ERP'ye aktarır.
     *
     * <p>
     * İki veritabanı yazması var ve <b>tek işlemde değiller</b>: Mikro yazması
     * kendi işleminde, kuyruk satırının işaretlenmesi bizim veritabanımızda.
     * Dağıtık işlem kurmuyoruz. Sıra bilinçli: <b>önce ERP, sonra işaret</b>.
     * Tersi olsaydı ERP yazması patladığında kuyrukta "aktarıldı" görünen ama
     * ERP'de olmayan bir adres kalırdı — sessiz ve fark edilmesi zor. Bu sırada
     * ise en kötü ihtimalle ERP'ye yazılmış ama kuyrukta bekleyen görünen bir
     * talep kalır; operatör tekrar aktarmayı denediğinde benzerlik uyarısı
     * %100 çıkar ve durumu görür.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> aktar(Long id, Long staffId) {
        CariUpdateRequest t = bekleyenTalep(id);

        switch (t.getRequestType()) {
            case CariUpdateRequest.TYPE_ADRES_EKLE -> {
                int adresNo = cariWriter.yeniAdres(new CariWriter.YeniAdres(
                        t.getCariKod(),
                        t.getAdresBaslik(),
                        t.getAdresCadde(),
                        t.getAdresMahalle(),
                        t.getAdresSokak(),
                        t.getAdresSemt(),
                        t.getAdresAptNo(),
                        t.getAdresDaireNo(),
                        t.getAdresIlce(),
                        t.getAdresIl(),
                        t.getAdresUlke(),
                        t.getAdresPostaKodu(),
                        t.getAdresTelUlke(),
                        t.getAdresTelBolge(),
                        t.getAdresTelNo()));
                t.setSonucAdresNo(adresNo);
            }
            case CariUpdateRequest.TYPE_FATURA_ADRESI -> {
                int etkilenen = cariWriter.cariGuncelle(new CariWriter.CariGuncelle(
                        t.getCariKod(), null, null, null, t.getHedefAdresNo()));
                if (etkilenen == 0) {
                    throw new GecersizIstek("Cari güncellenemedi: " + t.getCariKod());
                }
                t.setSonucAdresNo(t.getHedefAdresNo());
            }
            default -> {
                int etkilenen = cariWriter.cariGuncelle(new CariWriter.CariGuncelle(
                        t.getCariKod(), t.getCariUnvan(), t.getCariTelefon(),
                        t.getCariEmail(), null));
                if (etkilenen == 0) {
                    throw new GecersizIstek("Cari güncellenemedi: " + t.getCariKod());
                }
            }
        }

        t.setStatus(CariUpdateRequest.STATUS_AKTARILDI);
        t.setDecidedAt(OffsetDateTime.now());
        t.setDecidedBy(staffId);
        requestRepository.save(t);

        log.info("Talep ERP'ye aktarıldı: id={} tur={} cari={} sonucAdresNo={}",
                t.getId(), t.getRequestType(), t.getCariKod(), t.getSonucAdresNo());
        return talepDto(t, true);
    }

    /**
     * Talebi reddeder.
     *
     * <p>
     * Sebep <b>zorunlu</b>: müşteri bunu görüyor ve sebepsiz bir ret onu
     * telefona sarılmaya iter — yani reddin amacını boşa çıkarır.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> reddet(Long id, String neden, Long staffId) {
        CariUpdateRequest t = bekleyenTalep(id);

        String n = bosuNull(neden);
        if (n == null) {
            throw new GecersizIstek("Ret sebebi zorunlu — müşteri bu metni görecek.");
        }
        t.setRedNedeni(sinirla(n, 500, "Ret sebebi"));
        t.setStatus(CariUpdateRequest.STATUS_REDDEDILDI);
        t.setDecidedAt(OffsetDateTime.now());
        t.setDecidedBy(staffId);
        requestRepository.save(t);

        log.info("Talep reddedildi: id={} cari={}", t.getId(), t.getCariKod());
        return talepDto(t, true);
    }

    /**
     * Operatörün kendi yazdığı adres — kuyruk beklemeden doğrudan ERP'ye.
     *
     * <p>
     * 492 carinin hiç adresi yok ve bunların sipariş vermesi mümkün değil
     * ({@code sip_adresno} var olan bir satırı göstermek zorunda). Onları
     * müşteri talebi beklemeden doldurabilmek gerekiyor. Kuyruk atlanıyor
     * çünkü kuyruğun amacı <b>müşteri verisini denetlemek</b>; operatörün
     * kendi girdiği veri zaten denetlenmiş sayılır.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> adresEkle(String cariKod, TalepYamasi veri, Long staffId) {
        if (bosuNull(cariKod) == null) {
            throw new GecersizIstek("Cari kodu gerekli.");
        }
        if (bosuNull(veri.adres_il()) == null || bosuNull(veri.adres_ilce()) == null
                || bosuNull(veri.adres_cadde()) == null) {
            throw new GecersizIstek("İl, ilçe ve adres alanları zorunlu.");
        }

        TelefonAyirici.Telefon tel = TelefonAyirici.ayir(veri.adres_telefon());

        int adresNo = cariWriter.yeniAdres(new CariWriter.YeniAdres(
                cariKod.trim(),
                sinirla(veri.adres_baslik(), 10, "Adres başlığı"),
                sinirla(veri.adres_cadde(), 50, "Adres"),
                sinirla(veri.adres_mahalle(), 50, "Mahalle"),
                sinirla(veri.adres_sokak(), 50, "Sokak"),
                sinirla(veri.adres_semt(), 25, "Semt"),
                sinirla(veri.adres_apt_no(), 10, "Apartman no"),
                sinirla(veri.adres_daire_no(), 10, "Daire no"),
                sinirla(veri.adres_ilce(), 15, "İlçe"),
                sinirla(veri.adres_il(), 15, "İl"),
                "TÜRKIYE",
                sinirla(veri.adres_posta_kodu(), 8, "Posta kodu"),
                tel.ulkeKodu(), tel.bolgeKodu(), tel.numara()));

        log.info("Operatör doğrudan adres ekledi: cari={} adresNo={} personel={}",
                cariKod, adresNo, staffId);
        return Map.of("cari_kod", cariKod, "adres_no", adresNo);
    }

    // ------------------------------------------------------------------
    // DTO
    // ------------------------------------------------------------------

    private Map<String, Object> talepDto(CariUpdateRequest t, boolean detay) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", t.getId());
        d.put("tur", t.getRequestType());
        d.put("durum", t.getStatus());
        d.put("cari_kod", t.getCariKod());
        d.put("unvan", cariUnvani(t.getCariKod()));
        d.put("talep_eden", t.getRequestedEmail());
        d.put("olusturuldu", t.getCreatedAt());
        d.put("karar_tarihi", t.getDecidedAt());
        d.put("red_nedeni", t.getRedNedeni());
        d.put("sonuc_adres_no", t.getSonucAdresNo());

        d.put("adres_baslik", t.getAdresBaslik());
        d.put("adres_cadde", t.getAdresCadde());
        d.put("adres_mahalle", t.getAdresMahalle());
        d.put("adres_sokak", t.getAdresSokak());
        d.put("adres_semt", t.getAdresSemt());
        d.put("adres_apt_no", t.getAdresAptNo());
        d.put("adres_daire_no", t.getAdresDaireNo());
        d.put("adres_ilce", t.getAdresIlce());
        d.put("adres_il", t.getAdresIl());
        d.put("adres_posta_kodu", t.getAdresPostaKodu());
        d.put("adres_telefon", telefonBirlestir(t));
        d.put("hedef_adres_no", t.getHedefAdresNo());
        d.put("cari_unvan", t.getCariUnvan());
        d.put("cari_telefon", t.getCariTelefon());
        d.put("cari_email", t.getCariEmail());

        if (detay) {
            List<Map<String, Object>> mevcut = mevcutAdresler(t.getCariKod());
            d.put("mevcut_adresler", mevcut);
            // Yazılacak numara: operatör "sürpriz" yaşamasın. Numaralar 1'den
            // başlamıyor olabilir (205 satırda 0), bu yüzden hesaplanıp
            // gösteriliyor.
            d.put("yazilacak_adres_no", sonrakiNo(mevcut));
            if (CariUpdateRequest.TYPE_ADRES_EKLE.equals(t.getRequestType())) {
                d.put("benzerlik", benzerlikUyarisi(t, mevcut));
            }
        }
        return d;
    }

    private List<Map<String, Object>> mevcutAdresler(String cariKod) {
        List<Map<String, Object>> liste = new ArrayList<>();
        for (CariAdres a : adresRepository.findActiveByCariKod(cariKod)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("adres_no", a.getAdresNo());
            m.put("baslik", a.getAdresKodu());
            m.put("adres", adresMetni(a));
            m.put("ilce", a.getIlce());
            m.put("il", a.getIl());
            liste.add(m);
        }
        return liste;
    }

    private Integer sonrakiNo(List<Map<String, Object>> mevcut) {
        int en = 0;
        boolean varMi = false;
        for (Map<String, Object> m : mevcut) {
            Object no = m.get("adres_no");
            if (no instanceof Integer i) {
                varMi = true;
                en = Math.max(en, i);
            }
        }
        return varMi ? en + 1 : 1;
    }

    /**
     * Bekleyen adresin mevcutlarla benzerliği.
     *
     * @return en yüksek benzerlik {@link #UYARI_ESIGI} üstündeyse uyarı,
     *         değilse {@code null}
     */
    private Map<String, Object> benzerlikUyarisi(CariUpdateRequest t,
            List<Map<String, Object>> mevcut) {

        String yeni = normalize(birlestir(t.getAdresMahalle(), t.getAdresCadde(),
                t.getAdresSokak(), t.getAdresSemt(), t.getAdresIlce(), t.getAdresIl()));
        if (yeni.isBlank()) {
            return null;
        }

        int enYuksek = 0;
        Object enYakinNo = null;
        for (Map<String, Object> m : mevcut) {
            String eski = normalize(birlestir(
                    String.valueOf(m.getOrDefault("adres", "")),
                    String.valueOf(m.getOrDefault("ilce", "")),
                    String.valueOf(m.getOrDefault("il", ""))));
            int oran = benzerlik(yeni, eski);
            if (oran > enYuksek) {
                enYuksek = oran;
                enYakinNo = m.get("adres_no");
            }
        }

        if (enYuksek < UYARI_ESIGI) {
            return null;
        }
        return Map.of("oran", enYuksek, "adres_no", enYakinNo == null ? "" : enYakinNo);
    }

    /**
     * İki adres metninin kelime örtüşmesi (Jaccard, yüzde).
     *
     * <p>
     * Levenshtein yerine kelime kümesi: adreslerde sıra değişiyor ("Macun Mah.
     * 1443. Cad." / "1443 Cadde Macun Mahallesi") ve karakter mesafesi bunu
     * yakalayamıyor. Kesin bir ölçü değil, <b>uyarı</b> — kararı operatör
     * veriyor.
     * </p>
     */
    private static int benzerlik(String a, String b) {
        Set<String> x = new HashSet<>(Arrays.asList(a.split(" ")));
        Set<String> y = new HashSet<>(Arrays.asList(b.split(" ")));
        x.removeIf(String::isBlank);
        y.removeIf(String::isBlank);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        Set<String> kesisim = new HashSet<>(x);
        kesisim.retainAll(y);
        Set<String> birlesim = new HashSet<>(x);
        birlesim.addAll(y);
        return (int) Math.round(100.0 * kesisim.size() / birlesim.size());
    }

    /**
     * Karşılaştırma için sadeleştirme.
     *
     * <p>
     * Türkçe karakterler ASCII'ye indirgeniyor ve yaygın kısaltmalar
     * eşitleniyor: "Mah." ile "Mahallesi" aynı adresi anlatıyor ama ham
     * karşılaştırmada farklı görünürdü.
     * </p>
     */
    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.toLowerCase(Locale.forLanguageTag("tr"));
        t = t.replace('ı', 'i').replace('ş', 's').replace('ğ', 'g')
             .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.replaceAll("[^a-z0-9 ]", " ");
        t = t.replaceAll("\\bmahallesi\\b", "mah")
             .replaceAll("\\bmahalle\\b", "mah")
             .replaceAll("\\bcaddesi\\b", "cad")
             .replaceAll("\\bcadde\\b", "cad")
             .replaceAll("\\bsokagi\\b", "sok")
             .replaceAll("\\bsokak\\b", "sok")
             .replaceAll("\\bbulvari\\b", "bul")
             .replaceAll("\\bbulvar\\b", "bul");
        return t.replaceAll("\\s+", " ").trim();
    }

    private static String birlestir(String... parcalar) {
        StringBuilder sb = new StringBuilder();
        for (String p : parcalar) {
            if (p != null && !p.isBlank() && !"null".equals(p)) {
                sb.append(p.trim()).append(' ');
            }
        }
        return sb.toString();
    }

    private String adresMetni(CariAdres a) {
        return birlestir(a.getMahalle(), a.getCadde(), a.getSokak(),
                a.getAptNo() == null || a.getAptNo().isBlank() ? null : "No: " + a.getAptNo(),
                a.getDaireNo() == null || a.getDaireNo().isBlank() ? null : "D: " + a.getDaireNo(),
                a.getSemt()).trim();
    }

    private String telefonBirlestir(CariUpdateRequest t) {
        String b = bosuNull(t.getAdresTelBolge());
        String n = bosuNull(t.getAdresTelNo());
        if (n == null) {
            return null;
        }
        return b == null ? n : "0" + b + " " + n;
    }

    private String cariUnvani(String cariKod) {
        return cariRepository.findActiveByCariKod(cariKod)
                .map(CariHesap::getCariUnvan1)
                .orElse(null);
    }

    // ------------------------------------------------------------------

    private CariUpdateRequest bekleyenTalep(Long id) {
        CariUpdateRequest t = requestRepository.findById(id)
                .orElseThrow(() -> new GecersizIstek("Talep bulunamadı: " + id));
        if (!t.isBekliyor()) {
            throw new GecersizIstek("Bu talep zaten sonuçlanmış (" + t.getStatus() + ").");
        }
        return t;
    }

    private static String sinirla(String deger, int sinir, String alan) {
        String t = bosuNull(deger);
        if (t != null && t.length() > sinir) {
            throw new GecersizIstek(alan + " en fazla " + sinir
                    + " karakter olabilir (girilen: " + t.length() + ").");
        }
        return t;
    }

    private static String bosuNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
