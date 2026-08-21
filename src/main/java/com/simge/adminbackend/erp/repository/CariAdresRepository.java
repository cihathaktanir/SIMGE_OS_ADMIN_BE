package com.simge.adminbackend.erp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.simge.adminbackend.erp.ReadOnlyRepository;
import com.simge.adminbackend.erp.model.CariAdres;

/**
 * Mikro cari adresleri — panel okuma tarafı (ADR D-173).
 *
 * <p>
 * Kuyruk ekranı bekleyen adresin yanında carinin <b>mevcut</b> adreslerini
 * gösteriyor. Bu, ekranın en değerli parçası: "ERP'ye çöp girer" senaryosunu
 * asıl kesen şey insan onayı değil, aynı adresin ikinci kez girildiğini
 * operatöre <b>göstermek</b>.
 * </p>
 *
 * <p>
 * Salt okunur ({@link ReadOnlyRepository}). Adres yazma yolu yalnızca
 * {@code CariWriter} — D-127'nin "ERP'ye kim yazabiliyor" değişmezi.
 * </p>
 */
@Repository
public interface CariAdresRepository extends ReadOnlyRepository<CariAdres, Long> {

    @Query("""
            SELECT a FROM CariAdres a
             WHERE a.cariKod = :cariKod
               AND (a.iptal  IS NULL OR a.iptal  = false)
               AND (a.hidden IS NULL OR a.hidden = false)
             ORDER BY a.adresNo
            """)
    List<CariAdres> findActiveByCariKod(@Param("cariKod") String cariKod);
}
