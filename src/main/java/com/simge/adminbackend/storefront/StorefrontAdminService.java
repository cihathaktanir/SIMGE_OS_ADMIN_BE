package com.simge.adminbackend.storefront;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.HomeSection;
import com.simge.adminbackend.appdb.model.HomeSectionItem;
import com.simge.adminbackend.appdb.model.ImageLink;
import com.simge.adminbackend.appdb.model.StoreTheme;
import com.simge.adminbackend.appdb.repository.HomeSectionItemRepository;
import com.simge.adminbackend.appdb.repository.HomeSectionRepository;
import com.simge.adminbackend.appdb.repository.StoreThemeRepository;
import com.simge.adminbackend.erp.model.AnaGrup;
import com.simge.adminbackend.erp.model.Stok;
import com.simge.adminbackend.erp.repository.AnaGrupRepository;
import com.simge.adminbackend.erp.repository.StokRepository;
import com.simge.adminbackend.image.ImageService;

/**
 * Vitrin ana sayfasının panelden yönetimi (ADR D-154).
 *
 * <h2>Ne yönetiliyor, ne yönetilmiyor</h2>
 * <p>
 * Bölümler ({@code section_key}) <b>eklenip silinmiyor</b>: her anahtar vitrin
 * şablonundaki bir yuvanın adı. Şablonda karşılığı olmayan bir anahtar
 * eklemek hiçbir şey çizmez, var olanı silmek ise o yuvayı sessizce boşaltır
 * ve geri getirmek için veritabanına elle satır yazmak gerekir. Panel
 * bölümlerin <b>içeriğini</b> yönetiyor: başlık, sıra, açık/kapalı ve öğeler.
 * </p>
 *
 * <h2>ERP'ye yazılmıyor</h2>
 * <p>
 * Ürün ve kategori referansları Mikro'ya <b>bakıyor</b> ama oraya hiçbir şey
 * yazmıyor: yazılan tek yer {@code SIMGE_OS_APP} (D-100). Mikro'dan okunan
 * isimler yalnızca panelde göstermek için — operatör 8.238 ürünlük katalogda
 * "83308" yerine ürünün adını görsün diye.
 * </p>
 */
@Service
public class StorefrontAdminService {

    private static final Logger log = LoggerFactory.getLogger(StorefrontAdminService.class);

    /** Öğe görsellerinin dil kodları. */
    private static final String DIL_TR = "tr";
    private static final String DIL_EN = "en";

    private final StoreThemeRepository themeRepository;
    private final HomeSectionRepository sectionRepository;
    private final HomeSectionItemRepository itemRepository;
    private final StokRepository stokRepository;
    private final AnaGrupRepository anaGrupRepository;
    private final ImageService imageService;

    public StorefrontAdminService(StoreThemeRepository themeRepository,
            HomeSectionRepository sectionRepository,
            HomeSectionItemRepository itemRepository,
            StokRepository stokRepository,
            AnaGrupRepository anaGrupRepository,
            ImageService imageService) {
        this.themeRepository = themeRepository;
        this.sectionRepository = sectionRepository;
        this.itemRepository = itemRepository;
        this.stokRepository = stokRepository;
        this.anaGrupRepository = anaGrupRepository;
        this.imageService = imageService;
    }

    /** İstek reddedildiğinde; mesaj operatöre gösteriliyor. */
    public static class GecersizIstek extends RuntimeException {
        public GecersizIstek(String mesaj) {
            super(mesaj);
        }
    }

    // ------------------------------------------------------------------
    // Okuma
    // ------------------------------------------------------------------

