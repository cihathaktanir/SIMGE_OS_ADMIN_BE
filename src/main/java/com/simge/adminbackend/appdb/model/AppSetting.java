package com.simge.adminbackend.appdb.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Çalışma zamanı ayarı (ADR D-152).
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın eşi. Tablo ortak
 * ({@code SIMGE_OS_APP.SIMGE_SETTING}): <b>panel yazar, vitrin okur</b>.
 * </p>
 *
 * <p>
 * Doğrulama burada, yazan tarafta yapılıyor. Vitrin okuma yolunda iş kuralı
 * çalıştırmıyor — orada yapılan tek şey, değer bozuksa yedeğe düşmek.
 * </p>
 */
@Entity
@Table(name = "SIMGE_SETTING")
@Getter
@Setter
public class AppSetting {

    /** Vitrinin deposu. Fiyat da stok da bundan okunur; bölünmez (D-137). */
    public static final String KEY_WAREHOUSE = "storefront.warehouse";

    @Id
    @Column(name = "setting_key", nullable = false, length = 64)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 400)
    private String value;

    @Column(name = "description", length = 400)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
