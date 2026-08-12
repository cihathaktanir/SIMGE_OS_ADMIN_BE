package com.simge.adminbackend.appdb.model;

import java.time.Instant;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Vitrine kayıt başvurusu — <b>vitrin backend'i yazar, panel okur ve
 * sonuçlandırır.</b>
 *
 * <p>
 * Tablonun sahibi {@code SIMGE_OS_BE} (şeması {@code db/app} altında);
 * buradaki eşleme aynı satırların panel tarafındaki görünümüdür. Sütun
 * eklenmesi gerekirse migration o repoda yazılır, burada değil.
 * </p>
 */
@Entity
@Table(name = "SIMGE_REGISTRATION_REQUEST")
@Getter
@Setter
public class RegistrationRequest {

    /** Cari bulundu ve e-postası dolu — kişi kendi kendine kayıt olabiliyordu. */
    public static final String BRANCH_CARI_WITH_EMAIL = "CARI_WITH_EMAIL";
    /** Cari bulundu ama e-postası boş — kişiyi cariye panel bağlar. */
    public static final String BRANCH_CARI_NO_EMAIL = "CARI_NO_EMAIL";
    /** Bu vergi numarasıyla cari yok — önce Mikro'da cari açılmalı. */
    public static final String BRANCH_NO_CARI = "NO_CARI";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vergi_no", nullable = false)
    private String vergiNo;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "branch", nullable = false)
    private String branch;

    @Column(name = "matched_cari_kod")
    private String matchedCariKod;

    @Column(name = "status", nullable = false)
    private String status = STATUS_PENDING;

    /**
     * Başvuruyu sonuçlandıran <b>personel</b> id'si
     * ({@link StaffUser#getId()}). Sütun eskiden {@code SIMGE_USER.id}
     * taşıyordu; onay artık panelde yapıldığı için anlamı değişti (D-124).
     * Geçmiş kayıtlarda hâlâ eski anlam geçerli — sütunda FK yok, denetim
     * için okunuyor.
     */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "created_user_id")
    private Long createdUserId;

    // SYSUTCDATETIME() ile dolan datetime2 sütunları — offset taşımaz.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
