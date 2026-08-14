package com.simge.adminbackend.erp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.simge.adminbackend.erp.ReadOnlyRepository;
import com.simge.adminbackend.erp.model.Stok;

/**
 * Görsel yükleme ekranının ürün araması (ADR D-142) — <b>salt okunur</b>.
 */
public interface StokRepository extends ReadOnlyRepository<Stok, Long> {

    Optional<Stok> findFirstByKod(String kod);

    /** Toplu yüklemede dosya adlarını tek sorguda eşleştirmek için. */
    List<Stok> findByKodIn(List<String> kodlar);

    /**
     * Kod veya isimde arama.
     *
     * <p>
     * Desen {@code TurkishSearch} tarafından üretiliyor; T-SQL karakter
     * sınıflarıyla ("[gğGĞ]") Türkçe/ASCII farkını yok sayıyor. JPQL'e
     * dokunulmuyor, gelen düz bir parametre.
     * </p>
     *
     * <p>
     * Pasif ve iptal kartlar eleniyor: onlara görsel yüklemenin anlamı yok.
     * </p>
     */
    @Query("""
            SELECT s FROM Stok s
            WHERE (s.iptal IS NULL OR s.iptal = false)
              AND (s.pasif IS NULL OR s.pasif = false)
              AND (s.kod LIKE :desen OR s.isim LIKE :desen)
            ORDER BY CASE WHEN s.kod LIKE :onEk THEN 0 ELSE 1 END, s.isim
            """)
    List<Stok> ara(@Param("desen") String desen, @Param("onEk") String onEk, Pageable sayfa);
}
