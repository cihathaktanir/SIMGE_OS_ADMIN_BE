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
import com.simge.adminbackend.erp.CariKodUretici;
import com.simge.adminbackend.erp.CariLookupService;
import com.simge.adminbackend.erp.CariWriter;
import com.simge.adminbackend.erp.model.CariHesap;
import com.simge.adminbackend.staff.StaffPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    private final CariKodUretici kodUretici;

    public RegistrationReviewController(RegistrationReviewService service,
            CariLookupService cariLookup,
            CariKodUretici kodUretici) {
        this.service = service;
        this.cariLookup = cariLookup;
        this.kodUretici = kodUretici;
    }

    /**
     * @param erp_eposta doluysa carinin <b>boş</b> e-posta alanına yazılır
     *        (D-127). Boş bırakmak "ERP'ye dokunma, sadece hesabı bağla"
     *        demektir. Dolu bir adresin üzerine hiçbir durumda yazılmaz.
     */
    public record ApproveRequest(
            @Size(max = 25) String cari_kod,
            @Email @Size(max = 190) String erp_eposta,
            @Size(max = 500) String note) {
    }

    public record RejectRequest(@Size(max = 500) String note) {
    }

    /**
     * Mikro'da açılacak carinin alanları.
     *
     * <p>
     * Çoğu başvuru formundan geliyor ve panelde önceden dolu geliyor; personel
     * düzeltip onaylıyor. İki alan bilinçli olarak <b>istemciden</b> alınıyor:
     * </p>
     * <ul>
     *   <li>{@code cari_kod} — hangi seriye açılacağı iş kararı; sistem yalnızca
     *       öneriyor (bkz. {@code /cari-kod-oner}).</li>
     *   <li>{@code efatura_mukellefi} — VKN'ye bağlı bir mükellefiyet, veriden
     *       tahmin edilmiyor. Mevcut carilerde dağılım %59/%41; varsayılanı
     *       yanlış koymak fatura kesilemez hâle getirirdi.</li>
     * </ul>
     */
    public record YeniCariRequest(
            @NotBlank @Size(max = 25) String cari_kod,
            @NotBlank @Size(max = 200) String unvan,
            @NotBlank @Pattern(regexp = "[0-9]{10,11}") String vergi_no,
            @Size(max = 100) String vergi_dairesi,
            @Email @Size(max = 190) String eposta,
            boolean efatura_mukellefi,
            @Size(max = 100) String adres,
            @Size(max = 100) String mahalle,
            @Size(max = 50) String ilce,
            @Size(max = 50) String il,
            @Size(max = 50) String ulke,
            @Size(max = 10) String posta_kodu,
            @Size(max = 50) String telefon,
            @Size(max = 500) String note) {
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
            // NO_CARI dalında cari açmak için toplanan alanlar (D-127); panel
            // formu bunlarla önceden dolar, personel düzeltip onaylar.
            dto.put("vergi_dairesi", request.getVergiDairesi());
            dto.put("address", request.getAddress());
            dto.put("district", request.getDistrict());
            dto.put("city", request.getCity());
            dto.put("branch", request.getBranch());
            dto.put("matched_cari_kod", request.getMatchedCariKod());
            dto.put("created_cari_kod", request.getCreatedCariKod());
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
            RegistrationRequest request = service.approve(staff, id, body.cari_kod(),
                    body.erp_eposta(), body.note());
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

        } catch (RegistrationReviewService.EpostaYazilamadi e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "error", "erp_eposta_yazilamadi", "cari_kod", e.getCariKod()));

        } catch (CompanyInviteService.EmailTakenException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "email_taken"));

        } catch (CompanyInviteService.MailUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "mail_unavailable"));
        }
    }

    @Operation(summary = "Cari kodu öner",
            description = """
                    Verilen önekteki bir sonraki boş cari kodu. Yalnızca **öneri**:
                    hangi seriye açılacağı iş kararı, kodu personel onaylıyor.

                    Öneri bir rezervasyon değil; kodu asıl garantiye alan şey Mikro'daki
                    benzersiz indeks.""")
    @GetMapping("/cari-kod-oner")
    public ResponseEntity<Map<String, Object>> cariKodOner(
            @RequestParam(name = "onek", required = false) String onek) {
        return ResponseEntity.ok(Map.of(
                "onek", onek == null || onek.isBlank() ? kodUretici.varsayilanOnek() : onek,
                "cari_kod", kodUretici.oner(onek)));
    }

    @Operation(summary = "Mikro'da cari AÇARAK onayla",
            description = """
                    `NO_CARI` dalı için. Mikro'da **yeni cari açar**, başvuranı ona bağlar
                    ve hesap kurma bağlantısı gönderir. ERP'ye yazan iki uçtan biri (D-127).

                    Açılan satır, mevcut carilerden ölçülmüş şablonla yazılır: 182 sütunun
                    tamamı doldurulur, hiçbiri NULL kalmaz. Ana adres satırı da aynı
                    işlemde yazılır.

                    Yanıt kodları:
                    - **200** cari açıldı, başvuru onaylandı, davet gönderildi
                    - **404** başvuru yok
                    - **409** başvuru zaten incelenmiş / bu adresin hesabı var /
                      **bu vergi numarasıyla cari zaten var** (`cari_zaten_var`) /
                      cari kodu kullanımda (`cari_kodu_kullanimda`)
                    - **503** e-posta gönderimi yapılandırılmamış""")
    @PostMapping("/{id}/cari-ac")
    public ResponseEntity<Map<String, Object>> cariAc(Authentication authentication,
            @PathVariable("id") Long id, @Valid @RequestBody YeniCariRequest body) {

        StaffPrincipal staff = (StaffPrincipal) authentication.getPrincipal();

        CariWriter.YeniCari veri = new CariWriter.YeniCari(
                body.cari_kod().trim(), body.unvan().trim(), body.vergi_dairesi(),
                body.vergi_no(), body.eposta(), body.efatura_mukellefi(),
                body.adres(), body.mahalle(), body.ilce(), body.il(),
                body.ulke(), body.posta_kodu(), body.telefon());

        try {
            RegistrationRequest request = service.yeniCariAcarakOnayla(staff, id, veri, body.note());
            return ResponseEntity.ok(Map.of(
                    "id", request.getId(),
                    "status", request.getStatus(),
                    "cari_kod", request.getCreatedCariKod()));

        } catch (RegistrationReviewService.RequestNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));

        } catch (RegistrationReviewService.AlreadyReviewedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "already_reviewed"));

        } catch (RegistrationReviewService.CariZatenVar e) {
            // Personel "var olan cariye bağla" yoluna yönlendirilsin diye kodlar
            // da dönüyor; yoksa Mikro'da elle aramak zorunda kalırdı.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "cari_zaten_var", "cari_kodlari", e.getKodlar()));

        } catch (CariWriter.CariKoduKullanimda e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "cari_kodu_kullanimda", "cari_kod", e.getCariKod()));

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
