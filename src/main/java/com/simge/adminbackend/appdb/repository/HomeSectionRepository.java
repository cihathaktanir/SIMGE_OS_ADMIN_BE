package com.simge.adminbackend.appdb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simge.adminbackend.appdb.model.HomeSection;

public interface HomeSectionRepository extends JpaRepository<HomeSection, Long> {

    /**
     * Bir temanın tüm bölümleri, öğeleriyle birlikte — <b>tek sorguda</b>.
     *
     * <p>
     * {@code LEFT JOIN FETCH}: bölüm başına ayrı öğe sorgusu (N+1) atmamak için.
     * {@code DISTINCT} gerekiyor çünkü fetch join, bölümü öğe sayısı kadar
     * tekrarlıyor.
     * </p>
     */
    @Query("""
            SELECT DISTINCT s FROM HomeSection s
            LEFT JOIN FETCH s.items
            WHERE s.themeSlug = :slug
            ORDER BY s.sortOrder ASC, s.id ASC
            """)
    List<HomeSection> temaninBolumleri(@Param("slug") String slug);

    Optional<HomeSection> findByThemeSlugAndSectionKey(String themeSlug, String sectionKey);
}
