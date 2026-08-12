package com.simge.adminbackend.mail;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

/**
 * E-posta gönderimi.
 *
 * <p>
 * <b>Sağlayıcıya bağlı değildir.</b> Gmail, Brevo, SES — hepsi düz SMTP; hangisi
 * kullanılacağı yalnızca {@code SIMGE_MAIL_*} ortam değişkenleriyle belirlenir,
 * kodda hiçbir sağlayıcı adı geçmez. Sağlayıcı değiştirmek kod değişikliği
 * gerektirmez.
 * </p>
 *
 * <p>
 * <b>Kimlik bilgisi yapılandırmada tutulmaz.</b> Kullanıcı adı/parola boş
 * bırakıldığında gönderim kapanır ve e-postanın içeriği log'a yazılır; böylece
 * geliştirme ve testler SMTP hesabı olmadan da çalışır. Kapalıyken sessizce
 * "gönderdim" demez — çağıran {@link #isEnabled()} ile durumu bilebilir.
 * </p>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String fromName;
    private final String replyTo;
    private final boolean enabled;

    public MailService(JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String username,
            @Value("${simge.mail.from:}") String from,
            @Value("${simge.mail.from-name:Simge Online Selling}") String fromName,
            @Value("${simge.mail.reply-to:}") String replyTo,
            @Value("${simge.mail.enabled:true}") boolean configuredEnabled) {
        this.mailSender = mailSender;
        // Gönderen adresi ayrıca verilmediyse SMTP kullanıcısının kendisi.
        // Gmail zaten başka bir gönderen adresine izin vermez.
        this.from = (from == null || from.isBlank()) ? username : from;
        this.fromName = fromName;
        // Ayrıca verilmediyse gönderen adresin kendisi.
        this.replyTo = (replyTo == null || replyTo.isBlank()) ? this.from : replyTo;
        this.enabled = configuredEnabled && username != null && !username.isBlank();

        if (!this.enabled) {
            log.warn("E-posta gönderimi KAPALI (SIMGE_MAIL_USERNAME tanımlı değil). "
                    + "Gönderilecek iletiler log'a yazılacak, kimseye ulaşmayacak.");
        }
    }

    /** SMTP yapılandırılmış mı — akışlar buna göre dallanır (örn. admin kuyruğuna düşürme). */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * HTML gövdeli ileti gönderir.
     *
     * @return gerçekten gönderildiyse {@code true}; kapalıysa ya da gönderim
     *         başarısızsa {@code false}. Çağıran, kullanıcıya e-posta gittiğini
     *         iddia etmeden önce buna bakmalıdır.
     */
    public boolean send(String to, String subject, String htmlBody) {
        if (!enabled) {
            log.info("""
                    [E-POSTA KAPALI — gönderilmedi]
                    Alıcı : {}
                    Konu  : {}
                    ----- gövde -----
                    {}
                    -----------------""", to, subject, htmlToText(htmlBody));
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // İleti hem düz metin hem HTML olarak gidiyor (multipart/alternative).
            // Yalnızca HTML gönderen iletiler spam puanı topluyor; ayrıca metin
            // okuyucu kullanan ya da HTML'i kapatan istemcilerde ileti boş görünmüyor.
            //
            // MULTIPART_MODE_RELATED, varsayılan MIXED_RELATED'in eklediği
            // multipart/mixed katmanını atlıyor: ek dosyamız yok, o sarmalayıcı
            // boşuna. RELATED seçildi ki ileride gövdeye gömülü logo eklenebilsin.
            MimeMessageHelper helper = new MimeMessageHelper(message,
                    MimeMessageHelper.MULTIPART_MODE_RELATED, StandardCharsets.UTF_8.name());
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlToText(htmlBody), htmlBody);

            // Yanıtlar gönderen kutusuna düşsün; yanıtlanamayan bir adres
            // (noreply@...) spam filtreleri için olumsuz bir sinyal.
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            mailSender.send(message);
            log.info("E-posta gönderildi: konu='{}'", subject);
            return true;

        } catch (Exception e) {
            // Alıcı adresi log'a yazılmaz: kimin hesabı olduğu bilgisi sızmasın.
            log.error("E-posta gönderilemedi: konu='{}'", subject, e);
            return false;
        }
    }

    /**
     * HTML gövdeden düz metin sürümü üretir.
     *
     * <p>
     * Bu metin iletinin gerçek {@code text/plain} parçası olarak gidiyor (ve
     * gönderim kapalıyken log'a yazılıyor), yani kullanıcının okuyabileceği bir
     * şey olmalı. Şablonlar bunu kolaylaştıracak şekilde yazıldı: hem doğrulama
     * kodu hem sıfırlama bağlantısı HTML'de <b>görünür metin olarak da</b>
     * geçiyor, dolayısıyla etiketler atılınca kaybolmuyorlar.
     * </p>
     *
     * <p>
     * Tam bir HTML ayrıştırıcı değil ve olması da gerekmiyor — girdi bizim
     * kontrolümüzdeki iki sabit şablon. Rastgele HTML'e uygulanacaksa
     * kütüphane kullanılmalı.
     * </p>
     */
    private String htmlToText(String html) {
        return html
                // <head>/<style> içeriği metinde görünmesin.
                .replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h[1-6]|li)>", "\n")
                .replaceAll("<[^>]+>", "")
                // Etiketler gidince kalan HTML varlıkları çözülmeli, yoksa
                // kullanıcı "&amp;" gibi şeyler okur.
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&amp;", "&")   // en sonda: yeni varlık üretmesin
                // Girintiden gelen boşluklar satır başlarında birikiyor.
                .replaceAll("(?m)^[ \\t]+", "")
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
