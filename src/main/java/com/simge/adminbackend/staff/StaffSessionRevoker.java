package com.simge.adminbackend.staff;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * Bir personelin tüm açık panel oturumlarını düşürür.
 *
 * <p>
 * Oturumlar sunucuda (Spring Session JDBC, {@code SIMGE_STAFF_SESSION}) durduğu
 * için bu mümkün — JWT olsaydı token süresi dolana kadar iptal edilemezdi.
 * Panelin ERP'ye yazacak olması bu farkı önemli kılıyor: yetkisi alınan ya da
 * hesabı kapatılan biri bir sonraki istekte düşmeli.
 * </p>
 *
 * <p>
 * Dizin anahtarı {@code PRINCIPAL_NAME}; Spring Session bunu
 * {@code Authentication.getName()}'den alır ve {@link StaffPrincipal} orada
 * <b>kullanıcı adını</b> döndürür.
 * </p>
 */
@Component
public class StaffSessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(StaffSessionRevoker.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public StaffSessionRevoker(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** @param username personelin kullanıcı adı */
    public void revokeAll(String username) {
        revokeAllExcept(username, null);
    }

    /**
     * Bir oturum hariç hepsini düşürür.
     *
     * <p>
     * Parolayı kullanıcı <b>kendi isteğiyle</b> değiştirdiğinde kullanılır:
     * diğer cihazlar düşer ama işlemi yapan kişi giriş ekranına atılmaz.
     * </p>
     *
     * @param keepSessionId korunacak oturum; {@code null} ise hepsi düşer
     */
    public void revokeAllExcept(String username, String keepSessionId) {
        if (username == null || username.isBlank()) {
            return;
        }

        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(username);

        int revoked = 0;
        for (String id : sessions.keySet()) {
            if (id.equals(keepSessionId)) {
                continue;
            }
            sessionRepository.deleteById(id);
            revoked++;
        }

        if (revoked > 0) {
            log.info("{} açık panel oturumu düşürüldü", revoked);
        }
    }
}
