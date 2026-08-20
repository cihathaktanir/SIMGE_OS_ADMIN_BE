package com.simge.adminbackend.registration;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.CompanyInvitation;
import com.simge.adminbackend.appdb.model.RegistrationRequest;
import com.simge.adminbackend.appdb.repository.RegistrationRequestRepository;
import com.simge.adminbackend.erp.CariKodUretici;
import com.simge.adminbackend.erp.CariLookupService;
import com.simge.adminbackend.erp.CariWriter;
import com.simge.adminbackend.erp.model.CariHesap;
import com.simge.adminbackend.staff.StaffPrincipal;

/**
 * Kayıt başvurularının incelenmesi (ADR D-124; vitrinde D-121 idi).
 *
 * <p>
 * <b>Bu akış istisna değil, ana yol.</b> Ölçüm: 2.440 aktif carinin yalnızca
 * 252'sinde ({@literal ~%10}) e-posta adresi var. Kalan {@literal ~%90} kendi
 * kendine kayıt olamıyor ve buradan geçiyor.
 * </p>
 *
 * <p>
 * Onay ekranı cari kodunu sorar ve kodun Mikro'da gerçekten var olduğunu
 * doğrular — doğrulanmasaydı bir yazım hatası, kullanıcıyı hiçbir firmaya (ya
 * da daha kötüsü, yanlış bir firmaya) bağlı bir hesapla baş başa bırakırdı.
 * </p>
 *
 * <h2>ERP'ye yazma (D-127)</h2>
 * <p>
 * Bu servis artık Mikro'ya <b>yazabiliyor</b> ama yalnızca iki noktada, ikisi
 * de <b>personel onayıyla</b>:
 * </p>
 * <ul>
 *   <li>{@link #yeniCariAcarakOnayla} — {@code NO_CARI} dalında cariyi açar.
 *       Eskiden bu iş Mikro'da elle yapılıyordu.</li>
 *   <li>{@link #approve} — {@code CARI_NO_EMAIL} dalında, carinin <b>boş</b>
 *       e-posta alanını doldurur.</li>
 * </ul>
 * <p>
 * Otomatik yazma yok: hiçbir başvuru personel dokunmadan ERP'ye satır
 * yazdıramaz. Yazan tek sınıf {@link com.simge.adminbackend.erp.CariWriter}.
 * </p>
 */
