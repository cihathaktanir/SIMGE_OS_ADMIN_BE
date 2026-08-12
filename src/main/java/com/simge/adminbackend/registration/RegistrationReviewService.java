package com.simge.adminbackend.registration;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.CompanyInvitation;
import com.simge.adminbackend.appdb.model.RegistrationRequest;
import com.simge.adminbackend.appdb.repository.RegistrationRequestRepository;
import com.simge.adminbackend.erp.CariLookupService;
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
 * <b>ERP'ye hiçbir şey yazılmaz.</b> {@code NO_CARI} dalında cariyi Mikro'da
 * açmak <i>elle</i> yapılan bir iş; sistem yalnızca "hangi cariye bağlansın"
 * bilgisini alır. Bu yüzden onay ekranı cari kodunu sorar ve kodun Mikro'da
 * gerçekten var olduğunu doğrular — doğrulanmasaydı bir yazım hatası,
 * kullanıcıyı hiçbir firmaya (ya da daha kötüsü, yanlış bir firmaya) bağlı bir
 * hesapla baş başa bırakırdı.
 * </p>
 */
@Service
public class RegistrationReviewService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationReviewService.class);

    private final RegistrationRequestRepository requestRepository;
    private final CompanyInviteService inviteService;
    private final CariLookupService cariLookup;

    public RegistrationReviewService(RegistrationRequestRepository requestRepository,
            CompanyInviteService inviteService,
            CariLookupService cariLookup) {
        this.requestRepository = requestRepository;
        this.inviteService = inviteService;
        this.cariLookup = cariLookup;
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
     * Başvuruyu onaylar ve başvurana hesap kurma bağlantısı gönderir.
     *
     * @param cariKod bağlanacak Mikro carisi. {@code CARI_NO_EMAIL} dalında
     *        eşleşen cari zaten biliniyor ama yine de <b>insan onaylıyor</b>:
     *        aynı vergi numarasına birden çok şube kaydı düşebiliyor ve doğru
     *        olanı seçmek insan kararı.
     * @throws CariNotFoundException cari kodu Mikro'da yok ya da pasif
     */
    @Transactional(transactionManager = "appTransactionManager")
    public RegistrationRequest approve(StaffPrincipal staff, Long requestId, String cariKod,
            String note) {

        RegistrationRequest request = requestRepository.findById(requestId)
                .orElseThrow(RequestNotFoundException::new);

        if (!RegistrationRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new AlreadyReviewedException();
        }

        String kod = cariKod == null ? "" : cariKod.trim();
        if (kod.isBlank()) {
            throw new CariNotFoundException();
        }

        CariHesap cari = cariLookup.byKod(kod).orElseThrow(CariNotFoundException::new);

        CompanyInvitation invitation = inviteService.invite(
                kod, cari.getCariUnvan1(), staff.getId(), staff.getFullName(),
                request.getEmail(), request.getFullName());

        request.setStatus(RegistrationRequest.STATUS_APPROVED);
        request.setMatchedCariKod(kod);
        request.setReviewedBy(staff.getId());
        request.setReviewedAt(Instant.now());
        request.setReviewNote(trimToNull(note));
        requestRepository.save(request);

        log.info("Kayıt başvurusu onaylandı: requestId={} cariKod={} invitationId={} personel={}",
                requestId, kod, invitation.getId(), staff.getId());
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
}
