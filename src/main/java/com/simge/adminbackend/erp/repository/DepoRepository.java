package com.simge.adminbackend.erp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;

import com.simge.adminbackend.erp.ReadOnlyRepository;
import com.simge.adminbackend.erp.model.Depo;

/**
 * Mikro depoları (ADR D-152).
 *
 * <p>
 * {@link ReadOnlyRepository} genişletiyor: depo <b>seçiliyor</b>, ERP'de
 * değiştirilmiyor. Seçim {@code SIMGE_OS_APP}'teki ayara yazılıyor.
 * </p>
 */
public interface DepoRepository extends ReadOnlyRepository<Depo, Long> {

    /** Seçilebilir depolar: iptal edilmemiş, numarası pozitif. */
    @Query("""
            SELECT d FROM Depo d
            WHERE (d.iptal IS NULL OR d.iptal = false)
              AND d.no IS NOT NULL AND d.no > 0
            ORDER BY d.no
            """)
    List<Depo> secilebilirler();

    Optional<Depo> findFirstByNo(Integer no);
}
