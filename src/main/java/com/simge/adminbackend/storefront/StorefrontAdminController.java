package com.simge.adminbackend.storefront;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.simge.adminbackend.image.ImageProcessor;
import com.simge.adminbackend.staff.StaffPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Vitrin ana sayfasının yönetimi (ADR D-154).
 *
 * <p>
 * {@code ICERIK} rolü de erişebiliyor: ana sayfada hangi banner'ın ya da hangi
 * ürünün görüneceğine karar vermek içerik işi. Depo ayarı buna dahil
 * <b>değil</b> — o {@code /api/settings} altında ve yalnızca ADMIN.
 * </p>
 */
@Tag(name = "Vitrin yönetimi",
        description = "Ana sayfa bölümleri, banner'lar, kategori ve ürün seçimleri.")
@RestController
@RequestMapping("/api/storefront")
@PreAuthorize("hasAnyRole('ADMIN','ICERIK')")
public class StorefrontAdminController {

    private final StorefrontAdminService service;

    public StorefrontAdminController(StorefrontAdminService service) {
        this.service = service;
    }

    // --- Okuma ------------------------------------------------------------

    @Operation(summary = "Ana sayfa yapılandırması",
            description = """
                    Aktif temanın tüm bölümleri, öğeleriyle. Ürün ve kategori
                    referansları Mikro'dan **isimle** çözülür; `ref_ad` boş
                    geliyorsa o referans ERP'den kalkmış demektir.

                    `tema` parametresi verilmezse aktif tema kullanılır.""")
    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> anaSayfa(
            @RequestParam(name = "tema", required = false) String tema) {
        return ResponseEntity.ok(service.anaSayfa(tema));
    }

    @Operation(summary = "Kategoriler",
            description = "Mikro kategorileri; `gorsel_var` hangilerine görsel yüklendiğini gösterir.")
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> kategoriler() {
        return ResponseEntity.ok(Map.of("data", service.kategoriListesi()));
    }

    // --- Tema -------------------------------------------------------------

    @Operation(summary = "Temayı aktif yap",
            description = "Diğer temalar aynı işlemde pasife çekilir; iki aktif tema olamaz.")
    @PutMapping("/themes/{slug}/active")
    public ResponseEntity<Map<String, Object>> temaAktifle(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(Map.of("data", service.temaAktifle(slug)));
    }

    // --- Bölüm ------------------------------------------------------------

    @Operation(summary = "Bölümü güncelle",
            description = """
                    Başlık, sıra ve açık/kapalı. **Bölüm eklenip silinmiyor**:
                    her bölüm anahtarı vitrin şablonundaki bir yuvanın adı.""")
    @PutMapping("/sections/{id}")
    public ResponseEntity<Map<String, Object>> bolumGuncelle(@PathVariable("id") Long id,
            @RequestBody StorefrontAdminService.BolumGuncelle istek) {
        return ResponseEntity.ok(Map.of("data", service.bolumGuncelle(id, istek)));
    }

    // --- Öğe --------------------------------------------------------------

    @Operation(summary = "Bölüme öğe ekle",
            description = """
                    Öğenin türü **bölümden** belirlenir. Ürün ve kategori
                    referansları Mikro'da doğrulanır: olmayan bir numara
                    kaydedilmez.""")
    @PostMapping("/sections/{id}/items")
    public ResponseEntity<Map<String, Object>> ogeEkle(@PathVariable("id") Long id,
            @RequestBody StorefrontAdminService.OgeIstegi istek) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("data", service.ogeEkle(id, istek)));
    }

    @Operation(summary = "Öğeyi güncelle", description = "Null bırakılan alan değişmez.")
    @PutMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> ogeGuncelle(@PathVariable("id") Long id,
            @RequestBody StorefrontAdminService.OgeIstegi istek) {
        return ResponseEntity.ok(Map.of("data", service.ogeGuncelle(id, istek)));
    }

    @Operation(summary = "Öğeyi sil",
            description = "Görselin **baytları silinmez**: aynı görsel başka bir öğeye de bağlı olabilir.")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> ogeSil(@PathVariable("id") Long id) {
        service.ogeSil(id);
        return ResponseEntity.ok(Map.of("silindi", true));
    }

    /** Sıralama isteği: öğe kimlikleri gösterilecek sırada. */
    public record SiralamaIstegi(List<Long> ogeler) {
    }

    @Operation(summary = "Öğeleri sırala",
            description = "Tüm liste tek istekte yeniden numaralanır (sürükle-bırak sonrası).")
    @PutMapping("/sections/{id}/order")
    public ResponseEntity<Map<String, Object>> sirala(@PathVariable("id") Long id,
            @RequestBody SiralamaIstegi istek) {
        service.ogeleriSirala(id, istek.ogeler() == null ? List.of() : istek.ogeler());
        return ResponseEntity.ok(Map.of("data", service.anaSayfa(null)));
    }

    // --- Öğe görseli ------------------------------------------------------

    @Operation(summary = "Öğe görseli yükle",
            description = """
                    Banner ve hizmet ikonları için. Dosya küçültülür ve ürün
                    görselleriyle **aynı depoda** saklanır; ham dosya saklanmaz.

                    `dil` = tr | en — banner'ların üzerinde yazı olduğu için iki
                    dilde ayrı görsel olabiliyor.""")
    @PostMapping(value = "/items/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> gorselYukle(@PathVariable("id") Long id,
            @RequestParam(value = "dil", defaultValue = "tr") String dil,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "bos_dosya"));
        }
        try {
            return ResponseEntity.ok(Map.of("data", service.gorselYukle(
                    id, dil, file.getBytes(), file.getOriginalFilename(), staffId(authentication))));

        } catch (ImageProcessor.GecersizGorselException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Öğe görselini kaldır", description = "Baytlar durur, yalnızca bağ ve adres silinir.")
    @DeleteMapping("/items/{id}/image")
    public ResponseEntity<Map<String, Object>> gorselKaldir(@PathVariable("id") Long id,
            @RequestParam(value = "dil", defaultValue = "tr") String dil) {
        return ResponseEntity.ok(Map.of("data", service.gorselKaldir(id, dil)));
    }

    // --- Hata ------------------------------------------------------------

    /**
     * Reddedilen istekler.
     *
     * <p>
     * Mesaj operatöre olduğu gibi gösteriliyor: "geçersiz istek" demek yerine
     * hangi referansın neden kabul edilmediğini söylüyor.
     * </p>
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            StorefrontAdminService.GecersizIstek.class)
    public ResponseEntity<Map<String, Object>> gecersiz(StorefrontAdminService.GecersizIstek e) {
        return ResponseEntity.badRequest().body(Map.of("error", "gecersiz", "mesaj", e.getMessage()));
    }

    private Long staffId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            return null;
        }
        return principal.getId();
    }
}
