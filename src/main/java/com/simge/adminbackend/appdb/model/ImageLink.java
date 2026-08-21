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

    /**
     * Ana sayfa öğesi görseli — banner ve hizmet ikonları (ADR D-154).
     *
     * <p>
     * Anahtarı {@code "<öğe id>:<dil>"} ({@code "12:tr"}). Dil anahtarın
     * parçası çünkü banner'ların TR ve EN görselleri ayrı: üzerinde yazı olan
     * bir banner iki dilde iki farklı dosya.
     * </p>
     *
     * <p>
     * <b>Bu bağ neden var:</b> öğenin görseli aslında
     * {@code SIMGE_HOME_SECTION_ITEM.image_tr} sütununda duruyor ve çizimde
     * okunan tek yer orası. Bağ, baytların <b>sahipsiz görünmemesi</b> için
     * kuruluyor: V17'nin sonundaki bakım sorgusu hiçbir bağı olmayan baytları
     * "öksüz" sayıyor ve bağ kurulmasaydı, yayında olan bir banner'ın baytları
     * o listede çıkardı.
     * </p>
     */
    public static final String OWNER_HOME = "HOME";

    /**
     * Düz sayfa bloğunun görseli — Hakkımızda tanıtım fotoğrafı ve hizmet
     * ikonları (ADR D-172).
     *
     * <p>
     * Anahtarı blok kimliği ({@code "7"}). {@code OWNER_HOME}'dan farklı olarak
     * <b>dil anahtarın parçası değil</b>: bu görsellerin üzerinde yazı yok,
     * aynı fotoğraf iki dilde de doğru.
     * </p>
     *
     * <p>
     * Bağ, adresin kendisi {@code SIMGE_PAGE_BLOCK.image_url}'da dursa bile
     * kuruluyor: V17'nin bakım sorgusu hiçbir bağı olmayan baytları "öksüz"
     * sayıyor ve yayında olan bir görsel o listede çıkmamalı.
     * </p>
     */
    public static final String OWNER_PAGE = "PAGE";

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
