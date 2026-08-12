package com.simge.adminbackend.appdb.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.simge.adminbackend.appdb.model.StaffUser;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<StaffUser> findAllByOrderByUsernameAsc();

    /**
     * Belirli role sahip aktif personel sayısı.
     *
     * <p>
     * Türetilmiş sorgu adı yerine açık JPQL: {@code roles} bir
     * {@code @ElementCollection} ve koleksiyon üyeliğini isimden türetmek
     * sürüme göre değişen bir davranış — burada yanlış sayı, kendini kilitleyen
     * bir panel demek.
     * </p>
     */
    @Query("""
            SELECT COUNT(s) FROM StaffUser s
             WHERE :role MEMBER OF s.roles
               AND s.status = :status
            """)
    long countByRoleAndStatus(@Param("role") String role, @Param("status") String status);
}
