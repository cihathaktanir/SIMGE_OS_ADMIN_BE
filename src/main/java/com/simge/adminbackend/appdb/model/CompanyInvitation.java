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
 * Hesap kurma daveti — <b>panel yazar, vitrin kabul eder.</b>
 *
 * <p>
 * Tablonun sahibi {@code SIMGE_OS_BE}. Panel iki durumda satır ekler: bir kayıt
 * başvurusu onaylandığında (D-124). Bağlantıya tıklanınca hesabı açan taraf
 * yine vitrin backend'idir — parola orada belirlenir, panelin parolayla hiçbir
 * teması olmaz.
 * </p>
 *
 * <p>
 * <b>Token açık saklanmaz</b>, yalnızca SHA-256 özeti ({@link #tokenHash}).
 * </p>
 */
@Entity
@Table(name = "SIMGE_COMPANY_INVITATION")
@Getter
@Setter
public class CompanyInvitation {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** {@link #invitedBy} bir {@code SIMGE_USER.id} — firma yetkilisi davet etti. */
    public static final String INVITER_CUSTOMER = "CUSTOMER";
    /** {@link #invitedBy} bir {@code SIMGE_STAFF_USER.id} — panelden onaylandı. */
    public static final String INVITER_STAFF = "STAFF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cari_kod", nullable = false)
    private String cariKod;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    /**
     * {@link #invitedBy} hangi tablonun id'si.
     *
     * <p>
     * Personel ayrı bir tabloya taşınınca (D-123) {@code invited_by} tek başına
     * belirsizleşti: 7 numara hem bir müşteri hem bir personel olabilir. Tür
     * kolonu olmadan "bu daveti kim gönderdi" sorusunun cevabı yanlış kişiyi
     * gösterebilirdi.
     * </p>
     */
    @Column(name = "invited_by_type", nullable = false)
    private String invitedByType = INVITER_CUSTOMER;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "status", nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "created_user_id")
    private Long createdUserId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(Instant.now());
    }

    public boolean isUsable() {
        return STATUS_PENDING.equals(status) && !isExpired();
    }
}
