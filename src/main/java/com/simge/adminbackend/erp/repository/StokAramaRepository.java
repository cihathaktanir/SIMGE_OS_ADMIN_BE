package com.simge.adminbackend.erp.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.simge.adminbackend.erp.model.Stok;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

/**
 * Çok kelimeli ürün araması (ADR D-151).
 *
 * <p>
 * Neden ayrı bir sınıf: koşul <b>sayısı</b> girdiye bağlı ("TOZ ŞEKER 50KG" üç
 * koşul) ve JPQL'de değişken sayıda AND'i sabit bir {@code @Query} ile yazmanın
 * yolu yok. Burada sorgu metni koşul sayısına göre kuruluyor.
 * </p>
 *
 * <p>
 * <b>Kullanıcı girdisi sorguya asla gömülmüyor.</b> Değişken olan tek şey koşul
 * SAYISI; kelimelerin kendisi {@code :k0}, {@code :k1} … olarak bağlanıyor.
 * </p>
 */
@Repository
public class StokAramaRepository {

    /** Kelime sayısı {@code TurkishSearch.MAX_TOKENS} ile zaten sınırlı. */
    @PersistenceContext
    private EntityManager em;

    /**
     * Tüm kelimeleri <b>AND</b>'leyerek arar.
     *
     * <p>
     * Öncesinde yalnızca ilk kelime kullanılıyordu: operatör ürünün tam adını
     * yapıştırınca ("TOZ ŞEKER 50KG") sorgu "TOZ" ile çalışıyor, toz biberden
     * toz deterjana kadar her şey dönüyor ve aranan ürün sonuç sınırının
     * dışında kalabiliyordu.
     * </p>
     *
     * @param desenler her kelimenin {@code %kelime%} hâli
     * @param onEk     ilk kelimenin {@code kelime%} hâli; kodu bununla başlayan
     *                 kayıtlar listenin başına alınıyor
     */
    public List<Stok> ara(List<String> desenler, String onEk, int limit) {
        StringBuilder jpql = new StringBuilder("""
                SELECT s FROM Stok s
                WHERE (s.iptal IS NULL OR s.iptal = false)
                  AND (s.pasif IS NULL OR s.pasif = false)
                """);

        for (int i = 0; i < desenler.size(); i++) {
            jpql.append("  AND (s.kod LIKE :k").append(i)
                .append(" OR s.isim LIKE :k").append(i).append(")\n");
        }

        jpql.append("ORDER BY CASE WHEN s.kod LIKE :onEk THEN 0 ELSE 1 END, s.isim");

        TypedQuery<Stok> sorgu = em.createQuery(jpql.toString(), Stok.class);
        for (int i = 0; i < desenler.size(); i++) {
            sorgu.setParameter("k" + i, desenler.get(i));
        }
        sorgu.setParameter("onEk", onEk);
        sorgu.setMaxResults(limit);

        return sorgu.getResultList();
    }
}
