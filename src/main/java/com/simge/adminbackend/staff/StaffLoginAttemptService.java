package com.simge.adminbackend.staff;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/**
 * Giriş denemesi sayacı ve hesap kilidi.
 *
 * <p>
 * <b>Neden ayrı bir bean:</b> başarısız denemeyi kaydeden yazma işlemi, kimlik
 * doğrulama hatasıyla birlikte geri alınmamalı. Sayaç artırma çağıran metodun
 * transaction'ı içinde yapılırsa, hemen ardından fırlatılan exception rollback
 * tetikler ve sayaç hiç artmaz — kilitleme sessizce çalışmaz hale gelir. Bu
 * yüzden {@code REQUIRES_NEW} ile kendi transaction'ında commit edilir. Ayrıca
 * aynı sınıf içinden çağrılsaydı Spring proxy'si devreye girmeyeceği için
 * {@code @Transactional} zaten etkisiz kalırdı.
 * </p>
 */
@Service
public class StaffLoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(StaffLoginAttemptService.class);

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int LOCK_MINUTES = 15;

    private final StaffUserRepository staffRepository;

    public StaffLoginAttemptService(StaffUserRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Transactional(transactionManager = "appTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long staffId) {
        staffRepository.findById(staffId).ifPresent(user -> {
            int failed = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
            if (failed >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
                user.setFailedLoginCount(0);
                log.warn("Personel hesabı kilitlendi ({} dk): staffId={}", LOCK_MINUTES, staffId);
            } else {
                user.setFailedLoginCount(failed);
            }
            staffRepository.save(user);
            // Parola ve kullanıcı adı loglanmaz.
            log.info("Başarısız personel girişi: staffId={} ardışık={}", staffId, failed);
        });
    }

    @Transactional(transactionManager = "appTransactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long staffId) {
        staffRepository.findById(staffId).ifPresent(user -> {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(Instant.now());
            staffRepository.save(user);
        });
    }
}
