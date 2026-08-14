package com.simge.adminbackend.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import com.simge.adminbackend.appdb.model.ImageLink;
import com.simge.adminbackend.appdb.repository.ImageBlobRepository;

/**
 * {@link ImageService}'i <b>gerçek şemada</b> çalıştırır ve geri alır (ADR D-142).
 *
 * <p>
 * Birim testi ({@link ImageProcessorTest}) baytların doğru üretildiğini
 * gösteriyor ama sütun tiplerine hiç dokunmuyor. {@code VARBINARY(MAX)}
 * eşlemesi, hash sütununun uzunluğu ve yabancı anahtar ancak burada denenir —
 * nitekim ilk denemede {@code CHAR(64)} yüzünden uygulama açılışta durmuştu.
 * </p>
 *
 * <p>
 * <b>Kalıcı hiçbir şey yazmaz</b> (test {@code @Transactional}'ı geri alır).
 * Varsayılan olarak kapalı — canlı veritabanı ve parola ister:
 * </p>
 *
 * <pre>
 * $env:SIMGE_APP_DB_PASSWORD = '...'
 * .\mvnw.cmd test -Dsimge.erp.canli-test=true -Dtest=ImageServiceCanliTest
 * </pre>
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "simge.erp.canli-test", matches = "true")
class ImageServiceCanliTest {

    /** Mikro'da gerçekten var olan bir stok kodu. */
    private static final String DENEME_SKU = "A.TATLICI-30344";

    @Autowired
    private ImageService imageService;

    @Autowired
    private ImageBlobRepository blobRepository;

    @Test
    @Transactional(transactionManager = "appTransactionManager")
    @Rollback
    @DisplayName("Yükleme baytları saklıyor, bağı kuruyor ve aynı dosyayı ikinci kez saklamıyor")
    void yuklemeVeTekrarlamama() throws IOException {
        byte[] foto = jpegUret(2000, 1500, 1);

        ImageService.Yukleme ilk = imageService.yukle(
                ImageLink.OWNER_PRODUCT, DENEME_SKU, foto, "A.TATLICI-30344.jpg", null, false);

        assertThat(ilk.contentHash()).hasSize(64);
        assertThat(ilk.yeniBayt()).isTrue();
        assertThat(ilk.storedBytes()).isLessThan(ilk.sourceBytes());
        assertThat(blobRepository.existsById(ilk.contentHash())).isTrue();

        List<ImageLink> baglar = imageService.listele(ImageLink.OWNER_PRODUCT, DENEME_SKU);
        assertThat(baglar).extracting(ImageLink::getContentHash).contains(ilk.contentHash());

        // Aynı dosya ikinci kez: baytlar tekrar saklanmamalı, bağ da
        // kopyalanmamalı.
        int oncekiBagSayisi = baglar.size();
        ImageService.Yukleme ikinci = imageService.yukle(
                ImageLink.OWNER_PRODUCT, DENEME_SKU, foto, "kopya.jpg", null, false);

        assertThat(ikinci.contentHash()).isEqualTo(ilk.contentHash());
        assertThat(ikinci.yeniBayt()).isFalse();
        assertThat(imageService.listele(ImageLink.OWNER_PRODUCT, DENEME_SKU))
                .hasSize(oncekiBagSayisi);
    }

    @Test
    @Transactional(transactionManager = "appTransactionManager")
    @Rollback
    @DisplayName("İkinci görsel galeriye ekleniyor ve birincil yapılabiliyor")
    void galeriVeBirincil() throws IOException {
        ImageService.Yukleme bir = imageService.yukle(ImageLink.OWNER_PRODUCT, DENEME_SKU,
                jpegUret(900, 900, 2), "bir.jpg", null, true);
        ImageService.Yukleme iki = imageService.yukle(ImageLink.OWNER_PRODUCT, DENEME_SKU,
                jpegUret(900, 900, 3), "iki.jpg", null, false);

        assertThat(bir.contentHash()).isNotEqualTo(iki.contentHash());

        List<ImageLink> baglar = imageService.listele(ImageLink.OWNER_PRODUCT, DENEME_SKU);
        assertThat(baglar).hasSizeGreaterThanOrEqualTo(2);
        assertThat(baglar.get(0).getContentHash()).isEqualTo(bir.contentHash());

        // İkinciyi birincil yap: sıralama baştan yazılıyor, iki tane 0 olamaz.
        Long ikinciId = baglar.stream()
                .filter(l -> l.getContentHash().equals(iki.contentHash()))
                .findFirst().orElseThrow().getId();
        assertThat(imageService.birincilYap(ImageLink.OWNER_PRODUCT, DENEME_SKU, ikinciId)).isTrue();

        List<ImageLink> sonra = imageService.listele(ImageLink.OWNER_PRODUCT, DENEME_SKU);
        assertThat(sonra.get(0).getContentHash()).isEqualTo(iki.contentHash());
        assertThat(sonra.stream().filter(l -> l.getSortOrder() == ImageLink.BIRINCIL)).hasSize(1);
    }

    @Test
    @DisplayName("Dosya adından SKU çıkarma")
    void dosyaAdindanKod() {
        assertThat(ImageService.dosyaAdindanKod("ABC-123.jpg")).isEqualTo("ABC-123");
        assertThat(ImageService.dosyaAdindanKod("ABC-123 (2).jpeg")).isEqualTo("ABC-123");
        assertThat(ImageService.dosyaAdindanKod("C:\\foto\\ABC-123.png")).isEqualTo("ABC-123");
        // Sondaki tek/iki haneli sıra eki atılıyor — aynı ürünün ikinci fotoğrafı.
        assertThat(ImageService.dosyaAdindanKod("ABC123-2.jpg")).isEqualTo("ABC123");
    }

    private byte[] jpegUret(int genislik, int yukseklik, int tohum) throws IOException {
        BufferedImage gorsel = new BufferedImage(genislik, yukseklik, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gorsel.createGraphics();
        try {
            Random rastgele = new Random(tohum);
            for (int x = 0; x < genislik; x += 4) {
                for (int y = 0; y < yukseklik; y += 4) {
                    g.setColor(new Color(rastgele.nextInt(0xFFFFFF)));
                    g.fillRect(x, y, 4, 4);
                }
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream cikis = new ByteArrayOutputStream();
        ImageIO.write(gorsel, "jpg", cikis);
        return cikis.toByteArray();
    }
}
