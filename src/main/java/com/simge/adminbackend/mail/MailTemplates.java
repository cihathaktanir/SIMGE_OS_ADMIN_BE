package com.simge.adminbackend.mail;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Panelin gönderdiği e-posta gövdeleri.
 *
 * <p>
 * Panel <b>tek tür</b> e-posta gönderir: onaylanan kayıt başvurusu için hesap
 * kurma daveti. Doğrulama kodu, parola sıfırlama gibi akışlar vitrinde kalıyor;
 * onların şablonları da orada. Bu yüzden vitrindeki {@code MailTemplates}'in
 * tamamı kopyalanmadı — kullanılmayan şablon, güncellenmeyen şablondur.
 * </p>
 *
 * <p>
 * <b>Bağlantı vitrini gösterir</b> ({@code simge.site-url}), paneli değil:
 * daveti kabul eden kişi bir müşteri ve hesabını vitrinde açıyor. Panel
 * intranette; oraya işaret eden bir bağlantı kimsenin açamayacağı bir adres
 * olurdu.
 * </p>
 *
 * <p>
 * Metin ile biçim ayrı: cümleler {@code messages*.properties} dosyalarında,
 * HTML iskeleti burada. Kullanıcıdan gelen her değer
 * {@link HtmlUtils#htmlEscape} ile kaçırılır.
 * </p>
 */
@Component
public class MailTemplates {

    private final MessageSource messages;
    private final String brandName;
    private final String siteUrl;

    public MailTemplates(MessageSource messages,
            @Value("${simge.mail.from-name:Simge Online Selling}") String brandName,
            @Value("${simge.site-url:http://localhost:4200}") String siteUrl) {
        this.messages = messages;
        this.brandName = brandName;
        this.siteUrl = stripTrailingSlash(siteUrl);
    }

    private String t(String key, Locale locale, Object... args) {
        return messages.getMessage(key, args, locale);
    }

    /** Çeviri metnini HTML'e koymadan önce kaçır. */
    private String esc(String key, Locale locale, Object... args) {
        return HtmlUtils.htmlEscape(t(key, locale, args));
    }

    public String invitationSubject(String companyName, Locale locale) {
        return t("mail.invite.subject", locale, companyName);
    }

    /**
     * Hesap kurma daveti.
     *
     * <p>
     * Firma adı ve davet edenin adı kullanıcı verisi; {@link #esc} çeviriyi
     * argümanlarla birleştirdikten <b>sonra</b> kaçırdığı için argümanlar buraya
     * ham geçiliyor — önceden kaçırmak çift kaçışa yol açardı ("Öz &amp;amp;
     * Kardeşler" gibi).
     * </p>
     */
    public String invitationBody(String token, String companyName, String inviterName,
            int validDays, Locale locale) {
        String link = siteUrl + "/invite?token=" + token;
        String safeLink = HtmlUtils.htmlEscape(link);

        String content = """
                <p style="margin:0 0 16px">%s</p>
                <p style="margin:0 0 24px">%s</p>
                <p style="margin:0 0 24px;text-align:center">
                  <a href="%s" style="display:inline-block;padding:13px 28px;
                     background:#1b1464;color:#ffffff;text-decoration:none;
                     border-radius:10px;font-weight:500">%s</a>
                </p>
                <p style="margin:0 0 24px;color:#6b7280;font-size:13px;
                          word-break:break-all">%s<br>%s</p>
                <p style="margin:0 0 8px;color:#6b7280;font-size:13px">%s</p>
                <p style="margin:0;color:#6b7280;font-size:13px">%s</p>
                """.formatted(
                esc("mail.greeting", locale),
                esc("mail.invite.intro", locale,
                        nullSafe(inviterName), nullSafe(companyName), brandName),
                safeLink,
                esc("mail.invite.button", locale),
                esc("mail.invite.fallback", locale),
                safeLink,
                esc("mail.invite.validity", locale, validDays),
                esc("mail.invite.ignore", locale));

        return wrap(t("mail.invite.heading", locale), content, locale);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /** Ortak dış çerçeve — e-posta istemcileri için tablo tabanlı, satır içi stilli. */
    private String wrap(String heading, String content, Locale locale) {
        return """
                <!doctype html>
                <html lang="%s"><body style="margin:0;padding:0;background:#f4f5f8">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="background:#f4f5f8;padding:32px 12px">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                           style="max-width:520px;background:#ffffff;border-radius:16px;
                                  overflow:hidden;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif">
                      <tr><td style="background:#1b1464;padding:24px 28px">
                        <div style="color:#ffffff;font-size:19px;font-weight:600">%s</div>
                        <div style="color:rgba(255,255,255,.65);font-size:13px;margin-top:3px">%s</div>
                      </td></tr>
                      <tr><td style="padding:28px;color:#1c1c1c;font-size:14.5px;line-height:1.65">
                        %s
                      </td></tr>
                      <tr><td style="padding:16px 28px 24px;border-top:1px solid #eceef2;
                                     color:#9ca3af;font-size:12px">
                        %s
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body></html>
                """.formatted(
                HtmlUtils.htmlEscape(locale.getLanguage()),
                HtmlUtils.htmlEscape(brandName),
                HtmlUtils.htmlEscape(heading),
                content,
                esc("mail.footer", locale, brandName));
    }

    private String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
