package com.simge.adminbackend.appdb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.simge.adminbackend.appdb.model.StoreTheme;

public interface StoreThemeRepository extends JpaRepository<StoreTheme, Long> {

    List<StoreTheme> findAllByOrderBySortOrderAscIdAsc();

    Optional<StoreTheme> findBySlug(String slug);

    /** Aktif tema; vitrin bunun bölümlerini çiziyor. */
    Optional<StoreTheme> findFirstByStatusOrderBySortOrderAscIdAsc(Integer status);
}
