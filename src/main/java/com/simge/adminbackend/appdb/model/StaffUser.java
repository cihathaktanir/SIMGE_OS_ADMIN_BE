package com.simge.adminbackend.appdb.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Yönetim paneline giren Simge çalışanı (ADR D-123).
 *
 * <p>
 * <b>Vitrinin {@code SIMGE_USER} tablosundan ayrıdır.</b> Sebepleri
 * {@code db/admin/V1__staff_users_and_sessions.sql} başlığında; özeti: farklı
 * kimlik (kullanıcı adı ↔ e-posta), farklı güven bölgesi (intranet ↔ internet)
 * ve tek tablo olsaydı "personel vitrine giremez" kuralının bir {@code WHERE}
 * koşuluna kalacak olması.
 * </p>
 *
 * <p>
 * <b>E-posta zorunlu değil.</b> Depo görevlisinin kurumsal adresi olmayabilir;
 * hesabı e-postaya bağlamak, olmayan bir şeyi uydurmayı gerektirirdi. Bunun
 * sonucu: parola sıfırlama <b>kendi kendine yapılamaz</b>, yönetici sıfırlar.
 * Kapalı bir iç ağda çalışan, sayısı onlarla ölçülen bir kullanıcı kümesi için
 * doğru takas.
 * </p>
 */
@Entity
@Table(name = "SIMGE_STAFF_USER")
@Getter
@Setter
public class StaffUser {

    /** Panelin tamamı + personel yönetimi. */
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SATIS = "SATIS";
    public static final String ROLE_DEPO = "DEPO";
    public static final String ROLE_MUHASEBE = "MUHASEBE";
    /** Vitrin içeriği (ana sayfa bölümleri, öne çıkan ürünler). */
    public static final String ROLE_ICERIK = "ICERIK";

    public static final Set<String> ALL_ROLES =
            Set.of(ROLE_ADMIN, ROLE_SATIS, ROLE_DEPO, ROLE_MUHASEBE, ROLE_ICERIK);

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Giriş kimliği. ASCII, küçük harf; {@code UsernamePolicy} doğrular. */
    @Column(name = "username", nullable = false)
    private String username;

    /** BCrypt. Hesap her zaman bir parolayla açılır, boş kalmaz. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name")
    private String fullName;

    /** İsteğe bağlı; giriş için kullanılmaz, yalnızca "kim bu" bilgisi. */
    @Column(name = "email")
    private String email;

    @Column(name = "status", nullable = false)
    private String status = STATUS_ACTIVE;

    /**
     * Geçici parolayla açılan hesap, kullanıcı kendi parolasını belirleyene
     * kadar bu bayrakla işaretli kalır ve panelde <b>hiçbir uç çalışmaz</b>
     * (bkz. {@code StaffPasswordChangeGate}). Bayrağı taşımasaydık geçici
     * parola — en az iki kişinin bildiği bir parola — süresiz geçerli olurdu.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Hesabı açan personelin id'si; ilk hesabı sistem açtığı için null olabilir. */
    @Column(name = "created_by")
    private Long createdBy;

    // SYSUTCDATETIME() ile dolan datetime2 sütunları — offset taşımaz.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * Roller ayrı tabloda: bir kişi hem depo hem satış olabiliyor. Tek bir
     * {@code role} kolonu olsaydı ilk çift rol ihtiyacında "DEPO_SATIS" gibi
     * birleşik değerler türerdi.
     *
     * <p>
     * EAGER: rol kümesi olmadan oturum nesnesi kurulamıyor ve personel sayısı
     * onlarla ölçülüyor — tembel yükleme burada kazanç değil, sadece
     * {@code LazyInitializationException} riski olurdu.
     * </p>
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "SIMGE_STAFF_ROLE", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "role", nullable = false)
    private Set<String> roles = new LinkedHashSet<>();

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
