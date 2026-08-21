package com.simge.adminbackend.pages;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 * Düz sayfaların içeriği (ADR D-172).
 *
 * <p>
 * {@code ICERIK} rolü de erişebiliyor: Hakkımızda metnini yazmak içerik işi,
 * sistem işi değil. Vitrin ana sayfası ({@code /api/storefront}) ile aynı
 * yetki sınırı.
 * </p>
 */
@Tag(name = "Sayfa içeriği",
        description = "Hakkımızda gibi düz sayfaların iki dilli metni ve görselleri.")
@RestController
@RequestMapping("/api/pages")
@PreAuthorize("hasAnyRole('ADMIN','ICERIK')")
public class PageContentAdminController {

    private final PageContentAdminService service;

    public PageContentAdminController(PageContentAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Sayfanın blokları",
            description = """
                    Bir sayfanın tüm blokları, **iki dil bir arada** ve pasif
                    olanlar dahil.

                    Vitrin ucu (`SIMGE_OS_BE /api/pages/{key}`) tek dile
                    çözülmüş halini veriyor; burada iki dil yan yana çünkü
                    operatörün denetlemesi gereken şey tam olarak aradaki fark.

                    `sayfa` verilmezse `about`.""")
    @GetMapping
    public ResponseEntity<Map<String, Object>> sayfa(
            @RequestParam(name = "sayfa", required = false) String sayfa) {
        return ResponseEntity.ok(service.sayfa(sayfa));
    }

    @Operation(summary = "Bloğu güncelle",
            description = """
                    Başlık, metin ve açık/kapalı. **Null bırakılan alan
                    değişmez**; boş dize göndermek o alanı temizler.

                    Bloklar eklenip silinmiyor: her blok anahtarının karşılığı
                    vitrinde elle yazılmış bir yuva.

                    Açık bir bloğun Türkçe içeriği tamamen boşaltılamaz —
                    Türkçe hem varsayılan dil hem de İngilizcenin yedeği.""")
    @PutMapping("/blocks/{id}")
    public ResponseEntity<Map<String, Object>> blokGuncelle(@PathVariable("id") Long id,
            @RequestBody PageContentAdminService.BlokYama istek,
            Authentication authentication) {
        return ResponseEntity.ok(
                Map.of("data", service.blokGuncelle(id, istek, staffId(authentication))));
    }

    @Operation(summary = "Blok görseli yükle",
            description = """
                    Tanıtım fotoğrafı ve hizmet ikonları için. Dosya küçültülür
                    ve ürün görselleriyle **aynı depoda** saklanır; ham dosya
                    saklanmaz.

                    Ana sayfa banner'larının aksine **dil parametresi yok**: bu
                    görsellerin üzerinde yazı olmadığı için aynı fotoğraf iki
                    dilde de doğru.""")
    @PostMapping(value = "/blocks/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> gorselYukle(@PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "bos_dosya"));
        }
        try {
            return ResponseEntity.ok(Map.of("data", service.gorselYukle(
                    id, file.getBytes(), file.getOriginalFilename(), staffId(authentication))));

        } catch (ImageProcessor.GecersizGorselException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Blok görselini kaldır",
            description = "Baytlar durur, yalnızca bağ ve adres silinir.")
    @DeleteMapping("/blocks/{id}/image")
    public ResponseEntity<Map<String, Object>> gorselKaldir(@PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(
                Map.of("data", service.gorselKaldir(id, staffId(authentication))));
    }

    /**
     * Reddedilen istekler.
     *
     * <p>
     * Mesaj operatöre olduğu gibi gösteriliyor: "geçersiz istek" demek yerine
     * neyin neden kabul edilmediğini söylüyor.
     * </p>
     */
    @ExceptionHandler(PageContentAdminService.GecersizIstek.class)
    public ResponseEntity<Map<String, Object>> gecersiz(PageContentAdminService.GecersizIstek e) {
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
