package com.simge.adminbackend.appdb.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simge.adminbackend.appdb.model.CariUpdateRequest;

/**
 * Cari güncelleme talepleri — panel tarafı (ADR D-173).
 *
 * <p>
 * Vitrin tarafındaki eşinden farkı: burada sorgular cari koduna bağlı
 * <b>değil</b>, çünkü operatör tüm firmaların kuyruğunu görüyor.
 * </p>
 */
public interface CariUpdateRequestRepository extends JpaRepository<CariUpdateRequest, Long> {

    /**
     * Kuyruk listesi.
     *
     * <p>
     * Sıralama bilinçli: <b>bekleyenler önce</b>, kendi içlerinde eskiden
     * yeniye. Karara bağlanmış talepler tarihe göre yeniden eskiye. Böylece
     * en uzun bekleyen iş en üstte çıkıyor — operatör listeyi sıralamak
     * zorunda kalmıyor.
     * </p>
     */
    @Query("""
            SELECT r FROM CariUpdateRequest r
             WHERE (:durum IS NULL OR r.status = :durum)
             ORDER BY CASE WHEN r.status = 'BEKLIYOR' THEN 0 ELSE 1 END ASC,
                      CASE WHEN r.status = 'BEKLIYOR' THEN r.id END ASC,
                      r.id DESC
            """)
    List<CariUpdateRequest> listele(@Param("durum") String durum, Pageable pageable);

    /** Menüdeki rozet için. */
    long countByStatus(String status);

    /** Bir carinin talep geçmişi — kart açıldığında gösteriliyor. */
    List<CariUpdateRequest> findByCariKodOrderByIdDesc(String cariKod);
}
