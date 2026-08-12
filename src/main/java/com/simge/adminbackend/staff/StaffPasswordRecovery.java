package com.simge.adminbackend.staff;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/**
 * Kilitlenmiş panelden çıkış yolu (ADR D-125).
 *
 * <p>
 * <b>Hangi sorunu çözüyor:</b> personelde e-posta zorunlu değil (D-123),
 * dolayısıyla "şifremi unuttum" bağlantısı yok; parolayı ancak başka bir
 * yönetici sıfırlayabiliyor. Sistemde tek yönetici varsa ve o parolasını
 * unutursa panele giriş yolu kalmıyordu — tek çare veritabanına elle BCrypt
 * özeti yazmaktı. Kimse yanında BCrypt üreteciyle dolaşmıyor.
 * </p>
 *
 * <p>
 * Kullanımı: {@code SIMGE_ADMIN_RESET=<kullanıcı adı>} ile servisi bir kez
 * başlatın. Hesabın parolası sıfırlanır, yeni geçici parola <b>bir kereye
 * mahsus log'a</b> yazılır ve ilk girişte değiştirilmesi zorunlu olur.
 * </p>
 *
 * <p>
 * <b>Neden yeni bir saldırı yüzeyi açmıyor:</b> bunu çalıştırabilmek için
 * sunucuda ortam değişkeni tanımlayabilmek gerekiyor. O yetkiye sahip olan kişi
 * zaten {@code SIMGE_APP_DB_PASSWORD}'ü okuyup aynı satırı SQL ile
 * güncelleyebilir. Mekanizma yetki eklemiyor; yalnızca meşru yolu
 * kullanılabilir hâle getiriyor.
 * </p>
 *
 * <p>
 * <b>Kasıtlı olarak YAPMADIĞI şey:</b> rol vermiyor ve kapalı hesabı açmıyor.
 * Verseydi bu bayrak, "herhangi bir kullanıcıyı yönetici yap" anahtarına
 * dönüşürdü. Hedef hesap kapalıysa ya da rolü yoksa parola yine sıfırlanır ama
 * giriş çalışmaz; durum log'da açıkça söylenir.
 * </p>
 */
@Component
@Order(20) // StaffBootstrap'ten (10) SONRA: boş veritabanında önce hesap açılsın.
public class StaffPasswordRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffPasswordRecovery.class);

    private final StaffUserRepository staffRepository;
    private final StaffService staffService;
    private final UsernamePolicy usernamePolicy;
    private final String resetUsername;

    public StaffPasswordRecovery(StaffUserRepository staffRepository,
            StaffService staffService,
            UsernamePolicy usernamePolicy,
            @Value("${simge.admin-reset.username:}") String resetUsername) {
        this.staffRepository = staffRepository;
        this.staffService = staffService;
        this.usernamePolicy = usernamePolicy;
        this.resetUsername = resetUsername;
    }

    @Override
    @Transactional(transactionManager = "appTransactionManager")
    public void run(ApplicationArguments args) {
        String username = usernamePolicy.normalize(resetUsername);
        if (username.isEmpty()) {
            return;
        }

        StaffUser user = staffRepository.findByUsername(username).orElse(null);
        if (user == null) {
            // Hesap AÇILMIYOR. Yanlış yazılmış bir kullanıcı adı, sessizce yeni
            // bir yönetici doğurmamalı.
            log.error("""

                    ==================================================================
                     PAROLA SIFIRLAMA İSTENDİ AMA HESAP YOK: '{}'
                     Hiçbir şey yapılmadı; hesap oluşturulmaz. Kullanıcı adını
                     kontrol edip SIMGE_ADMIN_RESET değerini düzeltin.
                    ==================================================================
                    """, username);
            return;
        }

        StaffService.CreatedAccount reset = staffService.resetPassword(user.getId());

        log.warn("""

                ==================================================================
                 PAROLA SIFIRLANDI (SIMGE_ADMIN_RESET)
                   kullanıcı adı : {}
                   geçici parola : {}
                 Bu parola bir daha gösterilmeyecek. İlk girişte değiştirilmesi
                 ZORUNLU; değiştirilene kadar panelde hiçbir uç çalışmaz.

                 >>> SIMGE_ADMIN_RESET DEĞİŞKENİNİ ŞİMDİ KALDIRIN. <<<
                 Tanımlı kaldığı sürece HER AÇILIŞTA parola yeniden sıfırlanır ve
                 kullanıcı belirlediği parolayla giriş yapamaz.
                ==================================================================
                """, user.getUsername(), reset.temporaryPassword());

        // Parola sıfırlandı ama giriş yine de çalışmayacaksa bunu söyle —
        // kullanıcı log'daki parolayı deneyip "yine olmadı" diye dönmesin.
        if (!user.isActive()) {
            log.error("DİKKAT: '{}' hesabı KAPALI durumda; parola sıfırlandı ama giriş "
                    + "yapılamaz. Bu mekanizma hesap açmaz — açık bir yöneticiyle "
                    + "hesabı etkinleştirin ya da veritabanında status='ACTIVE' yapın.",
                    user.getUsername());
        }
        if (user.getRoles().isEmpty()) {
            log.error("DİKKAT: '{}' hesabının hiç rolü yok; parola sıfırlandı ama giriş "
                    + "yapılamaz. Bu mekanizma rol vermez — verseydi 'herhangi bir "
                    + "kullanıcıyı yönetici yap' anahtarına dönüşürdü.",
                    user.getUsername());
        }
    }
}
