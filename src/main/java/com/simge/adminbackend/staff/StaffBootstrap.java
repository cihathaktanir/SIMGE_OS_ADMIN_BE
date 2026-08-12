package com.simge.adminbackend.staff;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/**
 * İlk yönetici hesabı.
 *
 * <p>
 * Personel tablosu <b>tamamen boşsa</b> tek bir {@code ADMIN} hesabı açılır ve
 * geçici parolası bir kereye mahsus log'a yazılır. Tablo boş değilse hiçbir şey
 * yapılmaz — "yönetici yoksa aç" gibi bir kural, tüm yöneticileri pasife alan
 * birinin sessizce yeni bir yönetici doğurmasına yol açardı.
 * </p>
 *
 * <p>
 * <b>Parola yapılandırmada tutulmaz.</b> Rastgele üretilir, yalnızca özeti
 * saklanır ve hesap {@code must_change_password} ile işaretli açılır: ilk giren
 * kişi kendi parolasını belirlemeden panelde hiçbir şey yapamaz. Log'a düşen
 * parola bu yüzden tek kullanımlık.
 * </p>
 */
@Component
public class StaffBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffBootstrap.class);

    private final StaffUserRepository staffRepository;
    private final StaffService staffService;
    private final String bootstrapUsername;

    public StaffBootstrap(StaffUserRepository staffRepository,
            StaffService staffService,
            @Value("${simge.bootstrap-admin.username:admin}") String bootstrapUsername) {
        this.staffRepository = staffRepository;
        this.staffService = staffService;
        this.bootstrapUsername = bootstrapUsername;
    }

    @Override
    @Transactional(transactionManager = "appTransactionManager")
    public void run(ApplicationArguments args) {
        if (staffRepository.count() > 0) {
            return;
        }

        StaffService.CreatedAccount created = staffService.create(
                null, bootstrapUsername, "Sistem Yöneticisi", null,
                Set.of(StaffUser.ROLE_ADMIN));

        log.warn("""

                ==================================================================
                 İLK YÖNETİCİ HESABI AÇILDI (personel tablosu boştu)
                   kullanıcı adı : {}
                   geçici parola : {}
                 Bu parola bir daha gösterilmeyecek. İlk girişte değiştirilmesi
                 ZORUNLU; değiştirilene kadar panelde hiçbir uç çalışmaz.
                ==================================================================
                """, created.user().getUsername(), created.temporaryPassword());
    }
}
