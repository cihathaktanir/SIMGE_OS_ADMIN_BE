package com.simge.adminbackend.erp.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * "Bu depo seçilirse vitrinde ne olur?" sorusunun sayısal cevabı (ADR D-152).
 *
 * <p>
 * Depo seçimi, panelde yapılabilecek <b>en yıkıcı</b> ayar değişikliği: yanlış
 * depo vitrindeki tüm fiyatları değiştirir ya da katalogu tamamen boşaltır.
 * Operatörün seçmeden önce görmesi gereken şey deponun adı değil, <b>içinde
 * kaç ürün olduğu</b>.
 * </p>
 *
 * <p>
 * Ölçüldü — Mikro'daki 17 depo:
 * </p>
 *
 * <pre>
 *   depo  ad                  fiyatlı   hareketli
 *     4   ELMADAG 3             7.338       5.990   &lt;- şu anki
 *     7   CAYYOLU               7.351       4.436
 *    12   CAYYOLU 2 DEPO        1.352          26
 *    17   SANAL DEPO                0           0   &lt;- vitrin tamamen boşalır
 * </pre>
 *
 * <p>
 * <b>Tek sorgu, tek geçiş.</b> Depo başına ayrı sorgu atmak
 * {@code STOK_HAREKETLERI} (2,97 milyon satır) üzerinde 17 tarama demekti;
 * tek bir depo için o tarama tek başına ~112 ms sürüyor.
 * </p>
 */
@Repository
public class DepoDolulukRepository {

    /** Mikro birimi (birincil {@code EntityManagerFactory}); {@link StokAramaRepository} ile aynı desen. */
    @PersistenceContext
    private EntityManager em;

    /**
     * Bir deponun vitrin açısından doluluğu.
     *
     * @param fiyatliUrun bu depoda 1 numaralı satış listesinde fiyatı olan ürün
     *                    sayısı — vitrinde <b>görünebilecek</b> ürünler
     * @param stokluUrun  bu depoda stok hareketi olan ürün sayısı — "Sepete
     *                    Ekle" düğmesi açılabilecek ürünler
     */
    public record Doluluk(int fiyatliUrun, int stokluUrun) {
    }

    /**
     * Tüm depoların doluluğu: {@code dep_no -> Doluluk}.
     *
     * <p>
     * Sorgu <b>salt okunur</b> ve tamamen sabit; kullanıcı girdisi almıyor.
     * </p>
     */
    @SuppressWarnings("unchecked")
    public Map<Integer, Doluluk> hepsi() {
        String sql = """
                WITH f AS (
                    SELECT sfiyat_deposirano AS depo,
                           COUNT(DISTINCT sfiyat_stokkod) AS fiyatli
                    FROM STOK_SATIS_FIYAT_LISTELERI
                    WHERE sfiyat_listesirano = 1 AND sfiyat_fiyati > 0
                    GROUP BY sfiyat_deposirano
                ), h AS (
                    SELECT depo, COUNT(DISTINCT sth_stok_kod) AS hareketli FROM (
                        SELECT sth_giris_depo_no AS depo, sth_stok_kod
                          FROM STOK_HAREKETLERI WHERE sth_tip IN (0, 2)
                        UNION ALL
                        SELECT sth_cikis_depo_no AS depo, sth_stok_kod
                          FROM STOK_HAREKETLERI WHERE sth_tip IN (1, 2)
                    ) x GROUP BY depo
                )
                SELECT d.dep_no, ISNULL(f.fiyatli, 0), ISNULL(h.hareketli, 0)
                FROM DEPOLAR d
                LEFT JOIN f ON f.depo = d.dep_no
                LEFT JOIN h ON h.depo = d.dep_no
                """;

        List<Object[]> satirlar = em.createNativeQuery(sql).getResultList();

        Map<Integer, Doluluk> sonuc = new HashMap<>(satirlar.size());
        for (Object[] s : satirlar) {
            if (s[0] == null) {
                continue;
            }
            sonuc.put(((Number) s[0]).intValue(),
                    new Doluluk(((Number) s[1]).intValue(), ((Number) s[2]).intValue()));
        }
        return sonuc;
    }
}
