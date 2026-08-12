package com.simge.adminbackend.erp;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.erp.model.CariHesap;
import com.simge.adminbackend.erp.repository.CariHesaplarRepository;

/**
 * Mikro'dan cari okuma — <b>ayrı bean olması zorunlu</b>.
 *
 * <p>
 * Çağıranların çoğu {@code appTransactionManager} ile çalışıyor. Mikro okumasını
 * o sınıfların içine yazsaydık, {@code @Transactional(transactionManager =
 * "mikroTransactionManager")} anotasyonu <b>sınıf içi çağrıda proxy devreye
 * girmediği için sessizce etkisiz</b> kalırdı: sorgu yanlış transaction'da
 * çalışır, hata vermez, yalnızca beklenmedik davranır. Aynı tuzağa vitrin
 * backend'inde bir kez düşülmüştü.
 * </p>
 */
@Service
public class CariLookupService {

    /** Tek harfle arama binlerce satır tarar ve işe yaramaz. */
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_RESULTS = 20;

    private final CariHesaplarRepository cariRepository;

    public CariLookupService(CariHesaplarRepository cariRepository) {
        this.cariRepository = cariRepository;
    }

    @Transactional(transactionManager = "mikroTransactionManager", readOnly = true)
    public List<CariHesap> search(String query) {
        String term = query == null ? "" : query.trim();
        if (term.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }
        return cariRepository.search(term, PageRequest.of(0, MAX_RESULTS));
    }

    @Transactional(transactionManager = "mikroTransactionManager", readOnly = true)
    public Optional<CariHesap> byKod(String cariKod) {
        String kod = cariKod == null ? "" : cariKod.trim();
        return kod.isBlank() ? Optional.empty() : cariRepository.findActiveByCariKod(kod);
    }

    /** E-posta metninde geçen firma unvanı; Mikro tek doğru kaynak (D-100). */
    @Transactional(transactionManager = "mikroTransactionManager", readOnly = true)
    public String unvan(String cariKod) {
        return byKod(cariKod).map(CariHesap::getCariUnvan1).orElse("");
    }
}
