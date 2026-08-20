package com.simge.adminbackend.appdb.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ana sayfadaki tek bir bölüm (ADR D-154).
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın eşi; tablo ortak
 * ({@code SIMGE_OS_APP.SIMGE_HOME_SECTION}).
 * </p>
 *
 * <p>
 * <b>Bölümler panelden eklenip silinmiyor.</b> {@code sectionKey} vitrin
 * şablonundaki bir yuvanın adı: şablonda karşılığı olmayan bir anahtar
 * eklemek hiçbir şey çizmez, var olanı silmek ise o yuvayı sessizce
 * boşaltır. Panel bölümlerin <b>içeriğini</b> yönetiyor — başlığını, sırasını,
 * açık/kapalı olmasını ve öğelerini.
 * </p>
 */
@Entity
@Table(name = "SIMGE_HOME_SECTION")
@Getter
@Setter
public class HomeSection {

    public static final String TYPE_BANNER = "BANNER";
    public static final String TYPE_BANNER_GROUP = "BANNER_GROUP";
    public static final String TYPE_CATEGORY_LIST = "CATEGORY_LIST";
    public static final String TYPE_PRODUCT_LIST = "PRODUCT_LIST";
    public static final String TYPE_SERVICE_LIST = "SERVICE_LIST";
    public static final String TYPE_BLOG_LIST = "BLOG_LIST";
    public static final String TYPE_BRAND_LIST = "BRAND_LIST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theme_slug", nullable = false)
    private String themeSlug;

    @Column(name = "section_key", nullable = false)
    private String sectionKey;

    @Column(name = "section_type", nullable = false)
    private String sectionType;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "title_tr")
    private String titleTr;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "subtitle_tr")
    private String subtitleTr;

    @Column(name = "subtitle_en")
    private String subtitleEn;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<HomeSectionItem> items = new ArrayList<>();
}
