package com.simge.adminbackend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Görsel türevlerinin üretimi (ADR D-142).
 *
 * <p>
 * Veritabanı gerektirmiyor: bu sınıfın işi baytları baytlara çevirmek.
 * </p>
 */
class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    @DisplayName("Büyük fotoğraf iki türeve iniyor ve ciddi biçimde küçülüyor")
    void buyukFotografKuculuyor() throws IOException {
        // Tipik bir ürün fotoğrafı ölçüsü: 2400x1800.
        byte[] kaynak = jpegUret(2400, 1800);

        ImageProcessor.Sonuc sonuc = processor.isle(kaynak);

        assertThat(sonuc.thumbWidth()).isEqualTo(ImageProcessor.THUMB_MAX);
        assertThat(sonuc.detailWidth()).isEqualTo(ImageProcessor.DETAIL_MAX);

        // En-boy oranı korunmalı: 2400x1800 = 4:3
        assertThat(sonuc.thumbHeight()).isEqualTo(450);
        assertThat(sonuc.detailHeight()).isEqualTo(900);

        // Saklanan, kaynaktan küçük olmalı — yoksa küçültmenin anlamı yok.
        assertThat(sonuc.byteSize()).isLessThan(kaynak.length);

        assertThat(sonuc.contentHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(sonuc.byteSize())
                .isEqualTo(sonuc.thumbBytes().length + sonuc.detailBytes().length);
    }

    @Test
    @DisplayName("Küçük fotoğraf BÜYÜTÜLMÜYOR")
    void kucukFotografBuyutulmuyor() throws IOException {
        // 300 piksellik bir görseli 1200'e esnetmek dosyayı büyütür ve
        // görüntüyü bozar; olduğu gibi kalmalı.
        byte[] kaynak = jpegUret(300, 200);

        ImageProcessor.Sonuc sonuc = processor.isle(kaynak);

        assertThat(sonuc.thumbWidth()).isEqualTo(300);
        assertThat(sonuc.thumbHeight()).isEqualTo(200);
        assertThat(sonuc.detailWidth()).isEqualTo(300);
        assertThat(sonuc.detailHeight()).isEqualTo(200);

        // İki türev birebir aynı olduğu için tek kez kodlanıp paylaşılıyor;
        // saklanan boyut da tek kopya sayılıyor.
        assertThat(sonuc.detailBytes()).isSameAs(sonuc.thumbBytes());
        assertThat(sonuc.byteSize()).isEqualTo(sonuc.thumbBytes().length);
    }

    @Test
    @DisplayName("Dikey fotoğrafta uzun kenar sınırlanıyor")
    void dikeyFotograf() throws IOException {
        byte[] kaynak = jpegUret(1000, 3000);

        ImageProcessor.Sonuc sonuc = processor.isle(kaynak);

        assertThat(sonuc.detailHeight()).isEqualTo(ImageProcessor.DETAIL_MAX);
        assertThat(sonuc.detailWidth()).isEqualTo(400);
    }

    @Test
    @DisplayName("Aynı dosya aynı hash'i veriyor — içerik adresli olmanın koşulu")
    void ayniDosyaAyniHash() throws IOException {
        byte[] kaynak = jpegUret(800, 600);

        assertThat(processor.isle(kaynak).contentHash())
                .isEqualTo(processor.isle(kaynak).contentHash());
    }

    @Test
    @DisplayName("Saydam PNG siyah değil BEYAZ zemine basılıyor")
    void saydamPngBeyazZemine() throws IOException {
        // Ambalaj fotoğrafları çoğu zaman saydam PNG. JPEG saydamlık
        // taşımadığı için doğrudan çevrilirse saydam pikseller SİYAH çıkar.
        BufferedImage saydam = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(saydam, "png", png);

        ImageProcessor.Sonuc sonuc = processor.isle(png.toByteArray());

        BufferedImage cikti = ImageIO.read(new java.io.ByteArrayInputStream(sonuc.thumbBytes()));
        Color kose = new Color(cikti.getRGB(5, 5));
        assertThat(kose.getRed()).isGreaterThan(240);
        assertThat(kose.getGreen()).isGreaterThan(240);
        assertThat(kose.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("Görsel olmayan dosya reddediliyor")
    void gorselOlmayanReddediliyor() {
        assertThatThrownBy(() -> processor.isle("bu bir metin dosyasi".getBytes()))
                .isInstanceOf(ImageProcessor.GecersizGorselException.class);
    }

    @Test
    @DisplayName("Boş dosya reddediliyor")
    void bosDosyaReddediliyor() {
        assertThatThrownBy(() -> processor.isle(new byte[0]))
                .isInstanceOf(ImageProcessor.GecersizGorselException.class);
    }

    @Test
    @DisplayName("Sınırı aşan dosya reddediliyor")
    void cokBuyukReddediliyor() {
        byte[] devasa = new byte[ImageProcessor.MAX_UPLOAD_BYTES + 1];
        assertThatThrownBy(() -> processor.isle(devasa))
                .isInstanceOf(ImageProcessor.GecersizGorselException.class)
                .hasMessage("cok_buyuk");
    }

    /** Sıkışmayan, gerçekçi boyutta bir JPEG üretir. */
    private byte[] jpegUret(int genislik, int yukseklik) throws IOException {
        BufferedImage gorsel = new BufferedImage(genislik, yukseklik, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = gorsel.createGraphics();
        try {
            // Düz renk neredeyse sıfır bayta sıkışır ve "küçüldü mü" iddiası
            // anlamsızlaşır; gürültü gerçek bir fotoğrafa daha yakın.
            java.util.Random rastgele = new java.util.Random(42);
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
