package com.simge.adminbackend.appdb.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Düz bir sayfanın tek bir içerik bloğu (ADR D-172).
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın eşi; tablo ortak
 * ({@code SIMGE_OS_APP.SIMGE_PAGE_BLOCK}): <b>panel yazar, vitrin okur</b>.
 * </p>
 *
 * <p>
 * <b>Bloklar panelden eklenip silinmiyor.</b> {@code blockKey} vitrindeki bir
 * yuvanın adı; şablonda karşılığı olmayan bir anahtar hiçbir şey çizmez.
 * Panel blokların <b>içeriğini</b> yönetiyor — metnini, görselini,
 * açık/kapalı olmasını.
 * </p>
 */
@Entity
@Table(name = "SIMGE_PAGE_BLOCK")
@Getter
@Setter
public class PageBlock {

    /** Hakkımızda sayfası — bugün panelden düzenlenebilen tek sayfa. */
    public static final String PAGE_ABOUT = "about";

    public static final String TYPE_RICH_TEXT = "RICH_TEXT";
    public static final String TYPE_FEATURE = "FEATURE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_key", nullable = false)
    private String pageKey;

    @Column(name = "block_key", nullable = false)
    private String blockKey;

    @Column(name = "block_type", nullable = false)
    private String blockType;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** Görselin adresi; baytlar {@code SIMGE_IMAGE_BLOB}'ta (D-153). */
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "title_tr")
    private String titleTr;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "body_tr", columnDefinition = "NVARCHAR(MAX)")
    private String bodyTr;

    @Column(name = "body_en", columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
