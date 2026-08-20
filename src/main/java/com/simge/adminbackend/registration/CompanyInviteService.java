package com.simge.adminbackend.registration;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.CompanyInvitation;
import com.simge.adminbackend.appdb.repository.CompanyInvitationRepository;
import com.simge.adminbackend.appdb.repository.StorefrontUserRepository;
import com.simge.adminbackend.common.SecretCodes;
import com.simge.adminbackend.mail.MailService;
import com.simge.adminbackend.mail.MailTemplates;

/**
 * Panelin gönderdiği hesap kurma daveti (ADR D-124).
 *
 * <p>
 * <b>Onay bir davete dönüşür, hesaba değil.</b> Yönetici hiçbir zaman parola
 * belirlemiyor ya da bilmiyor; başvurana kendi parolasını kurması için bağlantı
 * gidiyor. Alternatifi — e-postayla geçici parola göndermek — parolayı posta
 * kutusunda açık bırakırdı. (Personel hesaplarında geçici parola var, çünkü
 * orada parola posta kutusundan değil elden geçiyor ve e-posta zorunlu değil;
 * bkz. D-123.)
 * </p>
 *
 * <p>
 * Hesabı açan taraf <b>vitrin backend'idir</b>: bağlantıya tıklanınca token
 * orada doğrulanır ve {@code SIMGE_USER} satırı orada oluşur. Panelin parolayla
 * hiçbir teması olmaz.
 * </p>
 *
 * <p>
 * ERP'ye hiçbir şey yazılmaz: davet edilen kişi Mikro'da görünmez, yalnızca
 * bizim tarafta o cariye bağlı bir kullanıcı oluşur.
 * </p>
 */
@Service
public class CompanyInviteService {

    private static final Logger log = LoggerFactory.getLogger(CompanyInviteService.class);

    /** Davet bağlantısının ömrü. Parola sıfırlamadan uzun: bu bir aciliyet değil. */
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final CompanyInvitationRepository invitationRepository;
    private final StorefrontUserRepository storefrontUserRepository;
    private final MailService mailService;
    private final MailTemplates mailTemplates;

    public CompanyInviteService(CompanyInvitationRepository invitationRepository,
            StorefrontUserRepository storefrontUserRepository,
            MailService mailService,
            MailTemplates mailTemplates) {
        this.invitationRepository = invitationRepository;
        this.storefrontUserRepository = storefrontUserRepository;
        this.mailService = mailService;
        this.mailTemplates = mailTemplates;
    }

    /**
     * Davet oluşturur ve gönderir.
     *
     * <p>
     * Sıklık sınırı <b>yok</b>: vitrindeki firma-içi davette sınır, bir firmanın
     * kendi kullanıcılarını bombalamasına karşıydı. Buradaki davet elle
     * onaylanan ve sayılı bir işlem.
     * </p>
     *
     * @param companyName e-posta metninde geçen firma unvanı; çağıran Mikro'dan
     *        okuyup verir. Bu servis ERP'ye hiç bakmaz — {@code appTransactionManager}
     *        ile çalışıyor ve buradan Mikro okumak, sınıf içi çağrıda proxy
     *        devreye girmediği için sessizce yanlış transaction'da olurdu.
     */
    @Transactional(transactionManager = "appTransactionManager")
    public CompanyInvitation invite(String cariKod, String companyName, Long staffId,
            String staffName, String email, String fullName, String phone) {

        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        if (storefrontUserRepository.existsByEmailIgnoreCase(normalized)) {
            // Kayıt akışının aksine burada söylenebilir: onaylayan kişi zaten
            // site yöneticisi ve kimi davet ettiğini biliyor; hesap sayma yolu değil.
            throw new EmailTakenException();
        }

        if (!mailService.isEnabled()) {
            throw new MailUnavailableException();
        }

        // Aynı adrese açık davet varsa iptal: aynı anda birden çok geçerli token
        // dolaşmasın, eskisi elinde kalan onu kullanamasın.
        invitationRepository.cancelOpenFor(normalized,
                CompanyInvitation.STATUS_PENDING, CompanyInvitation.STATUS_CANCELLED);

        String token = SecretCodes.urlToken();

        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setCariKod(cariKod);
        invitation.setInvitedBy(staffId);
        invitation.setInvitedByType(CompanyInvitation.INVITER_STAFF);
        invitation.setEmail(normalized);
        invitation.setFullName(fullName == null || fullName.isBlank() ? null : fullName.trim());
        // Basvurudan tasinan telefon (D-149): davet kabul ekrani ikinci kez sormasin.
        invitation.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        invitation.setTokenHash(SecretCodes.hash(token));
        invitation.setStatus(CompanyInvitation.STATUS_PENDING);
        invitation.setExpiresAt(Instant.now().plus(TOKEN_TTL));
        invitationRepository.save(invitation);

        Locale locale = LocaleContextHolder.getLocale();
        boolean sent = mailService.send(normalized,
                mailTemplates.invitationSubject(companyName, locale),
                mailTemplates.invitationBody(token, companyName, staffName,
                        (int) TOKEN_TTL.toDays(), locale));

        if (!sent) {
            // Ulaşmayan daveti geçerli bırakmanın anlamı yok; başvuru
            // beklemede kalsın ve tekrar denensin.
            invitation.setStatus(CompanyInvitation.STATUS_CANCELLED);
            invitationRepository.save(invitation);
            throw new MailUnavailableException();
        }

        log.info("Panelden davet gönderildi: invitationId={} cariKod={} personel={}",
                invitation.getId(), cariKod, staffId);
        return invitation;
    }

    /**
     * Davet gönderilebilir durumda mı? (ADR D-147)
     *
     * <p>
     * <b>ERP'ye yazmadan ÖNCE sorulmalı.</b> Onay akışı önce Mikro'ya cari yazıp
     * sonra daveti gönderiyor; iki ayrı veritabanı olduğu için ortak transaction
     * yok ve posta gidemeyince ERP'de öksüz bir cari kalıyor. Bu kontrol, en sık
     * karşılaşılan iki sebebi (SMTP hiç yapılandırılmamış / kimlik doğrulama
     * başarısız) ERP'ye dokunulmadan yakalıyor.
     * </p>
     */
    public boolean gonderilebilirMi() {
        return mailService.hazirMi();
    }

    /** Bu adresle zaten bir vitrin hesabı var. */
    public static class EmailTakenException extends RuntimeException {
    }

    /** SMTP yapılandırılmamış ya da gönderim başarısız. */
    public static class MailUnavailableException extends RuntimeException {
    }
}
