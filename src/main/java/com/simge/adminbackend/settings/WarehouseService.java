package com.simge.adminbackend.settings;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.AppSetting;
import com.simge.adminbackend.appdb.repository.AppSettingRepository;
import com.simge.adminbackend.erp.model.Depo;
import com.simge.adminbackend.erp.repository.DepoDolulukRepository;
import com.simge.adminbackend.erp.repository.DepoRepository;

/**
 * Vitrinin deposunu okur ve değiştirir (ADR D-152).
 *
 * <h2>Neden bu kadar çok denetim var</h2>
 * <p>
 * Bu, panelde yapılabilecek en yıkıcı ayar değişikliği. Depo yalnızca bir
 * numara değil: vitrindeki <b>her fiyat</b> ve <b>her stok</b> o depodan
 * okunuyor. Yanlış bir sayı yazmak katalogu boşaltır ya da — daha kötüsü —
 * ürünleri yanlış fiyatla satar. Bu yüzden yazma yolu üç şeyi denetliyor:
 * </p>
 * <ol>
 *   <li>Depo Mikro'nun {@code DEPOLAR} tablosunda gerçekten var mı,</li>
 *   <li>iptal edilmiş mi,</li>
 *   <li>içinde vitrine çıkacak ürün var mı (fiyatlı ve stok hareketli).</li>
 * </ol>
 *
 * <h2>Depo 0 neden reddediliyor</h2>
 * <p>
 * 0 numaralı depo {@code DEPOLAR}'da hiç yok. Fiyat tarafında 0 "genel liste"
 * anlamına geldiği için çalışıyor <b>gibi görünür</b>, ama
 * {@code STOK_HAREKETLERI} içinde 0 depolu hareket yok: katalogdaki her ürün
 * stoksuz görünür ve "Sepete Ekle" hiç açılmaz (D-137'de yaşandı).
 * </p>
 */
