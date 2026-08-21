package com.simge.adminbackend.cari;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.simge.adminbackend.erp.CariWriter;
import com.simge.adminbackend.staff.StaffPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cari güncelleme kuyruğu (ADR D-173).
 *
 * <p>
 * <b>Yalnızca ADMIN ve SATIS.</b> ICERIK rolü burada yok: bu ekran vitrin
 * içeriği değil, <b>ERP kaydı</b> değiştiriyor — müşterinin fatura adresi,
 * unvanı, sevk noktaları. Kayıt başvurularıyla aynı yetki sınırı.
 * </p>
 */
@Tag(name = "Cari güncelleme kuyruğu",
        description = "Müşteri adres/bilgi talepleri; onaylananlar Mikro'ya yazılır.")
@RestController
@RequestMapping("/api/cari-updates")
@PreAuthorize("hasAnyRole('ADMIN','SATIS')")
public class CariUpdateAdminController {

    private final CariUpdateAdminService service;

    public CariUpdateAdminController(CariUpdateAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Kuyruk",
            description = """
                    Talepler; **bekleyenler önce**, kendi içlerinde en uzun
                    bekleyen en üstte.

                    Her talebin yanında carinin **Mikro'daki mevcut adresleri**
                    ve adres ekleme taleplerinde **benzerlik uyarısı** geliyor.
                    Uyarı, bu ekranın en değerli parçası: aynı adresin ikinci
                    kez girilmesi en olası hata ve onu engelleyen şey onay
                    değil, operatöre göstermek.

                    `durum` verilmezse hepsi (BEKLIYOR | AKTARILDI | REDDEDILDI).""")
    @GetMapping
    public ResponseEntity<Map<String, Object>> kuyruk(
            @RequestParam(name = "durum", required = false) String durum,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(service.kuyruk(durum, limit));
    }

    @Operation(summary = "Bekleyen talep sayısı", description = "Menüdeki rozet için.")
    @GetMapping("/pending-count")
    public ResponseEntity<Map<String, Object>> bekleyen() {
        return ResponseEntity.ok(Map.of("bekleyen", service.bekleyenSayisi()));
    }

    @Operation(summary = "Carinin Mikro'daki adresleri",
            description = "\"Bu cariye adres ekle\" ekranı ve mükerrer kontrolü için.")
    @GetMapping("/cari/{cariKod}/addresses")
    public ResponseEntity<Map<String, Object>> cariAdresleri(
            @PathVariable("cariKod") String cariKod) {
        return ResponseEntity.ok(service.cariAdresleri(cariKod));
    }

    @Operation(summary = "Talebi düzenle",
            description = """
                    Aktarmadan **önce** yazım hatasını düzeltmek için. Null
                    bırakılan alan değişmez.

                    Kuyruğun asıl değeri burada: insan onayı tek başına çöp
                    veriyi engellemez, düzeltme imkânı engeller.""")
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> duzenle(@PathVariable("id") Long id,
            @RequestBody CariUpdateAdminService.TalepYamasi yama) {
        return ResponseEntity.ok(Map.of("data", service.duzenle(id, yama)));
    }

    @Operation(summary = "Mikro'ya aktar",
            description = """
                    Talebi ERP'ye yazar ve **AKTARILDI** olarak işaretler.

                    Adres ekleme taleplerinde yeni `adr_adres_no`
                    hesaplanır (`MAX+1`) ve yanıtta döner. Numara 1'den
                    başlamayabilir — ölçüldü, 205 satırda 0.

                    Yanıt kodları:
                    - **200** aktarıldı
                    - **400** talep zaten sonuçlanmış, cari yok, ya da adres
                      numarası bu sırada başkası tarafından kullanıldı""")
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Map<String, Object>> aktar(@PathVariable("id") Long id,
            Authentication authentication) {
        return ResponseEntity.ok(Map.of("data", service.aktar(id, staffId(authentication))));
    }

    /** Ret isteği. */
    public record RetIstegi(String neden) {
    }

    @Operation(summary = "Talebi reddet",
            description = "Sebep **zorunlu** — müşteri bu metni görüyor.")
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reddet(@PathVariable("id") Long id,
            @RequestBody RetIstegi istek, Authentication authentication) {
        return ResponseEntity.ok(Map.of("data",
                service.reddet(id, istek == null ? null : istek.neden(), staffId(authentication))));
    }

    @Operation(summary = "Cariye doğrudan adres ekle",
            description = """
                    Kuyruk beklemeden Mikro'ya yazar. 492 carinin hiç adresi
                    yok ve adres olmadan sipariş verilemiyor (`sip_adresno`
                    var olan bir satırı göstermek zorunda).

                    Kuyruk atlanıyor çünkü kuyruğun amacı **müşteri verisini
                    denetlemek**; operatörün kendi girdiği veri zaten
                    denetlenmiş sayılır.""")
    @PostMapping("/cari/{cariKod}/addresses")
    public ResponseEntity<Map<String, Object>> adresEkle(@PathVariable("cariKod") String cariKod,
            @RequestBody CariUpdateAdminService.TalepYamasi veri, Authentication authentication) {
        return ResponseEntity.ok(Map.of("data",
                service.adresEkle(cariKod, veri, staffId(authentication))));
    }

    // --- Hatalar: mesaj operatöre olduğu gibi gösteriliyor ---

    @ExceptionHandler(CariUpdateAdminService.GecersizIstek.class)
    public ResponseEntity<Map<String, Object>> gecersiz(CariUpdateAdminService.GecersizIstek e) {
        return ResponseEntity.badRequest().body(Map.of("error", "gecersiz", "mesaj", e.getMessage()));
    }

    @ExceptionHandler({ CariWriter.CariBulunamadi.class, CariWriter.AdresBulunamadi.class,
            CariWriter.AdresNumarasiCakisti.class })
    public ResponseEntity<Map<String, Object>> erp(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "erp", "mesaj", e.getMessage()));
    }

    private Long staffId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            return null;
        }
        return principal.getId();
    }
}
