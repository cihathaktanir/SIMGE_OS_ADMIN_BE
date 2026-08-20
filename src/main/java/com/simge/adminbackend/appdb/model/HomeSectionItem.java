package com.simge.adminbackend.appdb.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bir bölümün içindeki tekil öğe (ADR D-154).
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın eşi; tablo ortak.
 * </p>
 *
 * <h2>Görsel alanları</h2>
 * <p>
 * {@code imageTr}/{@code imageEn} bir <b>adres</b> tutuyor, bayt değil. İki
 * biçim geçerli:
 * </p>
 * <ul>
 *   <li>{@code /images/simge/banner/...} — vitrin paketiyle gelen dosya,</li>
 *   <li>{@code /api/images/&lt;hash&gt;/detail.jpg} — panelden yüklenmiş,
 *       {@code SIMGE_IMAGE_BLOB}'ta duran görsel.</li>
 * </ul>
 * <p>
 * Vitrin ikisini adrese bakarak ayırıyor (D-153). Panelden yükleme yapıldığında
 * bu sütun ikinci biçime geçiyor ve <b>tek doğru kaynak</b> o oluyor: ayrıca
 * bir bağ tablosu tutulmuyor, çünkü iki yerde tutulan bir adres er geç ayrışır.
 * </p>
 */
@Entity
@Table(name = "SIMGE_HOME_SECTION_ITEM")
@Getter
@Setter
public class HomeSectionItem {

    public static final String REF_PRODUCT = "PRODUCT";
    public static final String REF_CATEGORY = "CATEGORY";
    public static final String REF_BANNER = "BANNER";
    public static final String REF_SERVICE = "SERVICE";
    public static final String REF_BLOG = "BLOG";
    public static final String REF_BRAND = "BRAND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private HomeSection section;

    @Column(name = "ref_type", nullable = false)
    private String refType;

    @Column(name = "ref_id")
    private String refId;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "image_tr")
    private String imageTr;

    @Column(name = "image_en")
    private String imageEn;

    @Column(name = "title_tr")
    private String titleTr;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "subtitle_tr")
    private String subtitleTr;

    @Column(name = "subtitle_en")
    private String subtitleEn;

    @Column(name = "tag_tr")
    private String tagTr;

    @Column(name = "tag_en")
    private String tagEn;

    @Column(name = "button_text_tr")
    private String buttonTextTr;

    @Column(name = "button_text_en")
    private String buttonTextEn;

    @Column(name = "link_type")
    private String linkType;

    @Column(name = "link_value")
    private String linkValue;
}
