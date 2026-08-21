package com.simge.adminbackend.pages;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.ImageLink;
import com.simge.adminbackend.appdb.model.PageBlock;
import com.simge.adminbackend.appdb.repository.PageBlockRepository;
import com.simge.adminbackend.image.ImageService;

/**
 * Düz sayfa içeriğinin yönetimi (ADR D-172).
 *
 * <p>
 * Hakkımızda metni vitrin paketine gömülüydü ve yalnızca Türkçeydi. Artık
 * {@code SIMGE_OS_APP} veritabanında iki dilde duruyor; bu servis oraya
 * yazıyor, vitrin aynı satırları okuyor. Buradan yapılan değişiklik
 * <b>yeniden yayın gerektirmeden</b> vitrine yansıyor.
 * </p>
 *
 * <h2>Blok eklenmiyor, silinmiyor</h2>
 * <p>
 * Her blok anahtarının karşılığı vitrinde elle yazılmış bir yuva. Panelden
 * blok eklemek hiçbir şey çizmez; silmek ise geri getirilemez biçimde içerik
 * kaybettirir. Yapılabilen üç şey var: metnini değiştirmek, görselini
 * değiştirmek, bloğu kapatmak.
 * </p>
 */
@Service
public class PageContentAdminService {

    private static final Logger log = LoggerFactory.getLogger(PageContentAdminService.class);

    /** {@code title_tr} / {@code title_en} sütunlarının genişliği. */
    private static final int BASLIK_SINIRI = 255;

    private final PageBlockRepository blockRepository;
    private final ImageService imageService;

