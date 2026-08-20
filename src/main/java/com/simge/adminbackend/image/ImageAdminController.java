package com.simge.adminbackend.image;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.simge.adminbackend.appdb.model.ImageLink;
import com.simge.adminbackend.common.TurkishSearch;
import com.simge.adminbackend.erp.model.Stok;
import com.simge.adminbackend.erp.repository.StokAramaRepository;
import com.simge.adminbackend.erp.repository.StokRepository;
import com.simge.adminbackend.staff.StaffPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Ürün ve kategori görsellerinin yönetimi (ADR D-142).
 *
 * <p>
 * Mikro görsel tutmuyor; görseller {@code SIMGE_OS_APP}'te bizim. Bu yüzden bu
 * uçlar ERP'ye <b>yazmıyor</b> — yalnızca ürün aramak için okuyorlar.
 * </p>
 *
 * <p>
 * {@code ICERIK} rolü de erişebiliyor: görsel yüklemek sistem yönetimi değil,
 * içerik işi. Cari açma ya da başvuru onaylama yetkisi gerekmiyor.
 * </p>
 */
@Tag(name = "Görseller", description = "Ürün ve kategori görsellerinin yüklenmesi ve düzenlenmesi.")
@RestController
@RequestMapping("/api/images")
@PreAuthorize("hasAnyRole('ADMIN','ICERIK')")
public class ImageAdminController {

    /** Arama sonucu tavanı; panel açılır listede gösteriyor. */
    private static final int ARAMA_SINIRI = 40;

    /** Tek seferde kabul edilen dosya sayısı. */
    private static final int TOPLU_SINIR = 200;

    private final ImageService imageService;
    private final StokRepository stokRepository;
    /** Çok kelimeli arama (D-151); koşul sayısı girdiye bağlı olduğu için ayrı. */
    private final StokAramaRepository stokArama;

    public ImageAdminController(ImageService imageService, StokRepository stokRepository,
            StokAramaRepository stokArama) {
        this.imageService = imageService;
        this.stokRepository = stokRepository;
        this.stokArama = stokArama;
    }

    // --- Ürün arama -------------------------------------------------------

    @Operation(summary = "Ürün ara",
            description = """
                    Kod veya isimde arar; Türkçe karakter farkını yok sayar.
                    Her sonuçta `gorsel_var` alanı dönüyor — operatör hangilerinin
                    eksik olduğunu listeyi gezmeden görsün.""")
    @GetMapping("/products/search")
    public ResponseEntity<Map<String, Object>> urunAra(@RequestParam("q") String q) {
        List<String> kelimeler = TurkishSearch.tokenize(q);
        if (kelimeler.isEmpty()) {
            return ResponseEntity.ok(Map.of("data", List.of()));
        }

        // TÜM kelimeler AND'leniyor (D-151).
        //
        // Öncesinde yalnızca ilk kelime kullanılıyordu ("bu bir katalog araması
        // değil" gerekçesiyle). Operatörün gerçekte yaptığı şey ürünün tam adını
        // yapıştırmak: "TOZ ŞEKER 50KG" sorguya "TOZ" olarak gidiyor, toz
        // biberden toz deterjana kadar her şey dönüyor ve aranan ürün sonuç
        // sınırının dışında kalabiliyordu.
        List<String> desenler = kelimeler.stream()
                .map(TurkishSearch::containsPattern)
                .toList();

        List<Stok> bulunan = stokArama.ara(desenler,
                TurkishSearch.startsWithPattern(kelimeler.get(0)), ARAMA_SINIRI);

        List<String> kodlar = bulunan.stream().map(Stok::getKod).toList();
        Set<String> gorselli = new HashSet<>(
                imageService.gorseliOlanlar(ImageLink.OWNER_PRODUCT, kodlar));

        List<Map<String, Object>> data = new ArrayList<>(bulunan.size());
        for (Stok s : bulunan) {
            Map<String, Object> satir = new LinkedHashMap<>();
            satir.put("sto_kod", s.getKod());
            satir.put("isim", s.getIsim());
            // Vitrin yönetimi ürünleri sto_RECno ile referanslıyor (D-154);
            // görsel yükleme ise sto_kod ile. Aynı arama iki ekranı besliyor,
            // bu yüzden ikisi de dönüyor.
            satir.put("recno", s.getRecno());
            satir.put("gorsel_var", gorselli.contains(s.getKod() == null ? "" : s.getKod().trim()));
            data.add(satir);
        }
        return ResponseEntity.ok(Map.of("data", data));
    }

