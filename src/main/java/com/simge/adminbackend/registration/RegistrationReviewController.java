package com.simge.adminbackend.registration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simge.adminbackend.appdb.model.RegistrationRequest;
import com.simge.adminbackend.erp.CariLookupService;
import com.simge.adminbackend.erp.model.CariHesap;
import com.simge.adminbackend.staff.StaffPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Kayıt başvurularının yönetimi (ADR D-124).
 *
 * <p>
 * Vitrin backend'inden buraya taşındı: onay akışı Mikro'dan cari okuyor, davet
 * üretiyor ve ileride yeni cari açma talebine dönüşecek — hepsi intranette
 * kalması gereken işler. Vitrindeki {@code /api/admin/**} yüzeyi kaldırıldı.
 * </p>
 *
 * <p>
 * {@code SATIS} rolü de görebiliyor: başvuruyu değerlendiren kişi çoğu zaman
 * müşteriyi tanıyan satışçı, sistemi yöneten kişi değil.
 * </p>
 */
@Tag(name = "Kayıt başvuruları",
        description = "Cari e-postası olmayan ya da hiç cari kaydı bulunmayan başvuruların incelenmesi.")
@RestController
@RequestMapping("/api/registration-requests")
@PreAuthorize("hasAnyRole('ADMIN','SATIS')")
public class RegistrationReviewController {

    private final RegistrationReviewService service;
    private final CariLookupService cariLookup;

    public RegistrationReviewController(RegistrationReviewService service,
            CariLookupService cariLookup) {
        this.service = service;
        this.cariLookup = cariLookup;
    }

    public record ApproveRequest(
            @Size(max = 25) String cari_kod,
            @Size(max = 500) String note) {
    }

    public record RejectRequest(@Size(max = 500) String note) {
    }

    @Operation(summary = "Başvuru listesi",
            description = """
                    Duruma göre başvurular. Varsayılan `PENDING`.

                    Bu kuyruk istisna değil ana yol: 2.440 aktif carinin yalnızca 252'sinde
                    e-posta adresi var, kalanı kendi kendine kayıt olamıyor.""")
    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(name = "status", defaultValue = RegistrationRequest.STATUS_PENDING)
            String status) {

        List<Map<String, Object>> data = new ArrayList<>();
        for (RegistrationRequest request : service.byStatus(status)) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", request.getId());
            dto.put("vergi_no", request.getVergiNo());
            dto.put("email", request.getEmail());
            dto.put("full_name", request.getFullName());
            dto.put("phone", request.getPhone());
            dto.put("company_name", request.getCompanyName());
            dto.put("branch", request.getBranch());
            dto.put("matched_cari_kod", request.getMatchedCariKod());
            dto.put("status", request.getStatus());
            dto.put("review_note", request.getReviewNote());
            dto.put("created_at", request.getCreatedAt());
            data.add(dto);
        }

        return ResponseEntity.ok(Map.of("data", data, "total", data.size()));
    }

    @Operation(summary = "Bekleyen başvuru sayısı",
            description = "Panelin kenar çubuğundaki rozet için; liste yükü olmadan.")
    @GetMapping("/pending-count")
    public ResponseEntity<Map<String, Object>> pendingCount() {
        return ResponseEntity.ok(Map.of("count", service.pendingCount()));
    }

    @Operation(summary = "Cari ara",
            description = """
                    Kod, unvan ya da vergi numarasıyla arama. Onaylarken doğru cariyi
                    seçmek için; aynı vergi numarasına birden çok şube kaydı düşebiliyor.

                    Salt okunur. En fazla 20 sonuç döner; 2 karakterden kısa sorgu boş döner.""")
    @GetMapping("/cari-search")
    public ResponseEntity<Map<String, Object>> searchCari(@RequestParam("q") String q) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (CariHesap cari : cariLookup.search(q)) {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("cari_kod", cari.getCariKod());
            dto.put("unvan", cari.getCariUnvan1());
            dto.put("vergi_no", cari.getVergiDairesiNo());
            dto.put("vergi_dairesi", cari.getVergiDairesiAdi());
            dto.put("email", cari.getEmail());
            data.add(dto);
        }
        return ResponseEntity.ok(Map.of("data", data, "total", data.size()));
    }

    @Operation(summary = "Başvuruyu onayla",
            description = """
                    Başvuranı verilen cariye bağlar ve **hesap kurma bağlantısı** gönderir.
                    Hesap doğrudan açılmaz: parolayı başvuran kendisi belirler, personel
                    hiçbir zaman müşteri parolası bilmez.

                    Cari kodunun Mikro'da var olduğu doğrulanır.

                    Yanıt kodları:
                    - **200** onaylandı, davet gönderildi
                    - **404** başvuru yok
                    - **409** başvuru zaten incelenmiş, ya da bu adresin hesabı var
                    - **422** cari kodu Mikro'da bulunamadı
                    - **503** e-posta gönderimi yapılandırılmamış""")
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(Authentication authentication,
            @PathVariable("id") Long id, @Valid @RequestBody ApproveRequest body) {

        StaffPrincipal staff = (StaffPrincipal) authentication.getPrincipal();

        try {
            RegistrationRequest request = service.approve(staff, id, body.cari_kod(), body.note());
            return ResponseEntity.ok(Map.of(
                    "id", request.getId(),
                    "status", request.getStatus(),
                    "cari_kod", request.getMatchedCariKod()));

        } catch (RegistrationReviewService.RequestNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));

        } catch (RegistrationReviewService.AlreadyReviewedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "already_reviewed"));

        } catch (RegistrationReviewService.CariNotFoundException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of("error", "cari_not_found"));

        } catch (CompanyInviteService.EmailTakenException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "email_taken"));

        } catch (CompanyInviteService.MailUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "mail_unavailable"));
        }
    }

    @Operation(summary = "Başvuruyu reddet",
            description = """
                    Başvuruyu reddeder. **Otomatik bildirim gönderilmez** — ret sebepleri
                    çoğu zaman konuşarak çözülecek şeyler; kalıp bir ret e-postası bunu
                    kapatır. Not alanı arayan kişiye ne söyleneceğini hatırlamak için.""")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(Authentication authentication,
            @PathVariable("id") Long id, @Valid @RequestBody RejectRequest body) {

        StaffPrincipal staff = (StaffPrincipal) authentication.getPrincipal();

        try {
            RegistrationRequest request = service.reject(staff, id, body.note());
            return ResponseEntity.ok(Map.of("id", request.getId(), "status", request.getStatus()));

        } catch (RegistrationReviewService.RequestNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));

        } catch (RegistrationReviewService.AlreadyReviewedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "already_reviewed"));
        }
    }
}