    public PageContentAdminService(PageBlockRepository blockRepository, ImageService imageService) {
        this.blockRepository = blockRepository;
        this.imageService = imageService;
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

    /**
     * Bir sayfanın blokları — <b>iki dil bir arada</b>.
     *
     * <p>
     * Vitrin ucu içeriği tek dile çözüyor; panelin buna ihtiyacı yok, tam
     * tersi: operatör Türkçe ile İngilizceyi yan yana görmeden çevirinin
     * eksik olup olmadığını göremez.
     * </p>
     *
     * <p>
     * Pasif bloklar da dönüyor. Vitrin onları hiç görmüyor ama panelde
     * gizlenselerdi kapatılan bir blok ekrandan kaybolur ve geri açılamazdı.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public Map<String, Object> sayfa(String sayfaAnahtari) {
        String anahtar = sayfaDogrula(sayfaAnahtari);

        List<Map<String, Object>> bloklar = new ArrayList<>();
        for (PageBlock blok : blockRepository.findByPageKeyOrderBySortOrderAscIdAsc(anahtar)) {
            bloklar.add(blokDto(blok));
        }

        Map<String, Object> cevap = new LinkedHashMap<>();
        cevap.put("sayfa", anahtar);
        cevap.put("bloklar", bloklar);
        return cevap;
    }

    // ------------------------------------------------------------------
    // Yazma
    // ------------------------------------------------------------------

    /**
     * Blok güncellemesi.
     *
     * <p>
     * <b>Null bırakılan alan değişmez.</b> Boş dize ise gerçek bir değer:
     * "bu alanı temizle" demek. İkisini ayırmak zorundayız, yoksa girilmiş bir
     * çeviriyi geri almak mümkün olmazdı.
     * </p>
     */
    public record BlokYama(
            Boolean aktif,
            String baslik_tr,
            String baslik_en,
            String metin_tr,
            String metin_en) {
    }

    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> blokGuncelle(Long id, BlokYama yama, Long staffId) {
        PageBlock blok = blok(id);

        if (yama == null) {
            return blokDto(blok);
        }

        if (yama.aktif() != null) {
            blok.setActive(yama.aktif());
        }
        if (yama.baslik_tr() != null) {
            blok.setTitleTr(baslikDogrula(yama.baslik_tr(), "Türkçe başlık"));
        }
        if (yama.baslik_en() != null) {
            blok.setTitleEn(baslikDogrula(yama.baslik_en(), "İngilizce başlık"));
        }
        if (yama.metin_tr() != null) {
            blok.setBodyTr(bosuNull(yama.metin_tr()));
        }
        if (yama.metin_en() != null) {
            blok.setBodyEn(bosuNull(yama.metin_en()));
        }

        // Türkçesi olmayan AÇIK bir blok vitrinde boş bir kutu olarak görünür:
        // Türkçe hem varsayılan dil hem de İngilizce tarafın yedeği (vitrin,
        // İngilizce alan boşsa Türkçesine düşüyor). İngilizceyi boş bırakmak
        // bu yüzden serbest, Türkçeyi boşaltmak değil. Bloğu göstermemenin
        // yolu onu kapatmak.
        if (Boolean.TRUE.equals(blok.getActive())
                && bos(blok.getTitleTr()) && bos(blok.getBodyTr())) {
            throw new GecersizIstek(
                    "Açık bir bloğun Türkçe içeriği tamamen boş olamaz: Türkçe hem "
                            + "varsayılan dil hem de İngilizcenin yedeği. Bloğu vitrinde "
                            + "göstermek istemiyorsanız kapatın.");
        }

        blok.setUpdatedAt(OffsetDateTime.now());
        blok.setUpdatedBy(staffId);
        blockRepository.save(blok);

        log.info("Sayfa bloğu güncellendi: sayfa={} blok={} id={}",
                blok.getPageKey(), blok.getBlockKey(), blok.getId());

        return blokDto(blok);
    }

    /**
     * Bloğun görselini değiştirir.
     *
     * <p>
     * Baytlar ürün görselleriyle <b>aynı depoda</b> ({@code SIMGE_IMAGE_BLOB}):
     * aynı küçültme, aynı içerik adresli tekilleştirme. Bloğun sütununa yazılan
     * şey adres. Eski bağ kaldırılıyor ki bir yuvada tek bağ kalsın; baytlar
     * duruyor.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> gorselYukle(Long id, byte[] baytlar, String dosyaAdi, Long staffId)
            throws IOException {

        PageBlock blok = blok(id);

        baglariKaldir(blok);
        ImageService.Yukleme sonuc = imageService.yukle(
                ImageLink.OWNER_PAGE, gorselAnahtari(blok), baytlar, dosyaAdi, staffId, true);

        blok.setImageUrl("/api/images/" + sonuc.contentHash() + "/detail.jpg");
        blok.setUpdatedAt(OffsetDateTime.now());
        blok.setUpdatedBy(staffId);
        blockRepository.save(blok);

        log.info("Sayfa bloğu görseli değişti: blok={} hash={}",
                blok.getBlockKey(), sonuc.contentHash().substring(0, 12));

        return blokDto(blok);
    }

    /** Bloğun görselini kaldırır; baytlar durur (aynı görsel başka yerde de olabilir). */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> gorselKaldir(Long id, Long staffId) {
        PageBlock blok = blok(id);

        baglariKaldir(blok);
        blok.setImageUrl(null);
        blok.setUpdatedAt(OffsetDateTime.now());
        blok.setUpdatedBy(staffId);
        blockRepository.save(blok);

        return blokDto(blok);
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    private Map<String, Object> blokDto(PageBlock blok) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", blok.getId());
        dto.put("anahtar", blok.getBlockKey());
        dto.put("tur", blok.getBlockType());
        dto.put("ad", PageEtiketleri.ad(blok.getBlockKey()));
        dto.put("nerede", PageEtiketleri.nerede(blok.getBlockKey()));
        dto.put("aktif", Boolean.TRUE.equals(blok.getActive()));
        dto.put("sira", blok.getSortOrder());
        dto.put("gorsel", blok.getImageUrl());
        dto.put("baslik_tr", blok.getTitleTr());
        dto.put("baslik_en", blok.getTitleEn());
        dto.put("metin_tr", blok.getBodyTr());
        dto.put("metin_en", blok.getBodyEn());
        dto.put("guncellendi", blok.getUpdatedAt());
        return dto;
    }

    private PageBlock blok(Long id) {
        if (id == null) {
            throw new GecersizIstek("Blok kimliği gerekli.");
        }
        return blockRepository.findById(id)
                .orElseThrow(() -> new GecersizIstek("Blok bulunamadı: " + id));
    }

    /**
     * Sayfa anahtarını doğrular.
     *
     * <p>
     * Beyaz liste, çünkü bilinmeyen bir anahtar için boş liste dönmek
     * operatöre "bu sayfanın içeriği silinmiş" gibi görünürdü. Yeni bir düz
     * sayfa panelden yönetilecekse anahtarı buraya eklenir.
     * </p>
     */
    private String sayfaDogrula(String sayfa) {
        String s = sayfa == null || sayfa.isBlank() ? PageBlock.PAGE_ABOUT : sayfa.trim();
        if (!PageBlock.PAGE_ABOUT.equals(s)) {
            throw new GecersizIstek("Bilinmeyen sayfa: " + sayfa);
        }
        return s;
    }

    private String baslikDogrula(String deger, String alanAdi) {
        String t = bosuNull(deger);
        if (t != null && t.length() > BASLIK_SINIRI) {
            throw new GecersizIstek(alanAdi + " en fazla " + BASLIK_SINIRI
                    + " karakter olabilir (girilen: " + t.length() + ").");
        }
        return t;
    }

    private void baglariKaldir(PageBlock blok) {
        String anahtar = gorselAnahtari(blok);
        for (ImageLink bag : imageService.listele(ImageLink.OWNER_PAGE, anahtar)) {
            imageService.bagiKaldir(ImageLink.OWNER_PAGE, anahtar, bag.getId());
        }
    }

    private String gorselAnahtari(PageBlock blok) {
        return String.valueOf(blok.getId());
    }

    private static String bosuNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean bos(String s) {
        return s == null || s.isBlank();
    }
}