    /**
     * Aktif temanın tüm bölümleri, öğeleriyle ve <b>çözülmüş adlarıyla</b>.
     *
     * <p>
     * Ürün/kategori adları Mikro'dan tek sorguda okunuyor. Öğe başına sorgu
     * atmak, dört ürün listesi × sekizer ürün = 32 gidiş-dönüş demekti.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public Map<String, Object> anaSayfa(String temaSlug) {
        StoreTheme tema = temaBul(temaSlug);
        List<HomeSection> bolumler = sectionRepository.temaninBolumleri(tema.getSlug());

        // Referansları TEK seferde çöz.
        Map<Long, String> urunAdlari = urunAdlari(bolumler);
        Map<Long, AnaGrup> kategoriler = kategoriler(bolumler);

        List<Map<String, Object>> cikti = new ArrayList<>(bolumler.size());
        for (HomeSection b : bolumler) {
            cikti.add(bolumDto(b, urunAdlari, kategoriler));
        }

        Map<String, Object> cevap = new LinkedHashMap<>();
        cevap.put("tema", temaDto(tema));
        cevap.put("temalar", themeRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::temaDto).toList());
        cevap.put("bolumler", cikti);
        return cevap;
    }

    /** Görsel yüklenebilecek kategoriler; panelin kategori görseli ekranı için. */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public List<Map<String, Object>> kategoriListesi() {
        List<AnaGrup> gruplar = anaGrupRepository.aktifler();

        List<String> kodlar = gruplar.stream()
                .map(g -> g.getKod() == null ? "" : g.getKod().trim())
                .filter(k -> !k.isEmpty())
                .toList();
        // Hangilerinin görseli var — operatör eksikleri tek bakışta görsün;
        // ürün arama ekranındaki `gorsel_var` ile aynı desen.
        var gorselli = new java.util.HashSet<>(
                imageService.gorseliOlanlar(ImageLink.OWNER_CATEGORY, kodlar));

        List<Map<String, Object>> data = new ArrayList<>(gruplar.size());
        for (AnaGrup g : gruplar) {
            String kod = g.getKod() == null ? "" : g.getKod().trim();
            Map<String, Object> satir = new LinkedHashMap<>();
            satir.put("recno", g.getRecno());
            satir.put("kod", kod);
            satir.put("isim", g.getIsim() == null ? "" : g.getIsim().trim());
            satir.put("gorsel_var", gorselli.contains(kod));
            data.add(satir);
        }
        return data;
    }

    // ------------------------------------------------------------------
    // Tema
    // ------------------------------------------------------------------

    /**
     * Aktif temayı değiştirir.
     *
     * <p>
     * Diğerlerinin durumu <b>aynı işlemde</b> 0'a çekiliyor: iki aktif tema
     * kaldığında vitrinin hangisini çizeceği tanımsız olurdu.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> temaAktifle(String slug) {
        StoreTheme hedef = temaBul(slug);
        List<StoreTheme> hepsi = themeRepository.findAllByOrderBySortOrderAscIdAsc();
        for (StoreTheme t : hepsi) {
            t.setStatus(t.getId().equals(hedef.getId()) ? StoreTheme.AKTIF : StoreTheme.PASIF);
        }
        themeRepository.saveAll(hepsi);
        log.info("Aktif vitrin teması: {}", hedef.getSlug());
        return temaDto(hedef);
    }

    // ------------------------------------------------------------------
    // Bölüm
    // ------------------------------------------------------------------

    /** Bölüm güncellemesi; null bırakılan alan değişmez. */
    public record BolumGuncelle(
            Boolean aktif, Integer sira,
            String baslikTr, String baslikEn,
            String altBaslikTr, String altBaslikEn) {
    }

    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> bolumGuncelle(Long id, BolumGuncelle istek) {
        HomeSection b = sectionRepository.findById(id)
                .orElseThrow(() -> new GecersizIstek("Bölüm bulunamadı: " + id));

        if (istek.aktif() != null) {
            b.setActive(istek.aktif());
        }
        if (istek.sira() != null) {
            b.setSortOrder(istek.sira());
        }
        // Boş metin ile null aynı şey: operatör alanı temizlediğinde başlık
        // gerçekten kalksın.
        if (istek.baslikTr() != null) {
            b.setTitleTr(bosNull(istek.baslikTr()));
        }
        if (istek.baslikEn() != null) {
            b.setTitleEn(bosNull(istek.baslikEn()));
        }
        if (istek.altBaslikTr() != null) {
            b.setSubtitleTr(bosNull(istek.altBaslikTr()));
        }
        if (istek.altBaslikEn() != null) {
            b.setSubtitleEn(bosNull(istek.altBaslikEn()));
        }

        sectionRepository.save(b);
        return bolumDto(b, urunAdlari(List.of(b)), kategoriler(List.of(b)));
    }

    // ------------------------------------------------------------------
    // Öğe
    // ------------------------------------------------------------------

