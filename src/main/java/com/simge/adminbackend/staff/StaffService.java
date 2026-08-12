package com.simge.adminbackend.staff;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/**
 * Personel hesaplarının yönetimi (ADR D-123). Yalnızca {@code ROLE_ADMIN}.
 *
 * <p>
 * <b>Hesap açmak tek adım.</b> Yönetici kullanıcı adı, ad soyad ve rolleri
 * girer; sistem geçici parolayı üretir ve <b>bir kez</b> ekrana döner. E-posta
 * gönderimi yok, doğrulama bağlantısı yok, bekleme yok — depoda çalışmaya
 * başlayan biri için hesap dakikalar değil saniyeler içinde hazır olmalı.
 * Bunun bedeli, parolanın bir süre iki kişi tarafından bilinmesi; karşılığı
 * {@code must_change_password} bayrağı ({@link StaffPasswordChangeGate}).
 * </p>
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    private final StaffUserRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernamePolicy usernamePolicy;
    private final StaffSessionRevoker sessionRevoker;

    public StaffService(StaffUserRepository staffRepository,
            PasswordEncoder passwordEncoder,
            UsernamePolicy usernamePolicy,
            StaffSessionRevoker sessionRevoker) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.usernamePolicy = usernamePolicy;
        this.sessionRevoker = sessionRevoker;
    }

    /** Hesap açıldığında/parola sıfırlandığında dönen tek seferlik sonuç. */
    public record CreatedAccount(StaffUser user, String temporaryPassword) {
    }

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public List<StaffUser> list() {
        return staffRepository.findAllByOrderByUsernameAsc();
    }

    /**
     * Yeni personel hesabı açar ve geçici parolayı döner.
     *
     * @param roles en az bir rol; rolsüz hesap giriş yapamaz
     *        ({@link StaffAuthService})
     */
    @Transactional(transactionManager = "appTransactionManager")
    public CreatedAccount create(Long createdBy, String rawUsername, String fullName,
            String email, Set<String> roles) {

        String username = usernamePolicy.normalize(rawUsername);
        UsernamePolicy.Violation violation = usernamePolicy.validate(username);
        if (violation != null) {
            throw new InvalidUsernameException(violation);
        }
        if (staffRepository.existsByUsername(username)) {
            throw new UsernameTakenException();
        }

        Set<String> cleanRoles = cleanRoles(roles);

        String temporary = TempPassword.generate();

        StaffUser user = new StaffUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setFullName(trimToNull(fullName));
        user.setEmail(trimToNull(email));
        user.setStatus(StaffUser.STATUS_ACTIVE);
        user.setMustChangePassword(true);
        user.setFailedLoginCount(0);
        user.setCreatedBy(createdBy);
        user.setRoles(cleanRoles);
        staffRepository.save(user);

        // Parola loglanmaz — yalnızca hangi hesabın açıldığı.
        log.info("Personel hesabı açıldı: staffId={} username={} roller={} açan={}",
                user.getId(), username, cleanRoles, createdBy);
        return new CreatedAccount(user, temporary);
    }

    /**
     * Rolleri değiştirir.
     *
     * <p>
     * Son aktif yöneticinin {@code ADMIN} rolü alınamaz: alınırsa panele
     * kimse personel ekleyemez ve kilit yalnızca veritabanından açılır.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public StaffUser updateRoles(Long staffId, Set<String> roles) {
        StaffUser user = staffRepository.findById(staffId).orElseThrow(NotFoundException::new);
        Set<String> cleanRoles = cleanRoles(roles);

        if (user.hasRole(StaffUser.ROLE_ADMIN) && !cleanRoles.contains(StaffUser.ROLE_ADMIN)) {
            requireAnotherAdmin(user.getId());
        }

        user.setRoles(cleanRoles);
        staffRepository.save(user);

        // Yetki değişti; kullanıcı çıkıp girene kadar eskisiyle dolaşmasın.
        sessionRevoker.revokeAll(user.getUsername());
        log.info("Personel rolleri güncellendi: staffId={} roller={}", staffId, cleanRoles);
        return user;
    }

    /** Hesabı açar/kapatır. Kapatma, açık oturumları da düşürür. */
    @Transactional(transactionManager = "appTransactionManager")
    public StaffUser setStatus(Long staffId, boolean active) {
        StaffUser user = staffRepository.findById(staffId).orElseThrow(NotFoundException::new);

        if (!active && user.hasRole(StaffUser.ROLE_ADMIN)) {
            requireAnotherAdmin(user.getId());
        }

        user.setStatus(active ? StaffUser.STATUS_ACTIVE : StaffUser.STATUS_DISABLED);
        if (active) {
            // Kapatılan hesap açılırken kilit sayacı da sıfırlansın; aksi halde
            // hesap "açık ama girilemiyor" durumunda kalabiliyor.
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        staffRepository.save(user);

        if (!active) {
            sessionRevoker.revokeAll(user.getUsername());
        }
        log.info("Personel hesabı {}: staffId={}", active ? "açıldı" : "kapatıldı", staffId);
        return user;
    }

    /**
     * Parolayı sıfırlar ve yeni geçici parolayı döner.
     *
     * <p>
     * Personelin e-posta adresi olmak zorunda olmadığı için "bana sıfırlama
     * bağlantısı gönder" akışı yok; sıfırlamayı yönetici yapar. Sıfırlanan
     * hesabın açık oturumları düşürülür — parolayı unutan kişinin oturumu
     * başkasının elindeyse orada kalmasın.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public CreatedAccount resetPassword(Long staffId) {
        StaffUser user = staffRepository.findById(staffId).orElseThrow(NotFoundException::new);

        String temporary = TempPassword.generate();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(true);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        staffRepository.save(user);

        sessionRevoker.revokeAll(user.getUsername());
        log.info("Personel parolası sıfırlandı: staffId={}", staffId);
        return new CreatedAccount(user, temporary);
    }

    /** Adı/e-postayı günceller; kullanıcı adı değişmez (giriş kimliği). */
    @Transactional(transactionManager = "appTransactionManager")
    public StaffUser updateProfile(Long staffId, String fullName, String email) {
        StaffUser user = staffRepository.findById(staffId).orElseThrow(NotFoundException::new);
        user.setFullName(trimToNull(fullName));
        user.setEmail(trimToNull(email));
        staffRepository.save(user);
        return user;
    }

    private void requireAnotherAdmin(Long excludingStaffId) {
        long activeAdmins = staffRepository.countByRoleAndStatus(
                StaffUser.ROLE_ADMIN, StaffUser.STATUS_ACTIVE);
        // Sayı bu kullanıcının kendisini de içeriyor; başka bir yönetici varsa >1.
        if (activeAdmins <= 1) {
            log.warn("Son yönetici korundu: staffId={}", excludingStaffId);
            throw new LastAdminException();
        }
    }

    private Set<String> cleanRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new NoRolesException();
        }
        Set<String> clean = new LinkedHashSet<>();
        for (String role : roles) {
            String value = role == null ? "" : role.trim().toUpperCase(java.util.Locale.ROOT);
            if (!StaffUser.ALL_ROLES.contains(value)) {
                throw new UnknownRoleException(value);
            }
            clean.add(value);
        }
        return clean;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class NotFoundException extends RuntimeException {
    }

    public static class UsernameTakenException extends RuntimeException {
    }

    public static class NoRolesException extends RuntimeException {
    }

    /** Panel kendini kilitlemesin: son aktif yönetici korunur. */
    public static class LastAdminException extends RuntimeException {
    }

    public static class UnknownRoleException extends RuntimeException {
        private final transient String role;

        public UnknownRoleException(String role) {
            this.role = role;
        }

        public String getRole() {
            return role;
        }
    }

    public static class InvalidUsernameException extends RuntimeException {
        private final transient UsernamePolicy.Violation violation;

        public InvalidUsernameException(UsernamePolicy.Violation violation) {
            this.violation = violation;
        }

        public UsernamePolicy.Violation getViolation() {
            return violation;
        }
    }
}
