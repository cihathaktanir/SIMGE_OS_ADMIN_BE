package com.simge.adminbackend.erp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Mikro deposu — <b>salt okunur</b> (ADR D-152).
 *
 * <p>
 * Panel, vitrinin hangi depodan okuyacağını seçtiriyor. Seçenekler uydurulmuyor:
 * liste doğrudan Mikro'nun {@code DEPOLAR} tablosundan geliyor, çünkü tek doğru
 * kaynak orası. Kodda sabit bir depo listesi tutmak, ERP'de yeni depo açıldığında
 * ya da bir depo kapandığında sessizce yanlış olurdu.
 * </p>
 */
@Entity
@Table(name = "DEPOLAR")
@Getter
@Setter
public class Depo {

    @Id
    @Column(name = "dep_RECno")
    private Long recno;

    /**
     * Depo numarası — vitrinin ayarında saklanan değer.
     *
     * <p>
     * {@code dep_RECno} değil: fiyat ({@code sfiyat_deposirano}) ve stok
     * ({@code sth_giris_depo_no} / {@code sth_cikis_depo_no}) tablolarındaki
     * alanlar bu numarayı taşıyor.
     * </p>
     */
    @Column(name = "dep_no")
    private Integer no;

    @Column(name = "dep_adi")
    private String adi;

    /** Mikro'da iptal edilmiş depo; seçilebilir olmamalı. */
    @Column(name = "dep_iptal")
    private Boolean iptal;
}
