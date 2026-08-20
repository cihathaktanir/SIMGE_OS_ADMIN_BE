package com.simge.adminbackend.appdb.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Vitrin teması (ADR D-154).
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın eşi; tablo ortak. Panel yazar,
 * vitrin okur.
 * </p>
 */
@Entity
@Table(name = "SIMGE_THEME")
@Getter
@Setter
public class StoreTheme {

    /** Aktif tema. Aynı anda yalnızca bir temada 1 olmalı. */
    public static final int AKTIF = 1;
    public static final int PASIF = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
