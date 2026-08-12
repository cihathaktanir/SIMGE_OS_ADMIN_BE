package com.simge.adminbackend.erp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.simge.adminbackend.erp.ReadOnlyRepository;
import com.simge.adminbackend.erp.model.CariHesap;

@Repository
public interface CariHesaplarRepository extends ReadOnlyRepository<CariHesap, Long> {

    /**
     * Panelde cari arama.
     *
     * <p>
     * Başvuruyu onaylayan kişi doğru cariyi seçmek zorunda; unvanın tamamını ya
     * da cari kodunu ezbere bilmesi beklenemez. Kod, unvan ve vergi numarası tek
     * kutudan aranır.
     * </p>
     */
    @Query("""
            SELECT c FROM CariHesap c
            WHERE (c.iptal IS NULL OR c.iptal = false)
              AND (c.hidden IS NULL OR c.hidden = false)
              AND (UPPER(c.cariKod) LIKE UPPER(CONCAT('%', :q, '%'))
                OR UPPER(c.cariUnvan1) LIKE UPPER(CONCAT('%', :q, '%'))
                OR c.vergiDairesiNo LIKE CONCAT('%', :q, '%'))
            ORDER BY c.cariKod
            """)
    List<CariHesap> search(@Param("q") String q, Pageable pageable);

    /** Tek cari — onay sırasında kodun gerçekten var olduğunu doğrulamak için. */
    @Query("""
            SELECT c FROM CariHesap c
            WHERE c.cariKod = :cariKod
              AND (c.iptal IS NULL OR c.iptal = false)
              AND (c.hidden IS NULL OR c.hidden = false)
            """)
    Optional<CariHesap> findActiveByCariKod(@Param("cariKod") String cariKod);

    /**
     * Kod önerisi için: bu önekle başlayan <b>tüm</b> kodlar.
     *
     * <p>
     * İptal/gizli olanlar da dahil — kod benzersizliği tüm tabloda geçerli,
     * iptal edilmiş bir carinin kodunu yeniden kullanmak benzersiz indekse
     * takılır.
     * </p>
     */
    @Query("SELECT c.cariKod FROM CariHesap c WHERE c.cariKod LIKE CONCAT(:onek, '%')")
    List<String> kodlariBul(@Param("onek") String onek);

    /** Aynı vergi numarasına düşen kodlar — cari açmadan önce mükerrer kontrolü. */
    @Query("""
            SELECT c.cariKod FROM CariHesap c
            WHERE c.vergiDairesiNo = :vergiNo
              AND (c.iptal IS NULL OR c.iptal = false)
              AND (c.hidden IS NULL OR c.hidden = false)
            """)
    List<String> kodlariVergiNoIle(@Param("vergiNo") String vergiNo);
}
