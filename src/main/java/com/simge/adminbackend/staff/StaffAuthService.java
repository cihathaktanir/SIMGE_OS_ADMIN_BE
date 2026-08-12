package com.simge.adminbackend.staff;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/**
 * Personel giriş doğrulaması.
 *
 * <p>
 * Başarısız girişte hangi bilginin yanlış olduğu <b>söylenmez</b> — kullanıcı
 * yok / parola yanlış / hesap pasif, hepsi aynı sonucu döner.
 * </p>
 *
 * <p>
 * <b>Rolü olmayan hesap giriş yapamaz.</b> Rolsüz bir personel panelde hiçbir
 * şey göremez; girişi kabul edip ardından her ekranda 403 vermek, kullanıcıya
 * "sistem bozuk" hissi verir. Rol atanmamışsa hesap henüz hazır değildir.
 * </p>
 */
@Service
public class StaffAuthService {

    private static final Logger log = LoggerFactory.getLogger(StaffAuthService.class);

    /**
     * Kullanıcı bulunamadığında da özet karşılaştırması yapılır ki cevap süresi
     * "böyle bir kullanıcı var mı" sorusunu ele vermesin.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final StaffUserRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffLoginAttemptService loginAttemptService;
    private final UsernamePolicy usernamePolicy;

    public StaffAuthService(StaffUserRepository staffRepository,
            PasswordEncoder passwordEncoder,
            StaffLoginAttemptService loginAttemptService,
            UsernamePolicy usernamePolicy) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.usernamePolicy = usernamePolicy;
    }

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public StaffPrincipal authenticate(String username, String rawPassword) {
        String normalized = usernamePolicy.normalize(username);
        String password = rawPassword == null ? "" : rawPassword;

        StaffUser user = staffRepository.findByUsername(normalized).orElse(null);

        if (user == null) {
            passwordEncoder.matches(password, DUMMY_HASH);
            throw new InvalidCredentialsException();
        }

        if (user.isLocked()) {
            log.warn("Kilitli personel hesabına giriş denemesi: staffId={}", user.getId());
            throw new AccountLockedException(user.getLockedUntil());
        }

        boolean passwordOk = passwordEncoder.matches(password, user.getPasswordHash());

        if (!passwordOk || !user.isActive() || user.getRoles().isEmpty()) {
            loginAttemptService.recordFailure(user.getId());
            throw new InvalidCredentialsException();
        }

        loginAttemptService.recordSuccess(user.getId());
        log.info("Personel girişi başarılı: staffId={} roller={}", user.getId(), user.getRoles());
        return new StaffPrincipal(user);
    }

    /**
     * Oturumdaki personel nesnesini veritabanından tazeler.
     *
     * <p>
     * Parola değiştikten sonra ({@code must_change_password} inince) ve rolleri
     * değişen kullanıcıda çağrılır. Oturumdaki nesne serileştirilmiş bir kopya;
     * tazelenmezse kullanıcı çıkıp girene kadar eski yetkileriyle dolaşır.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public StaffPrincipal reload(Long staffId) {
        return staffRepository.findById(staffId).map(StaffPrincipal::new).orElse(null);
    }

    /** Kullanıcı yok / parola yanlış / hesap pasif / rolü yok — ayrım yapılmaz. */
    public static class InvalidCredentialsException extends RuntimeException {
    }

    public static class AccountLockedException extends RuntimeException {
        private final transient Instant until;

        public AccountLockedException(Instant until) {
            this.until = until;
        }

        public Instant getUntil() {
            return until;
        }
    }
}