    /** Öğe ekleme/güncelleme; güncellemede null bırakılan alan değişmez. */
    public record OgeIstegi(
            String refTuru, String refId,
            Boolean aktif, Integer sira,
            String baslikTr, String baslikEn,
            String altBaslikTr, String altBaslikEn,
            String etiketTr, String etiketEn,
            String butonTr, String butonEn,
            String bagTuru, String bagDegeri) {
    }

    /**
     * Bölüme öğe ekler.
     *
     * <p>
     * Öğenin türü bölümün türünden belirleniyor, istekten değil: kategori
     * şeridine ürün eklemek ya da banner grubuna kategori eklemek vitrinde
     * sessizce hiçbir şey çizmezdi.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> ogeEkle(Long bolumId, OgeIstegi istek) {
        HomeSection b = sectionRepository.findById(bolumId)
                .orElseThrow(() -> new GecersizIstek("Bölüm bulunamadı: " + bolumId));

        String refTuru = refTuru(b.getSectionType());
        HomeSectionItem oge = new HomeSectionItem();
        oge.setSection(b);
        oge.setRefType(refTuru);
        oge.setActive(istek.aktif() == null || istek.aktif());
        oge.setSortOrder(istek.sira() != null ? istek.sira() : sonrakiSira(b));

        // Referanslı türlerde hedefin Mikro'da gerçekten var olduğunu
        // doğruluyoruz: olmayan bir recno, vitrinde sessizce eksik bir karo
        // demek olurdu.
        if (HomeSectionItem.REF_PRODUCT.equals(refTuru) || HomeSectionItem.REF_CATEGORY.equals(refTuru)) {
            oge.setRefId(refDogrula(refTuru, istek.refId()));
        }

        alanlariUygula(oge, istek);
        itemRepository.save(oge);
        return ogeDto(oge, urunAdlari(List.of(b)), kategoriler(List.of(b)));
    }

    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> ogeGuncelle(Long id, OgeIstegi istek) {
        HomeSectionItem oge = itemRepository.findById(id)
                .orElseThrow(() -> new GecersizIstek("Öğe bulunamadı: " + id));

        if (istek.aktif() != null) {
            oge.setActive(istek.aktif());
        }
        if (istek.sira() != null) {
            oge.setSortOrder(istek.sira());
        }
        if (istek.refId() != null && (HomeSectionItem.REF_PRODUCT.equals(oge.getRefType())
                || HomeSectionItem.REF_CATEGORY.equals(oge.getRefType()))) {
            oge.setRefId(refDogrula(oge.getRefType(), istek.refId()));
        }
        alanlariUygula(oge, istek);

        itemRepository.save(oge);
        HomeSection b = oge.getSection();
        return ogeDto(oge, urunAdlari(List.of(b)), kategoriler(List.of(b)));
    }

    /**
     * Öğeyi siler.
     *
     * <p>
     * <b>Görselin baytları silinmiyor</b> — aynı hash başka bir öğeye de bağlı
     * olabilir ve ham dosya saklanmadığı için geri dönüşü yok (V17'deki not).
     * Silinen tek şey öğe satırı ve görsel bağı.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public void ogeSil(Long id) {
        HomeSectionItem oge = itemRepository.findById(id)
                .orElseThrow(() -> new GecersizIstek("Öğe bulunamadı: " + id));
        baglariKaldir(oge, DIL_TR);
        baglariKaldir(oge, DIL_EN);
        itemRepository.delete(oge);
    }

    /**
     * Öğelerin sırasını topluca yazar.
     *
     * <p>
     * Tek tek güncelleme yerine toplu: sürükle-bırak sonrası tüm liste yeniden
     * numaralanıyor ve ara durumların kaydedilmesi "iki öğe aynı sırada"
     * görüntüsü üretirdi.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public void ogeleriSirala(Long bolumId, List<Long> sirali) {
        HomeSection b = sectionRepository.findById(bolumId)
                .orElseThrow(() -> new GecersizIstek("Bölüm bulunamadı: " + bolumId));

        Map<Long, HomeSectionItem> mevcut = new HashMap<>();
        for (HomeSectionItem o : itemRepository.findBySectionOrderBySortOrderAscIdAsc(b)) {
            mevcut.put(o.getId(), o);
        }

        int sira = 1;
        List<HomeSectionItem> yazilacak = new ArrayList<>();
        for (Long id : sirali) {
            HomeSectionItem o = mevcut.get(id);
            if (o == null) {
                // Başka bir bölümün öğesi gönderilmiş: sessizce atlamak yerine
                // reddediyoruz, yoksa sıralama yarım uygulanırdı.
                throw new GecersizIstek("Öğe bu bölüme ait değil: " + id);
            }
            o.setSortOrder(sira++);
            yazilacak.add(o);
        }
        itemRepository.saveAll(yazilacak);
    }

    // ------------------------------------------------------------------
    // Öğe görseli
    // ------------------------------------------------------------------

    /**
     * Öğenin görselini değiştirir.
     *
     * <p>
     * Baytlar ürün görselleriyle <b>aynı depoda</b> ({@code SIMGE_IMAGE_BLOB}):
     * aynı küçültme, aynı içerik adresli tekilleştirme. Öğenin sütununa yazılan
     * şey adres.
     * </p>
     *
     * <p>
     * Eski bağ kaldırılıyor ki bir yuvada tek bağ kalsın; baytlar duruyor.
     * </p>
     *
     * @param dil {@code tr} veya {@code en} — banner'ların iki dilde ayrı
     *            görseli olabiliyor (üzerinde yazı var)
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> gorselYukle(Long ogeId, String dil, byte[] baytlar,
            String dosyaAdi, Long staffId) throws IOException {

        HomeSectionItem oge = itemRepository.findById(ogeId)
                .orElseThrow(() -> new GecersizIstek("Öğe bulunamadı: " + ogeId));
        String d = dilDogrula(dil);

        baglariKaldir(oge, d);
        ImageService.Yukleme sonuc = imageService.yukle(
                ImageLink.OWNER_HOME, gorselAnahtari(oge, d), baytlar, dosyaAdi, staffId, true);

        String adres = "/api/images/" + sonuc.contentHash() + "/detail.jpg";
        if (DIL_EN.equals(d)) {
            oge.setImageEn(adres);
        } else {
            oge.setImageTr(adres);
        }
        itemRepository.save(oge);

        log.info("Vitrin öğesi görseli değişti: öğe={} dil={} hash={}",
                ogeId, d, sonuc.contentHash().substring(0, 12));

        HomeSection b = oge.getSection();
        return ogeDto(oge, urunAdlari(List.of(b)), kategoriler(List.of(b)));
    }

    /** Öğenin görselini kaldırır; baytlar durur. */
    @Transactional(transactionManager = "appTransactionManager")
    public Map<String, Object> gorselKaldir(Long ogeId, String dil) {
        HomeSectionItem oge = itemRepository.findById(ogeId)
                .orElseThrow(() -> new GecersizIstek("Öğe bulunamadı: " + ogeId));
        String d = dilDogrula(dil);

        baglariKaldir(oge, d);
        if (DIL_EN.equals(d)) {
            oge.setImageEn(null);
        } else {
            oge.setImageTr(null);
        }
        itemRepository.save(oge);

        HomeSection b = oge.getSection();
        return ogeDto(oge, urunAdlari(List.of(b)), kategoriler(List.of(b)));
    }