@Service
public class RegistrationReviewService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationReviewService.class);

    private final RegistrationRequestRepository requestRepository;
    private final CompanyInviteService inviteService;
    private final CariLookupService cariLookup;
    private final CariWriter cariWriter;
    private final CariKodUretici kodUretici;

    public RegistrationReviewService(RegistrationRequestRepository requestRepository,
            CompanyInviteService inviteService,
            CariLookupService cariLookup,
            CariWriter cariWriter,
            CariKodUretici kodUretici) {
        this.requestRepository = requestRepository;
        this.inviteService = inviteService;
        this.cariLookup = cariLookup;
        this.cariWriter = cariWriter;
        this.kodUretici = kodUretici;
    }

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public List<RegistrationRequest> byStatus(String status) {
        return requestRepository.findByStatusOrderByIdDesc(status);
    }

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public long pendingCount() {
        return requestRepository.countByStatus(RegistrationRequest.STATUS_PENDING);
    }

    /**
     * Başvuruyu <b>var olan</b> bir cariye bağlayarak onaylar ve başvurana hesap
     * kurma bağlantısı gönderir.
     *
     * @param cariKod bağlanacak Mikro carisi. {@code CARI_NO_EMAIL} dalında
     *        eşleşen cari zaten biliniyor ama yine de <b>insan onaylıyor</b>:
     *        aynı vergi numarasına birden çok şube kaydı düşebiliyor ve doğru
     *        olanı seçmek insan kararı.
     * @param erpEposta doluysa carinin <b>boş</b> e-posta alanına yazılır
     *        (D-127). Boş bırakılırsa ERP'ye dokunulmaz — "sadece hesabı bağla,
     *        ERP'yi ben güncellerim" demenin yolu bu. Dolu bir adresin üzerine
     *        hiçbir durumda yazılmaz; bunu {@link CariWriter} garantiliyor.
     * @throws CariNotFoundException cari kodu Mikro'da yok ya da pasif
     */
    @Transactional(transactionManager = "appTransactionManager")
    public RegistrationRequest approve(StaffPrincipal staff, Long requestId, String cariKod,
            String erpEposta, String note) {

        RegistrationRequest request = bekleyenBasvuru(requestId);

        String kod = cariKod == null ? "" : cariKod.trim();
        if (kod.isBlank()) {
            throw new CariNotFoundException();
        }

        CariHesap cari = cariLookup.byKod(kod).orElseThrow(CariNotFoundException::new);

        // POSTA ÖNCE SINANIR (D-147): aşağıdaki ERP yazması geri alınamıyor.
        postaHazirDegilseDur();

        // ERP yazması davetten ÖNCE: e-posta yazılamıyorsa davet de gitmesin.
        // Tersi sırada, davet gitmiş ama ERP güncellenmemiş bir ara durum kalırdı
        // ve bunu kimse fark etmezdi.
        String yazilacak = trimToNull(erpEposta);
        boolean epostaYazildi = false;
        if (yazilacak != null) {
            epostaYazildi = cariWriter.epostaYaz(kod, yazilacak.toLowerCase(Locale.ROOT));
            if (!epostaYazildi) {
                throw new EpostaYazilamadi(kod);
            }
        }

        CompanyInvitation invitation = inviteService.invite(
                kod, cari.getCariUnvan1(), staff.getId(), staff.getFullName(),
                request.getEmail(), request.getFullName(), request.getPhone());

        request.setStatus(RegistrationRequest.STATUS_APPROVED);
        request.setMatchedCariKod(kod);
        request.setReviewedBy(staff.getId());
        request.setReviewedAt(Instant.now());
        request.setReviewNote(trimToNull(note));
        requestRepository.save(request);

        log.info("Kayıt başvurusu onaylandı: requestId={} cariKod={} erpEposta={} "
                + "invitationId={} personel={}",
                requestId, kod, epostaYazildi, invitation.getId(), staff.getId());
        return request;
    }

    /**
     * {@code NO_CARI} dalı: Mikro'da <b>yeni cari açar</b> ve başvuruyu ona
     * bağlayarak onaylar (D-127).
     *
     * <p>
     * Cari kodunu personel veriyor. Önerilebiliyor ({@link CariKodUretici}) ama
     * dayatılmıyor: hangi seriye açılacağı bir iş kararı ve mevcut veride tek
     * bir düzen yok.
     * </p>
     *
     * <p>
     * Vergi numarası kontrolü burada tekrarlanıyor: başvuru "cari yok" diye
     * sınıflandırıldığından beri biri Mikro'da elle açmış olabilir. O durumda
     * ikinci bir cari açmak mükerrer kayıt üretirdi; personel uyarılıp var olan
     * cariye bağlamaya yönlendiriliyor.
     * </p>
     *
     * @throws CariZatenVar aynı vergi numarasıyla aktif cari bulundu
     * @throws CariWriter.CariKoduKullanimda kod başka bir caride kullanılıyor
     */
    @Transactional(transactionManager = "appTransactionManager")
    public RegistrationRequest yeniCariAcarakOnayla(StaffPrincipal staff, Long requestId,
            CariWriter.YeniCari veri, String note) {

        RegistrationRequest request = bekleyenBasvuru(requestId);

        List<String> mevcut = kodUretici.ayniVergiNoluKodlar(veri.vergiNo());
        if (!mevcut.isEmpty()) {
            throw new CariZatenVar(mevcut);
        }

        // POSTA ÖNCE SINANIR (D-147). Aşağıdaki cari açma işlemi Mikro'da
        // COMMIT'leniyor ve buradaki transaction onu geri alamıyor; posta
        // gidemeyecekse ERP'ye hiç dokunmamak gerekiyor.
        postaHazirDegilseDur();

        // DİKKAT — iki ayrı veritabanı, tek işlem YOK.
        // Bu metot appTransactionManager'da; cariWriter ise Mikro'nun kendi
        // transaction'ında yazıyor ve dönüşte COMMIT'lenmiş oluyor. Buradan
        // sonrası patlarsa (örneğin davet e-postası gönderilemezse) başvuru
        // PENDING'de kalır ama cari Mikro'da açılmış olur. Bu sessiz bir hata
        // değil: ikinci denemede aynı vergi numarası bulunur ve personel
        // CariZatenVar ile uyarılıp "var olan cariye bağla" yoluna yönlendirilir.
        // Dağıtık işlem (XA) kurmak, tek bir onay ekranı için ödenecek bedelden
        // çok daha pahalı olurdu.
        long recNo = cariWriter.yeniCari(veri);

        CompanyInvitation invitation = inviteService.invite(
                veri.cariKod(), veri.unvan(), staff.getId(), staff.getFullName(),
                request.getEmail(), request.getFullName(), request.getPhone());

        request.setStatus(RegistrationRequest.STATUS_APPROVED);
        request.setMatchedCariKod(veri.cariKod());
        request.setCreatedCariKod(veri.cariKod());
        request.setReviewedBy(staff.getId());
        request.setReviewedAt(Instant.now());
        request.setReviewNote(trimToNull(note));
        requestRepository.save(request);

        log.info("Mikro'da cari açılarak başvuru onaylandı: requestId={} cariKod={} "
                + "recNo={} invitationId={} personel={}",
                requestId, veri.cariKod(), recNo, invitation.getId(), staff.getId());
        return request;
    }

    /**
     * Posta gönderilemeyecekse ERP'ye dokunmadan durur (ADR D-147).
     *
     * <p>
     * Kullanıcının bildirdiği durum: SMTP hiç yapılandırılmamışken bir başvuru
     * onaylandı, Mikro'da cari açıldı, davet gönderilemedi ve
     * {@code MailUnavailableException} bu servisin transaction'ını geri sardı —
     * ama Mikro'daki yazma <b>başka bir veritabanında</b> ve çoktan
     * COMMIT'lenmişti. Sonuç: başvuru PENDING, cari ortada.
     * </p>
     *
     * <p>
     * Fırlatılan istisna gönderim anındakiyle <b>aynı</b>; panel zaten
     * 503 / "e-posta gönderimi yapılandırılmamış" gösteriyor, mesaj değişmiyor.
     * Değişen tek şey: artık ERP'ye yazılmadan önce başarısız oluyor.
     * </p>
     */
    private void postaHazirDegilseDur() {
        if (!inviteService.gonderilebilirMi()) {
            log.warn("Onay durduruldu: e-posta gönderilemiyor, ERP'ye yazılmadı");
            throw new CompanyInviteService.MailUnavailableException();
        }
    }

    private RegistrationRequest bekleyenBasvuru(Long requestId) {
        RegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(RequestNotFoundException::new);
        if (!RegistrationRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new AlreadyReviewedException();
        }
        return request;
    }

    /**
     * Başvuruyu reddeder.
     *
     * <p>
     * Başvurana <b>otomatik bildirim gitmez</b>. Ret sebepleri çoğu zaman "sizi
     * tanımıyoruz" ya da "yetkiniz olduğunu doğrulayamadık" gibi konuşarak
     * çözülecek şeyler; kalıp bir ret e-postası bunu kapatır. Not alanı, arayan
     * kişiye ne söyleneceğini hatırlamak için var.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public RegistrationRequest reject(StaffPrincipal staff, Long requestId, String note) {
        RegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(RequestNotFoundException::new);

        if (!RegistrationRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new AlreadyReviewedException();
        }

        request.setStatus(RegistrationRequest.STATUS_REJECTED);
        request.setReviewedBy(staff.getId());
        request.setReviewedAt(Instant.now());
        request.setReviewNote(trimToNull(note));
        requestRepository.save(request);

        log.info("Kayıt başvurusu reddedildi: requestId={} personel={}", requestId, staff.getId());
        return request;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static class RequestNotFoundException extends RuntimeException {
    }

    /** Başvuru daha önce onaylanmış ya da reddedilmiş — iki kez işlenmesin. */
    public static class AlreadyReviewedException extends RuntimeException {
    }

    /** Verilen cari kodu Mikro'da bulunamadı. */
    public static class CariNotFoundException extends RuntimeException {
    }

    /**
     * Carinin e-posta alanı yazılamadı — ya kod bulunamadı ya da adres zaten
     * dolu. Dolu adresin üzerine yazmak kasıtlı olarak engelli (D-127): Mikro'yu
     * doğru kabul eden fatura ve mutabakat akışlarını sessizce bozardı.
     */
    public static class EpostaYazilamadi extends RuntimeException {
        private final String cariKod;

        public EpostaYazilamadi(String cariKod) {
            super("Cari e-postası yazılamadı: " + cariKod);
            this.cariKod = cariKod;
        }

        public String getCariKod() {
            return cariKod;
        }
    }

    /**
     * Aynı vergi numarasıyla Mikro'da zaten cari var — yenisini açmak mükerrer
     * kayıt üretirdi.
     */
    public static class CariZatenVar extends RuntimeException {
        private final List<String> kodlar;

        public CariZatenVar(List<String> kodlar) {
            super("Bu vergi numarasıyla cari zaten var: " + String.join(", ", kodlar));
            this.kodlar = List.copyOf(kodlar);
        }

        public List<String> getKodlar() {
            return kodlar;
        }
    }
}
