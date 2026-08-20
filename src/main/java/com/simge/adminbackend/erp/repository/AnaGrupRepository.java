package com.simge.adminbackend.erp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;

import com.simge.adminbackend.erp.ReadOnlyRepository;
import com.simge.adminbackend.erp.model.AnaGrup;

/**
 * Mikro kategorileri (ADR D-153).
 *
 * <p>
 * {@link ReadOnlyRepository} genişletiyor, {@code JpaRepository} değil: bu
 * arayüzde {@code save}/{@code delete} yok, dolayısıyla ERP'ye yazmak
 * <b>derleme hatası</b> (D-104).
 * </p>
 */
public interface AnaGrupRepository extends ReadOnlyRepository<AnaGrup, Long> {

    /** İptal edilmemiş kategoriler, isme göre. */
    @Query("""
            SELECT g FROM AnaGrup g
            WHERE (g.iptal IS NULL OR g.iptal = false)
              AND g.kod IS NOT NULL
            ORDER BY g.isim
            """)
    List<AnaGrup> aktifler();

    List<AnaGrup> findByRecnoIn(List<Long> recnolar);
}