    // ------------------------------------------------------------------
    // İç işler
    // ------------------------------------------------------------------

    private StoreTheme temaBul(String slug) {
        Optional<StoreTheme> tema = (slug == null || slug.isBlank())
                ? themeRepository.findFirstByStatusOrderBySortOrderAscIdAsc(StoreTheme.AKTIF)
                : themeRepository.findBySlug(slug.trim());

        return tema.orElseThrow(() -> new GecersizIstek(slug == null || slug.isBlank()
                ? "Aktif tema yok. Bir temayı aktif yapın."
                : "Tema bulunamadı: " + slug));
    }

    private Map<String, Object> temaDto(StoreTheme t) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", t.getId());
        dto.put("slug", t.getSlug());
        dto.put("ad", t.getName());
        dto.put("aktif", StoreTheme.AKTIF == (t.getStatus() == null ? 0 : t.getStatus()));
        return dto;
    }

    private Map<String, Object> bolumDto(HomeSection b, Map<Long, String> urunAdlari,
            Map<Long, AnaGrup> kategoriler) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", b.getId());
        dto.put("anahtar", b.getSectionKey());
        dto.put("ad", BolumEtiketleri.ad(b.getSectionKey()));
        dto.put("nerede", BolumEtiketleri.nerede(b.getSectionKey()));
        dto.put("tur", b.getSectionType());
        dto.put("aktif", Boolean.TRUE.equals(b.getActive()));
        dto.put("sira", b.getSortOrder());
        dto.put("baslik_tr", b.getTitleTr());
        dto.put("baslik_en", b.getTitleEn());
        dto.put("altbaslik_tr", b.getSubtitleTr());
        dto.put("altbaslik_en", b.getSubtitleEn());
        // Panel hangi alanları göstereceğine buna bakarak karar veriyor;
        // bölüm türünü arayüzde yeniden yorumlamak iki yerde bilgi demekti.
        dto.put("oge_turu", refTuru(b.getSectionType()));
        dto.put("baslik_kullanilir", HomeSection.TYPE_PRODUCT_LIST.equals(b.getSectionType()));

        List<Map<String, Object>> ogeler = new ArrayList<>();
        for (HomeSectionItem o : b.getItems()) {
            ogeler.add(ogeDto(o, urunAdlari, kategoriler));
        }
        dto.put("ogeler", ogeler);
        return dto;
    }

    private Map<String, Object> ogeDto(HomeSectionItem o, Map<Long, String> urunAdlari,
            Map<Long, AnaGrup> kategoriler) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", o.getId());
        dto.put("ref_turu", o.getRefType());
        dto.put("ref_id", o.getRefId());
        dto.put("aktif", Boolean.TRUE.equals(o.getActive()));
        dto.put("sira", o.getSortOrder());
        dto.put("gorsel_tr", o.getImageTr());
        dto.put("gorsel_en", o.getImageEn());
        dto.put("baslik_tr", o.getTitleTr());
        dto.put("baslik_en", o.getTitleEn());
        dto.put("altbaslik_tr", o.getSubtitleTr());
        dto.put("altbaslik_en", o.getSubtitleEn());
        dto.put("etiket_tr", o.getTagTr());
        dto.put("etiket_en", o.getTagEn());
        dto.put("buton_tr", o.getButtonTextTr());
        dto.put("buton_en", o.getButtonTextEn());
        dto.put("bag_turu", o.getLinkType());
        dto.put("bag_degeri", o.getLinkValue());

        // Referansın Mikro'daki karşılığı. null ise referans ERP'den kalkmış
        // demektir; panel bunu "bulunamadı" diye gösteriyor ki operatör sessiz
        // bir boşluk yerine sorunu görsün.
        Long ref = sayiya(o.getRefId());
        if (HomeSectionItem.REF_PRODUCT.equals(o.getRefType())) {
            dto.put("ref_ad", ref == null ? null : urunAdlari.get(ref));
        } else if (HomeSectionItem.REF_CATEGORY.equals(o.getRefType())) {
            AnaGrup g = ref == null ? null : kategoriler.get(ref);
            dto.put("ref_ad", g == null ? null : (g.getIsim() == null ? null : g.getIsim().trim()));
            dto.put("ref_kod", g == null ? null : (g.getKod() == null ? null : g.getKod().trim()));
        }
        return dto;
    }

    /** Bölüm türünden öğe türü. Panel de bunu kullanıyor; tek yerde tanımlı. */
    private String refTuru(String sectionType) {
        return switch (sectionType == null ? "" : sectionType) {
            case HomeSection.TYPE_PRODUCT_LIST -> HomeSectionItem.REF_PRODUCT;
            case HomeSection.TYPE_CATEGORY_LIST -> HomeSectionItem.REF_CATEGORY;
            case HomeSection.TYPE_BANNER, HomeSection.TYPE_BANNER_GROUP -> HomeSectionItem.REF_BANNER;
            case HomeSection.TYPE_SERVICE_LIST -> HomeSectionItem.REF_SERVICE;
            case HomeSection.TYPE_BLOG_LIST -> HomeSectionItem.REF_BLOG;
            case HomeSection.TYPE_BRAND_LIST -> HomeSectionItem.REF_BRAND;
            default -> HomeSectionItem.REF_BANNER;
        };
    }

    private String refDogrula(String refTuru, String refId) {
        Long id = sayiya(refId);
        if (id == null) {
            throw new GecersizIstek("Geçersiz referans: " + refId);
        }
        if (HomeSectionItem.REF_PRODUCT.equals(refTuru)) {
            if (stokRepository.findByRecnoIn(List.of(id)).isEmpty()) {
                throw new GecersizIstek("Mikro'da bu numarayla ürün yok: " + id);
            }
        } else if (anaGrupRepository.findByRecnoIn(List.of(id)).isEmpty()) {
            throw new GecersizIstek("Mikro'da bu numarayla kategori yok: " + id);
        }
        return String.valueOf(id);
    }

    private void alanlariUygula(HomeSectionItem oge, OgeIstegi istek) {
        if (istek.baslikTr() != null) {
            oge.setTitleTr(bosNull(istek.baslikTr()));
        }
        if (istek.baslikEn() != null) {
            oge.setTitleEn(bosNull(istek.baslikEn()));
        }
        if (istek.altBaslikTr() != null) {
            oge.setSubtitleTr(bosNull(istek.altBaslikTr()));
        }
        if (istek.altBaslikEn() != null) {
            oge.setSubtitleEn(bosNull(istek.altBaslikEn()));
        }
        if (istek.etiketTr() != null) {
            oge.setTagTr(bosNull(istek.etiketTr()));
        }
        if (istek.etiketEn() != null) {
            oge.setTagEn(bosNull(istek.etiketEn()));
        }
        if (istek.butonTr() != null) {
            oge.setButtonTextTr(bosNull(istek.butonTr()));
        }
        if (istek.butonEn() != null) {
            oge.setButtonTextEn(bosNull(istek.butonEn()));
        }
        if (istek.bagTuru() != null) {
            oge.setLinkType(bosNull(istek.bagTuru()));
        }
        if (istek.bagDegeri() != null) {
            oge.setLinkValue(bosNull(istek.bagDegeri()));
        }
    }

    private int sonrakiSira(HomeSection b) {
        return itemRepository.findBySectionOrderBySortOrderAscIdAsc(b).stream()
                .map(HomeSectionItem::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private Map<Long, String> urunAdlari(List<HomeSection> bolumler) {
        List<Long> recnolar = refler(bolumler, HomeSectionItem.REF_PRODUCT);
        if (recnolar.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> harita = new HashMap<>();
        for (Stok s : stokRepository.findByRecnoIn(recnolar)) {
            harita.put(s.getRecno(), s.getIsim() == null ? "" : s.getIsim().trim());
        }
        return harita;
    }

    private Map<Long, AnaGrup> kategoriler(List<HomeSection> bolumler) {
        List<Long> recnolar = refler(bolumler, HomeSectionItem.REF_CATEGORY);
        if (recnolar.isEmpty()) {
            return Map.of();
        }
        Map<Long, AnaGrup> harita = new HashMap<>();
        for (AnaGrup g : anaGrupRepository.findByRecnoIn(recnolar)) {
            harita.put(g.getRecno(), g);
        }
        return harita;
    }

    private List<Long> refler(List<HomeSection> bolumler, String refTuru) {
        List<Long> sonuc = new ArrayList<>();
        for (HomeSection b : bolumler) {
            for (HomeSectionItem o : b.getItems()) {
                if (refTuru.equals(o.getRefType())) {
                    Long id = sayiya(o.getRefId());
                    if (id != null && !sonuc.contains(id)) {
                        sonuc.add(id);
                    }
                }
            }
        }
        return sonuc;
    }

    /** Bir öğe-dil yuvasının görsel bağ anahtarı. */
    private String gorselAnahtari(HomeSectionItem oge, String dil) {
        return oge.getId() + ":" + dil;
    }

    private void baglariKaldir(HomeSectionItem oge, String dil) {
        String anahtar = gorselAnahtari(oge, dil);
        for (ImageLink bag : imageService.listele(ImageLink.OWNER_HOME, anahtar)) {
            imageService.bagiKaldir(ImageLink.OWNER_HOME, anahtar, bag.getId());
        }
    }

    private String dilDogrula(String dil) {
        String d = dil == null ? DIL_TR : dil.trim().toLowerCase(Locale.ROOT);
        if (!DIL_TR.equals(d) && !DIL_EN.equals(d)) {
            throw new GecersizIstek("Dil 'tr' veya 'en' olmalı: " + dil);
        }
        return d;
    }

    private static Long sayiya(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String bosNull(String s) {
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
