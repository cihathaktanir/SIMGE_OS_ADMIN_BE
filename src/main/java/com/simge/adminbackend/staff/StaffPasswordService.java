package com.simge.adminbackend.staff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/**
 * Personelin kendi parolasını değiştirmesi (ADR D-123).
 *
 * <p>
 * Hem ilk girişteki zorunlu değişiklik hem sonraki gönüllü değişiklikler bu
 * servisten geçer — iki ayrı yol olsaydı biri kurallardan birini kaçırırdı.
 * </p>
 *
 * <p>
 * <b>Mevcut parola her durumda sorulur</b>, zorunlu değişiklikte bile. Zorunlu
 * değişikliğe gelen kişi parolayı zaten az önce girdi; sormanın maliyeti sıfır,
 * karşılığında açık bırakılmış bir oturumun başında oturan biri parolayı ele
 * geçiremiyor.
 * </p>
 */
@Service
public class StaffPasswordService {

    private static final Logger log = LoggerFactory.getLogger(StaffPasswordService.class);

    private final StaffUserRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final StaffSessionRevoker sessionRevoker;

    public StaffPasswordService(StaffUserRepository staffRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            StaffSessionRevoker sessionRevoker) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionRevoker = sessionRevoker;
    }

    /**
     * @param keepSessionId işlemi yapan oturum; diğerleri düşürülür
     * @return tazelenmiş oturum nesnesi — çağıran bunu {@code SecurityContext}'e
     *         yazmak zorunda, yoksa {@code mustChangePassword} bayrağı eski
     *         oturumda açık kalır ve kullanıcı panele giremez
     */
    @Transactional(transactionManager = "appTransactionManager")
    public StaffPrincipal change(Long staffId, String currentPassword, String newPassword,
            String keepSessionId) {

        StaffUser user = staffRepository.findById(staffId).orElseThrow(NotFoundException::new);

        if (!passwordEncoder.matches(
                currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new WrongCurrentPasswordException();
        }

        if (newPassword != null && newPassword.equals(currentPassword)) {
            throw new SamePasswordException();
        }

        PasswordPolicy.Violation violation =
                passwordPolicy.validate(newPassword, user.getUsername(), user.getFullName());
        if (violation != null) {
            throw new WeakPasswordException(violation);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        staffRepository.save(user);

        sessionRevoker.revokeAllExcept(user.getUsername(), keepSessionId);
        log.info("Personel parolası değiştirildi: staffId={}", staffId);

        return new StaffPrincipal(user);
    }

    public static class NotFoundException extends RuntimeException {
    }

    public static class WrongCurrentPasswordException extends RuntimeException {
    }

    public static class SamePasswordException extends RuntimeException {
    }

    public static class WeakPasswordException extends RuntimeException {
        private final transient PasswordPolicy.Violation violation;

        public WeakPasswordException(PasswordPolicy.Violation violation) {
            this.violation = violation;
        }

        public PasswordPolicy.Violation getViolation() {
            return violation;
        }
    }
}
