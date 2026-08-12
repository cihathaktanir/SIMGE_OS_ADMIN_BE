package com.simge.adminbackend.appdb.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.simge.adminbackend.appdb.model.CompanyInvitation;

/**
 * Davetler — panel tarafındaki görünüm.
 *
 * <p>
 * Panel davet <b>oluşturur</b> ve listeler; kabul akışı (token doğrulama, hesap
 * açma) vitrin backend'inde kalır. Bu yüzden {@code findFirstByTokenHash}
 * burada yok: panelin token'la yapacağı bir iş olmamalı.
 * </p>
 */
@Repository
public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {

    List<CompanyInvitation> findByCariKodOrderByIdDesc(String cariKod);

    /**
     * Aynı adrese açık davetleri iptal eder — yeni davet gönderilmeden önce.
     * Aynı anda birden çok geçerli token dolaşmasın; eski bağlantı elinde
     * kalan biri onu kullanabilirdi.
     */
    @Modifying
    @Query("""
            UPDATE CompanyInvitation i
               SET i.status = :cancelled
             WHERE LOWER(i.email) = LOWER(:email)
               AND i.status = :pending
            """)
    void cancelOpenFor(@Param("email") String email,
            @Param("pending") String pending, @Param("cancelled") String cancelled);
}
