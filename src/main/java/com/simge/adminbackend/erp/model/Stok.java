package com.simge.adminbackend.erp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Mikro stok kartı — <b>salt okunur</b>, yalnızca görsel yükleme ekranının
 * ihtiyacı kadar alan (ADR D-142).
 *
 * <p>
 * Vitrin backend'indeki {@code Product} sınıfının dar bir kopyasıdır. Panelin
 * burada ihtiyacı ürün aramak: kod, isim ve grup. Fiyat, stok, birim gibi
 * alanlar bilerek yok — yükleme ekranı onları göstermiyor ve almak
 * {@code STOK_SATIS_FIYAT_LISTELERI} ile birleşim gerektirirdi.
 * </p>
 *
 * <p>
 * Kopya olmasının gerekçesi {@link CariHesap} ile aynı.
 * </p>
 */
@Entity
@Table(name = "STOKLAR")
@Getter
@Setter
public class Stok {

    @Id
    @Column(name = "sto_RECno")
    private Long recno;

    /** SKU. Görsellerin bağlandığı anahtar (D-142). */
    @Column(name = "sto_kod")
    private String kod;

    @Column(name = "sto_isim")
    private String isim;

    @Column(name = "sto_anagrup_kod")
    private String anaGrupKod;

    @Column(name = "sto_altgrup_kod")
    private String altGrupKod;

    /** Mikro'da pasif/iptal edilmiş kartlar; arama bunları elemeli. */
    @Column(name = "sto_iptal")
    private Boolean iptal;

    @Column(name = "sto_pasif_fl")
    private Boolean pasif;
}
