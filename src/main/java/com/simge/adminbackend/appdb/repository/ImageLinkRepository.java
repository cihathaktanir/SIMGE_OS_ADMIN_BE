package com.simge.adminbackend.appdb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simge.adminbackend.appdb.model.ImageLink;

public interface ImageLinkRepository extends JpaRepository<ImageLink, Long> {

    List<ImageLink> findByOwnerTypeAndOwnerKeyOrderBySortOrderAsc(String ownerType, String ownerKey);

    Optional<ImageLink> findFirstByOwnerTypeAndOwnerKeyAndContentHash(
            String ownerType, String ownerKey, String contentHash);

    /** Yeni görsel listenin sonuna eklensin diye; hiç yoksa -1 döner. */
    @Query("""
            SELECT COALESCE(MAX(l.sortOrder), -1)
            FROM ImageLink l
            WHERE l.ownerType = :ownerType AND l.ownerKey = :ownerKey
            """)
    int enBuyukSira(@Param("ownerType") String ownerType, @Param("ownerKey") String ownerKey);

    /**
     * Hangi ürünlerin görseli var — panelde "eksik olanları göster" için.
     *
     * <p>
     * Yalnızca anahtarları döner; baytlara dokunmaz.
     * </p>
     */
    @Query("""
            SELECT DISTINCT l.ownerKey
            FROM ImageLink l
            WHERE l.ownerType = :ownerType AND l.ownerKey IN :keys
            """)
    List<String> gorseliOlanlar(@Param("ownerType") String ownerType,
            @Param("keys") List<String> keys);

    long countByOwnerType(String ownerType);
}
