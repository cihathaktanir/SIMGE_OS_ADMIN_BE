package com.simge.adminbackend.appdb.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import com.simge.adminbackend.appdb.model.StorefrontUser;

/**
 * Vitrin kullanıcıları — panelde <b>salt okunur</b>.
 *
 * <p>
 * {@code JpaRepository} yerine çıplak {@link Repository}: bu tablonun sahibi
 * vitrin backend'i ve panelin ona yazması bir tasarım hatası olurdu. Yazma
 * metodu arayüzde yoksa, hata derleme zamanında yakalanır — aynı gerekçe
 * ERP'deki {@code ReadOnlyRepository} için de geçerli (D-104).
 * </p>
 */
public interface StorefrontUserRepository extends Repository<StorefrontUser, Long> {

    boolean existsByEmailIgnoreCase(String email);

    List<StorefrontUser> findByCariKodOrderByIdAsc(String cariKod);
}
