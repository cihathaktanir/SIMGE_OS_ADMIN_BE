package com.simge.adminbackend.appdb.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bir görselin hangi ürüne ya da kategoriye ait olduğu (ADR D-142).
 *
 * <p>
 * {@link #ownerKey} ürünlerde Mikro {@code STOKLAR.sto_kod}, kategorilerde
 * grup kodu. <b>Veritabanı FK'sı değil</b>: Mikro ayrı bir veritabanı ve
 * orası tek doğru kaynak (D-100). Ürün ERP'den kalkarsa satır kalır, vitrin
 * okumada göstermez.
 * </p>
 *
 * <p>
 * {@code sto_RECno} değil {@code sto_kod} kullanılıyor: toplu yüklemede
 * operatör dosyayı SKU ile adlandırıyor ve RECno Mikro'nun iç sıra numarası.
 * </p>
 */
@Entity
@Table(name = "SIMGE_IMAGE_LINK")
@Getter
@Setter
public class ImageLink {

    public static final String OWNER_PRODUCT = "PRODUCT";
    public static final String OWNER_CATEGORY = "CATEGORY";

    /** Karoda ve listede gösterilen görselin sırası. */
    public static final int BIRINCIL = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Column(name = "owner_key", nullable = false)
    private String ownerKey;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = BIRINCIL;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "source_bytes")
    private Integer sourceBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** {@code SIMGE_STAFF_USER.id} — kim yükledi. */
    @Column(name = "created_by")
    private Long createdBy;
}