    // --- Listeleme --------------------------------------------------------

    @Operation(summary = "Bir sahibin görselleri",
            description = "`tur` = product | category. Sıra 0 birincil görseldir.")
    @GetMapping("/{tur}/{key}")
    public ResponseEntity<Map<String, Object>> listele(@PathVariable("tur") String tur,
            @PathVariable("key") String key) {
        String ownerType = ownerType(tur);
        if (ownerType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gecersiz_tur"));
        }
        return ResponseEntity.ok(Map.of("data", cevir(imageService.listele(ownerType, key))));
    }

    // --- Tekli yükleme ----------------------------------------------------

    @Operation(summary = "Görsel yükle",
            description = """
                    Dosya küçültülür ve iki türev saklanır (600 / 1200 piksel);
                    **ham dosya saklanmaz**. En fazla 10 MB — bu bir kabul kriteri
                    değil, kötüye kullanım koruması.

                    Yanıt kodları:
                    - **201** yüklendi
                    - **400** dosya görsel değil / çok büyük / ürün bulunamadı
                    - **413** dosya sunucu sınırını aştı""")
    @PostMapping(value = "/{tur}/{key}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> yukle(@PathVariable("tur") String tur,
            @PathVariable("key") String key,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "birincil", defaultValue = "false") boolean birincil,
            Authentication authentication) throws IOException {

        String ownerType = ownerType(tur);
        if (ownerType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gecersiz_tur"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "bos_dosya"));
        }

        // Ürün gerçekten var mı: olmayan bir SKU'ya görsel bağlamak sessizce
        // kaybolan bir kayıt üretirdi.
        if (ImageLink.OWNER_PRODUCT.equals(ownerType)
                && stokRepository.findFirstByKod(key.trim()).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "urun_bulunamadi"));
        }

        try {
            ImageService.Yukleme sonuc = imageService.yukle(ownerType, key,
                    file.getBytes(), file.getOriginalFilename(), staffId(authentication), birincil);
            return ResponseEntity.status(HttpStatus.CREATED).body(cevir(sonuc));

        } catch (ImageProcessor.GecersizGorselException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --- Toplu yükleme ----------------------------------------------------

    @Operation(summary = "Toplu görsel yükle",
            description = """
                    Dosya **adından** SKU eşleştirir: `ABC123.jpg` -> `ABC123`.
                    `ABC123-2.jpg` ve `ABC123 (2).jpg` gibi sondaki sıra ekleri
                    atılır, yani aynı ürüne birden fazla fotoğraf yüklenebilir.

                    8.238 ürünlük katalogda tek tek yükleme yapılamayacağı için var.
                    Eşleşmeyen dosyalar **atlanır ve raporlanır**; kısmi başarı
                    normal kabul edilir, tamamı geri alınmaz.""")
    @PostMapping(value = "/products/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> topluYukle(
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "dosya_yok"));
        }
        if (files.size() > TOPLU_SINIR) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "cok_fazla_dosya", "sinir", TOPLU_SINIR));
        }

        // Tüm SKU'ları TEK sorguda çözüyoruz. Dosya başına sorgu atmak 200
        // dosyada 200 gidiş-dönüş demekti.
        Map<String, String> adayKodlar = new LinkedHashMap<>();
        for (MultipartFile f : files) {
            adayKodlar.put(f.getOriginalFilename(),
                    ImageService.dosyaAdindanKod(f.getOriginalFilename()));
        }

        List<Stok> stoklar = stokRepository.findByKodIn(
                adayKodlar.values().stream().filter(k -> !k.isBlank()).distinct().toList());

        // Eşleşme büyük/küçük harf duyarsız: operatörün dosya adı Mikro'daki
        // kodun harf düzenini birebir tutturmak zorunda kalmasın.
        Map<String, String> kodEslesme = new HashMap<>();
        for (Stok s : stoklar) {
            kodEslesme.put(ImageService.eslesmeAnahtari(s.getKod()), s.getKod().trim());
        }

        Long staffId = staffId(authentication);
        List<Map<String, Object>> basarili = new ArrayList<>();
        List<Map<String, Object>> atlanan = new ArrayList<>();

        for (MultipartFile f : files) {
            String dosyaAdi = f.getOriginalFilename();
            String aday = adayKodlar.get(dosyaAdi);
            String gercekKod = kodEslesme.get(ImageService.eslesmeAnahtari(aday));

            if (gercekKod == null) {
                atlanan.add(Map.of("dosya", String.valueOf(dosyaAdi),
                        "sebep", "urun_bulunamadi", "aranan_kod", aday));
                continue;
            }

            try {
                ImageService.Yukleme sonuc = imageService.yukle(ImageLink.OWNER_PRODUCT,
                        gercekKod, f.getBytes(), dosyaAdi, staffId, false);
                Map<String, Object> satir = cevir(sonuc);
                satir.put("dosya", dosyaAdi);
                basarili.add(satir);

            } catch (ImageProcessor.GecersizGorselException e) {
                atlanan.add(Map.of("dosya", String.valueOf(dosyaAdi), "sebep", e.getMessage()));
            } catch (IOException e) {
                // Tek bir bozuk dosya tüm partiyi düşürmesin.
                atlanan.add(Map.of("dosya", String.valueOf(dosyaAdi), "sebep", "okunamadi"));
            }
        }

        Map<String, Object> cevap = new LinkedHashMap<>();
        cevap.put("yuklenen", basarili.size());
        cevap.put("atlanan", atlanan.size());
        cevap.put("data", basarili);
        cevap.put("hatalar", atlanan);
        return ResponseEntity.ok(cevap);
    }

    // --- Düzenleme --------------------------------------------------------

    @Operation(summary = "Birincil görseli belirle",
            description = "Karoda ve listede gösterilecek görsel. Diğerleri yeniden sıralanır.")
    @PutMapping("/{tur}/{key}/primary/{linkId}")
    public ResponseEntity<Map<String, Object>> birincilYap(@PathVariable("tur") String tur,
            @PathVariable("key") String key, @PathVariable("linkId") Long linkId) {

        String ownerType = ownerType(tur);
        if (ownerType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gecersiz_tur"));
        }
        if (!imageService.birincilYap(ownerType, key, linkId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "bulunamadi"));
        }
        return ResponseEntity.ok(Map.of("data", cevir(imageService.listele(ownerType, key))));
    }

    @Operation(summary = "Görseli kaldır",
            description = """
                    Bağı siler. **Baytlar silinmez** — aynı görsel başka bir ürüne de
                    bağlı olabilir. Öksüz baytların temizliği ayrı bir bakım işi.""")
    @DeleteMapping("/{tur}/{key}/{linkId}")
    public ResponseEntity<Map<String, Object>> kaldir(@PathVariable("tur") String tur,
            @PathVariable("key") String key, @PathVariable("linkId") Long linkId) {

        String ownerType = ownerType(tur);
        if (ownerType == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "gecersiz_tur"));
        }
        if (!imageService.bagiKaldir(ownerType, key, linkId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "bulunamadi"));
        }
        return ResponseEntity.ok(Map.of("data", cevir(imageService.listele(ownerType, key))));
    }

    @Operation(summary = "Özet", description = "Kaç ürün/kategori görseli var, kaç ayrı bayt saklanıyor.")
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> ozet() {
        return ResponseEntity.ok(Map.of("data", imageService.ozet()));
    }

    // --- iç işler ---------------------------------------------------------

    private String ownerType(String tur) {
        return switch (tur == null ? "" : tur) {
            case "product", "products" -> ImageLink.OWNER_PRODUCT;
            case "category", "categories" -> ImageLink.OWNER_CATEGORY;
            default -> null;
        };
    }

    private Long staffId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            return null;
        }
        return principal.getId();
    }

    private List<Map<String, Object>> cevir(List<ImageLink> linkler) {
        List<Map<String, Object>> data = new ArrayList<>(linkler.size());
        for (ImageLink l : linkler) {
            Map<String, Object> satir = new LinkedHashMap<>();
            satir.put("id", l.getId());
            satir.put("content_hash", l.getContentHash());
            satir.put("sort_order", l.getSortOrder());
            satir.put("birincil", l.getSortOrder() == ImageLink.BIRINCIL);
            satir.put("source_name", l.getSourceName());
            satir.put("source_bytes", l.getSourceBytes());
            data.add(satir);
        }
        return data;
    }

    private Map<String, Object> cevir(ImageService.Yukleme y) {
        Map<String, Object> satir = new LinkedHashMap<>();
        satir.put("sto_kod", y.ownerKey());
        satir.put("content_hash", y.contentHash());
        satir.put("sort_order", y.sortOrder());
        satir.put("source_bytes", y.sourceBytes());
        satir.put("stored_bytes", y.storedBytes());
        satir.put("yeni_bayt", y.yeniBayt());
        return satir;
    }
}