@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    /**
     * Bir deponun vitrine yeteceğinin alt sınırı.
     *
     * <p>
     * Ölçüme dayanıyor: gerçek satış depolarında fiyatlı ürün 7.300 civarı,
     * kullanılmayan depolarda 1.100-1.700. Sınır, "operatör yanlışlıkla boş bir
     * depo seçti" durumunu yakalamak için var; dolu bir depoyu engellememeli.
     * </p>
     */
    private static final int UYARI_ESIGI = 100;

    private final AppSettingRepository settingRepository;
    private final DepoRepository depoRepository;
    private final DepoDolulukRepository dolulukRepository;

    public WarehouseService(AppSettingRepository settingRepository,
            DepoRepository depoRepository,
            DepoDolulukRepository dolulukRepository) {
        this.settingRepository = settingRepository;
        this.depoRepository = depoRepository;
        this.dolulukRepository = dolulukRepository;
    }

    /** Seçim ekranındaki bir satır. */
    public record DepoSatiri(
            int no,
            String ad,
            int fiyatliUrun,
            int stokluUrun,
            boolean secili,
            /** false ise panel bu satırı seçtirmiyor ve sebebini gösteriyor. */
            boolean uygun,
            String uyari) {
    }

    /** Yazma reddedildiğinde fırlatılır; mesajı doğrudan operatöre gösteriliyor. */
    public static class GecersizDepo extends RuntimeException {
        public GecersizDepo(String mesaj) {
            super(mesaj);
        }
    }

    /**
     * Şu anki depo.
     *
     * <p>
     * Ayar satırı yoksa <b>uydurmuyoruz</b>: vitrin bu durumda kendi yedeğine
     * düşüyor ve panelin onu tahmin etmesi yanlış bilgi göstermek olurdu.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public Integer mevcutDepo() {
        return settingRepository.findById(AppSetting.KEY_WAREHOUSE)
                .map(AppSetting::getValue)
                .map(String::trim)
                .map(v -> {
                    try {
                        return Integer.valueOf(v);
                    } catch (NumberFormatException e) {
                        log.warn("Depo ayarı sayı değil: '{}'", v);
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Seçilebilir depolar, doluluk sayılarıyla.
     *
     * <p>
     * Sayılar süs değil: operatörün bir depoyu seçmeden önce görmesi gereken
     * tek bilgi bu. "SANAL DEPO" adı masum görünür; yanında {@code 0 / 0}
     * yazması onu seçilemez yapar.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public List<DepoSatiri> depolar() {
        Integer mevcut = mevcutDepo();
        Map<Integer, DepoDolulukRepository.Doluluk> doluluk = dolulukRepository.hepsi();

        List<DepoSatiri> satirlar = new ArrayList<>();
        for (Depo d : depoRepository.secilebilirler()) {
            DepoDolulukRepository.Doluluk dol =
                    doluluk.getOrDefault(d.getNo(), new DepoDolulukRepository.Doluluk(0, 0));

            String uyari = uyari(dol);
            satirlar.add(new DepoSatiri(
                    d.getNo(),
                    d.getAdi() == null ? "" : d.getAdi().trim(),
                    dol.fiyatliUrun(),
                    dol.stokluUrun(),
                    mevcut != null && mevcut.equals(d.getNo()),
                    uyari == null,
                    uyari));
        }
        return satirlar;
    }

    /**
     * Depoyu değiştirir.
     *
     * <p>
     * <b>Mikro'ya yazmaz.</b> Değişen tek şey {@code SIMGE_OS_APP}'teki ayar
     * satırı; ERP salt okunur (D-100).
     * </p>
     *
     * @throws GecersizDepo depo yoksa, iptalliyse ya da vitrini boşaltacaksa
     */
    @Transactional(transactionManager = "appTransactionManager")
    public DepoSatiri degistir(int yeniDepo, Long staffId) {
        if (yeniDepo <= 0) {
            // Ayrı bir mesaj: 0 yazmak yaygın bir hata ve "depo bulunamadı"
            // demek sebebi gizlerdi.
            throw new GecersizDepo("Depo 0 kullanılamaz: Mikro'da 0 numaralı depo yok ve "
                    + "stok hareketlerinde hiç geçmiyor. Seçilirse katalogdaki tüm ürünler "
                    + "stoksuz görünür.");
        }

        Depo depo = depoRepository.findFirstByNo(yeniDepo)
                .orElseThrow(() -> new GecersizDepo(
                        "Mikro'da " + yeniDepo + " numaralı depo yok."));

        if (Boolean.TRUE.equals(depo.getIptal())) {
            throw new GecersizDepo("Depo " + yeniDepo + " Mikro'da iptal edilmiş.");
        }

        DepoDolulukRepository.Doluluk dol = dolulukRepository.hepsi()
                .getOrDefault(yeniDepo, new DepoDolulukRepository.Doluluk(0, 0));

        String uyari = uyari(dol);
        if (uyari != null) {
            throw new GecersizDepo(uyari);
        }

        AppSetting ayar = settingRepository.findById(AppSetting.KEY_WAREHOUSE)
                .orElseGet(() -> {
                    AppSetting yeni = new AppSetting();
                    yeni.setKey(AppSetting.KEY_WAREHOUSE);
                    yeni.setDescription("Vitrinin deposu. Fiyat da stok da BU depodan "
                            + "okunur — bölünmez.");
                    return yeni;
                });

        String onceki = ayar.getValue();
        ayar.setValue(String.valueOf(yeniDepo));
        ayar.setUpdatedAt(OffsetDateTime.now());
        ayar.setUpdatedBy(staffId);
        settingRepository.save(ayar);

        // Bu satır bilerek INFO: depo değişikliği vitrindeki her fiyatı
        // etkiliyor ve sonradan "ne zaman değişti" diye sorulacak ilk şey.
        log.info("Vitrin deposu değişti: {} -> {} ({}), personel={}, fiyatlı={}, stoklu={}",
                onceki, yeniDepo, depo.getAdi(), staffId, dol.fiyatliUrun(), dol.stokluUrun());

        return new DepoSatiri(yeniDepo,
                depo.getAdi() == null ? "" : depo.getAdi().trim(),
                dol.fiyatliUrun(), dol.stokluUrun(), true, true, null);
    }

    /**
     * Deponun vitrine yetip yetmediği.
     *
     * @return engelleme sebebi, sorun yoksa {@code null}
     */
    private String uyari(DepoDolulukRepository.Doluluk dol) {
        if (dol.fiyatliUrun() < UYARI_ESIGI) {
            return "Bu depoda fiyatı olan ürün sayısı " + dol.fiyatliUrun()
                    + ". Seçilirse vitrinde neredeyse hiç ürün görünmez.";
        }
        if (dol.stokluUrun() < UYARI_ESIGI) {
            return "Bu depoda stok hareketi olan ürün sayısı " + dol.stokluUrun()
                    + ". Seçilirse ürünler görünür ama hepsi stoksuz çıkar ve "
                    + "\"Sepete Ekle\" açılmaz.";
        }
        return null;
    }
}
