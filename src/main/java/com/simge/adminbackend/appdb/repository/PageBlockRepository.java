package com.simge.adminbackend.appdb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simge.adminbackend.appdb.model.PageBlock;

/**
 * Düz sayfa içeriğinin yazma tarafı (ADR D-172).
 */
public interface PageBlockRepository extends JpaRepository<PageBlock, Long> {

    /**
     * Bir sayfanın <b>tüm</b> blokları — pasif olanlar dahil.
     *
     * <p>
     * Vitrin yalnızca aktifleri okuyor; panel hepsini görmek zorunda, yoksa
     * kapatılan bir blok ekrandan kaybolur ve geri açılamaz.
     * </p>
     */
    List<PageBlock> findByPageKeyOrderBySortOrderAscIdAsc(String pageKey);
}
