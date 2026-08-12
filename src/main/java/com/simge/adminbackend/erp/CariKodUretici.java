package com.simge.adminbackend.erp;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.erp.repository.CariHesaplarRepository;

/**
 * Yeni cari kodu <b>önerir</b> — dayatmaz (ADR D-127).
 *
 * <p>
 * Cari kodu, şemadan türetilemeyen tek alan: Mikro'da benzersiz olmak dışında
 * bir kuralı yok ve mevcut veride tek bir düzen de yok — {@code S-} (950 adet),
 * {@code M-} (878), harf önekli gruplar, hatta doğrudan vergi numarası
 * kullanılmış kayıtlar var. Hangi seriye açılacağı bir <b>iş kararı</b>;
 * kodun kendisini panelde insan onaylıyor.
 * </p>
 *
 * <p>
 * Buradaki öneri yalnızca yazım kolaylığı: seçilen önekteki en büyük numaranın
 * bir fazlası. Numarası olmayan kodlar ({@code M-TEST01} gibi) atlanır.
 * </p>
 *
 * <p>
 * <b>Öneri bir rezervasyon değil.</b> Kodu asıl garantiye alan şey Mikro'daki
 * benzersiz indeks: iki kişi aynı anda onaylarsa ikincisi
 * {@link CariWriter.CariKoduKullanimda} alır ve yeni öneriyle tekrar dener.
 * </p>
 */
@Service
public class CariKodUretici {

    private static final Pattern SON_NUMARA = Pattern.compile("^(.*?)(\\d+)$");

    private final CariHesaplarRepository cariRepository;
    private final String varsayilanOnek;

    public CariKodUretici(CariHesaplarRepository cariRepository,
            @Value("${simge.erp.cari-kod-onek:M-}") String varsayilanOnek) {
        this.cariRepository = cariRepository;
        this.varsayilanOnek = varsayilanOnek;
    }

    public String varsayilanOnek() {
        return varsayilanOnek;
    }

    /**
     * Önekteki bir sonraki boş kod.
     *
     * @param onek boş verilirse yapılandırmadaki varsayılan kullanılır
     */
    @Transactional(transactionManager = "mikroTransactionManager", readOnly = true)
    public String oner(String onek) {
        String p = (onek == null || onek.isBlank() ? varsayilanOnek : onek.trim())
                .toUpperCase(Locale.ROOT);

        List<String> kodlar = cariRepository.kodlariBul(p);

        int enBuyuk = 0;
        int basamak = 3;
        for (String kod : kodlar) {
            Matcher m = SON_NUMARA.matcher(kod.trim());
            if (!m.matches()) {
                continue;
            }
            String sayi = m.group(2);
            try {
                int deger = Integer.parseInt(sayi);
                if (deger > enBuyuk) {
                    enBuyuk = deger;
                    // Basamak sayısını en büyük koddan alıyoruz: seri M-001 diye
                    // gidiyorsa öneri de M-001 biçiminde olsun.
                    basamak = sayi.length();
                }
            } catch (NumberFormatException ignored) {
                // Numara int'e sığmıyorsa bu kod seriyi temsil etmiyor demektir.
            }
        }

        return p + String.format("%0" + basamak + "d", enBuyuk + 1);
    }

    /**
     * Bu vergi numarasıyla zaten cari var mı.
     *
     * <p>
     * Cari açmadan önceki son kontrol. Başvuru "cari yok" diye sınıflandırılmış
     * olabilir ama aradan geçen sürede biri Mikro'da elle açmış olabilir; o
     * durumda ikinci bir cari açmak mükerrer kayıt üretirdi.
     * </p>
     */
    @Transactional(transactionManager = "mikroTransactionManager", readOnly = true)
    public List<String> ayniVergiNoluKodlar(String vergiNo) {
        String no = vergiNo == null ? "" : vergiNo.replaceAll("[^0-9]", "");
        return no.isBlank() ? List.of() : cariRepository.kodlariVergiNoIle(no);
    }
}
