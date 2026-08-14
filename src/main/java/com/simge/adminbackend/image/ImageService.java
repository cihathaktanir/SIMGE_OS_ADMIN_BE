package com.simge.adminbackend.image;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.ImageBlob;
import com.simge.adminbackend.appdb.model.ImageLink;
import com.simge.adminbackend.appdb.repository.ImageBlobRepository;
import com.simge.adminbackend.appdb.repository.ImageLinkRepository;

/**
 * Görsel yükleme ve bağlama (ADR D-142).
 *
 * <p>
 * İş akışı: baytları işle (küçült, iki türev üret, SHA-256 al) → aynı hash
 * zaten varsa <b>tekrar saklama</b>, yalnızca bağ kur.
 * </p>
 *
 * <p>
 * Bu servis <b>Mikro'ya dokunmuyor</b>. Görseller bize ait; ERP'de böyle bir
 * alan yok (D-100). Ürünün var olup olmadığı çağıran tarafta denetleniyor.
 * </p>
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private final ImageProcessor processor;
    private final ImageBlobRepository blobRepository;
    private final ImageLinkRepository linkRepository;

    public ImageService(ImageProcessor processor,
            ImageBlobRepository blobRepository,
            ImageLinkRepository linkRepository) {
        this.processor = processor;
        this.blobRepository = blobRepository;
        this.linkRepository = linkRepository;
    }

    /** Bir yüklemenin sonucu; panel bunu operatöre gösteriyor. */
    public record Yukleme(
            String ownerKey,
            String contentHash,
            int sortOrder,
            int sourceBytes,
            int storedBytes,
            boolean yeniBayt) {
    }

    /**
     * Tek bir görseli yükler ve sahibine bağlar.
     *
     * @param birincilYap true ise görsel sıra 0'a alınır (karoda gösterilen);
     *        false ise galerinin sonuna eklenir
     */
    @Transactional(transactionManager = "appTransactionManager")
    public Yukleme yukle(String ownerType, String ownerKey, byte[] kaynak,
            String dosyaAdi, Long staffId, boolean birincilYap) throws IOException {

        String anahtar = normalize(ownerKey);
        ImageProcessor.Sonuc sonuc = processor.isle(kaynak);

        // Baytlar yalnızca ilk kez saklanıyor. Aynı fotoğraf başka bir ürüne
        // de yüklendiyse burada zaten var; içerik adresli olmanın karşılığı bu.
        boolean yeniBayt = !blobRepository.existsById(sonuc.contentHash());
        if (yeniBayt) {
            ImageBlob blob = new ImageBlob();
            blob.setContentHash(sonuc.contentHash());
            blob.setFormat(ImageBlob.FORMAT_JPG);
            blob.setThumbBytes(sonuc.thumbBytes());
            blob.setThumbWidth(sonuc.thumbWidth());
            blob.setThumbHeight(sonuc.thumbHeight());
            blob.setDetailBytes(sonuc.detailBytes());
            blob.setDetailWidth(sonuc.detailWidth());
            blob.setDetailHeight(sonuc.detailHeight());
            blob.setByteSize(sonuc.byteSize());
            blob.setCreatedAt(Instant.now());
            blobRepository.save(blob);
        }

        // Aynı görsel aynı ürüne ikinci kez yükleniyorsa yeni satır açmıyoruz;
        // operatör aynı dosyayı yanlışlıkla tekrar sürüklediğinde galeride
        // kopya çıkmasın.
        ImageLink link = linkRepository
                .findFirstByOwnerTypeAndOwnerKeyAndContentHash(ownerType, anahtar, sonuc.contentHash())
                .orElseGet(ImageLink::new);

        boolean yeniBag = link.getId() == null;
        if (yeniBag) {
            link.setOwnerType(ownerType);
            link.setOwnerKey(anahtar);
            link.setContentHash(sonuc.contentHash());
            link.setSortOrder(linkRepository.enBuyukSira(ownerType, anahtar) + 1);
            link.setCreatedAt(Instant.now());
        }
        link.setSourceName(kirp(dosyaAdi, 255));
        link.setSourceBytes(kaynak.length);
        link.setCreatedBy(staffId);
        linkRepository.save(link);

        if (birincilYap) {
            birincilYap(ownerType, anahtar, link.getId());
        }

        log.info("Görsel yüklendi: {} {} hash={} {} -> {} bayt (yeni={})",
                ownerType, anahtar, sonuc.contentHash().substring(0, 12),
                kaynak.length, sonuc.byteSize(), yeniBayt);

        return new Yukleme(anahtar, sonuc.contentHash(), link.getSortOrder(),
                kaynak.length, sonuc.byteSize(), yeniBayt);
    }

    /**
     * Bir görseli birincil yapar (sıra 0).
     *
     * <p>
     * Diğerleri 1'den başlayarak yeniden numaralanıyor. Sıralar arasında boşluk
     * bırakmak yerine baştan yazmak, listede en fazla birkaç görsel olduğu için
     * daha basit ve "iki tane 0" durumunu imkânsız kılıyor.
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public boolean birincilYap(String ownerType, String ownerKey, Long linkId) {
        String anahtar = normalize(ownerKey);
        List<ImageLink> hepsi =
                linkRepository.findByOwnerTypeAndOwnerKeyOrderBySortOrderAsc(ownerType, anahtar);

        boolean bulundu = hepsi.stream().anyMatch(l -> l.getId().equals(linkId));
        if (!bulundu) {
            return false;
        }

        int sira = 1;
        for (ImageLink l : hepsi) {
            l.setSortOrder(l.getId().equals(linkId) ? ImageLink.BIRINCIL : sira++);
        }
        linkRepository.saveAll(hepsi);
        return true;
    }

    /**
     * Bağı kaldırır.
     *
     * <p>
     * <b>Baytlar silinmiyor</b>: aynı hash başka bir ürüne de bağlı olabilir.
     * Öksüz kalan baytların temizliği ayrı bir bakım işi (V17 sonundaki not).
     * </p>
     */
    @Transactional(transactionManager = "appTransactionManager")
    public boolean bagiKaldir(String ownerType, String ownerKey, Long linkId) {
        String anahtar = normalize(ownerKey);
        return linkRepository.findById(linkId)
                .filter(l -> l.getOwnerType().equals(ownerType) && l.getOwnerKey().equals(anahtar))
                .map(l -> {
                    linkRepository.delete(l);
                    // Silinen birincilse, kalanların ilki birincil olsun; ürün
                    // görselsiz kalmasın diye.
                    List<ImageLink> kalan = linkRepository
                            .findByOwnerTypeAndOwnerKeyOrderBySortOrderAsc(ownerType, anahtar);
                    if (!kalan.isEmpty() && kalan.get(0).getSortOrder() != ImageLink.BIRINCIL) {
                        kalan.get(0).setSortOrder(ImageLink.BIRINCIL);
                        linkRepository.save(kalan.get(0));
                    }
                    return true;
                })
                .orElse(false);
    }

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public List<ImageLink> listele(String ownerType, String ownerKey) {
        return linkRepository.findByOwnerTypeAndOwnerKeyOrderBySortOrderAsc(
                ownerType, normalize(ownerKey));
    }

    /** Hangi SKU'ların görseli var — arama sonucunu işaretlemek için. */
    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public List<String> gorseliOlanlar(String ownerType, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> normalize = new ArrayList<>(keys.size());
        for (String k : keys) {
            normalize.add(normalize(k));
        }
        return linkRepository.gorseliOlanlar(ownerType, normalize);
    }

    @Transactional(transactionManager = "appTransactionManager", readOnly = true)
    public Map<String, Object> ozet() {
        Map<String, Object> ozet = new LinkedHashMap<>();
        ozet.put("urun_gorseli", linkRepository.countByOwnerType(ImageLink.OWNER_PRODUCT));
        ozet.put("kategori_gorseli", linkRepository.countByOwnerType(ImageLink.OWNER_CATEGORY));
        ozet.put("saklanan_bayt", blobRepository.count());
        return ozet;
    }

    /**
     * Anahtarı normalleştirir.
     *
     * <p>
     * Mikro kodlarında baştaki/sondaki boşluk sık; toplu yüklemede dosya adı
     * da boşluklu gelebiliyor. Büyük harfe çevrilmiyor — Mikro'daki kod
     * büyük/küçük harf duyarlı olabilir ve uydurmak yerine olduğu gibi
     * bırakmak doğru.
     * </p>
     */
    private String normalize(String key) {
        return key == null ? "" : key.trim();
    }

    private String kirp(String metin, int enFazla) {
        if (metin == null) {
            return null;
        }
        String t = metin.trim();
        return t.length() <= enFazla ? t : t.substring(0, enFazla);
    }

    /** Dosya adından SKU çıkarır: "ABC-123.jpg" -> "ABC-123". */
    public static String dosyaAdindanKod(String dosyaAdi) {
        if (dosyaAdi == null || dosyaAdi.isBlank()) {
            return "";
        }
        // Tarayıcı bazı durumlarda tam yol gönderiyor.
        String ad = dosyaAdi.replace('\\', '/');
        int egik = ad.lastIndexOf('/');
        if (egik >= 0) {
            ad = ad.substring(egik + 1);
        }
        int nokta = ad.lastIndexOf('.');
        if (nokta > 0) {
            ad = ad.substring(0, nokta);
        }
        // "ABC123 (1).jpg" ve "ABC123-2.jpg" gibi ikinci/üçüncü fotoğraf
        // adlandırmaları yaygın; sondaki sıra ekini atıyoruz.
        ad = ad.replaceAll("\\s*\\(\\d+\\)$", "");
        ad = ad.replaceAll("[-_]\\d{1,2}$", "");
        return ad.trim();
    }

    /** Dosya adı büyük/küçük harf farkıyla eşleşsin diye karşılaştırma anahtarı. */
    public static String eslesmeAnahtari(String kod) {
        return kod == null ? "" : kod.trim().toLowerCase(Locale.ROOT);
    }
}
