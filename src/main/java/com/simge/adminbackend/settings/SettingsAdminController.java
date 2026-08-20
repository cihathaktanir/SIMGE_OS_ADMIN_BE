package com.simge.adminbackend.settings;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.simge.adminbackend.staff.StaffPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Vitrin ayarları (ADR D-152).
 *
 * <p>
 * <b>Yalnızca ADMIN.</b> Görsel yüklemek içerik işi ve ICERIK rolüne açık; depo
 * değiştirmek değil — vitrindeki tüm fiyatları ve stokları değiştiriyor.
 * </p>
 */
@Tag(name = "Vitrin ayarları", description = "Vitrinin deposu gibi çalışma zamanı ayarları.")
@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SettingsAdminController {

    private final WarehouseService warehouseService;

    public SettingsAdminController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @Operation(summary = "Depo seçenekleri",
            description = """
                    Mikro'daki depolar; her satırda o depoda **fiyatı olan** ve
                    **stok hareketi olan** ürün sayısı var.

                    Sayılar seçim için kritik: adı masum görünen bir depo (ör.
                    "SANAL DEPO") boş olabilir ve seçilirse vitrin tamamen
                    boşalır. `uygun=false` olan satırlar seçtirilmiyor.""")
    @GetMapping("/warehouse")
    public ResponseEntity<Map<String, Object>> depolar() {
        Map<String, Object> cevap = new LinkedHashMap<>();
        cevap.put("mevcut", warehouseService.mevcutDepo());
        cevap.put("data", warehouseService.depolar());
        return ResponseEntity.ok(cevap);
    }

    /** İstek gövdesi. */
    public record DepoIstegi(Integer depo) {
    }

    @Operation(summary = "Depoyu değiştir",
            description = """
                    **Vitrindeki tüm fiyatları ve stokları etkiler.** Değişiklik
                    anında geçerli olur: vitrin backend'i depoyu her sorgudan
                    önce okuyor, yeniden başlatma gerekmiyor.

                    Yanıt kodları:
                    - **200** değişti
                    - **400** depo yok, iptalli, 0 ya da vitrini boşaltacak kadar boş""")
    @PutMapping("/warehouse")
    public ResponseEntity<Map<String, Object>> depoDegistir(@RequestBody DepoIstegi istek,
            Authentication authentication) {

        if (istek == null || istek.depo() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "depo_gerekli"));
        }

        try {
            WarehouseService.DepoSatiri sonuc =
                    warehouseService.degistir(istek.depo(), staffId(authentication));
            return ResponseEntity.ok(Map.of("data", sonuc));

        } catch (WarehouseService.GecersizDepo e) {
            // Mesaj operatöre olduğu gibi gösteriliyor: "geçersiz depo" demek
            // yerine hangi sayının neden reddedildiğini söylüyor.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "gecersiz_depo", "mesaj", e.getMessage()));
        }
    }

    private Long staffId(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            return null;
        }
        return principal.getId();
    }
}
