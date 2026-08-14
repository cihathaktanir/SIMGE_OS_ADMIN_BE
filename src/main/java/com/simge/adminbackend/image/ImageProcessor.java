package com.simge.adminbackend.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.stereotype.Component;

/**
 * Yüklenen fotoğraftan vitrinin kullandığı iki türevi üretir (ADR D-142).
 *
 * <p>
 * Boyutlar tahmin değil <b>ölçüm</b>: vitrindeki ürün görselleri 190, 236 ve
 * 306 piksel karolarda, ürün detayında ise 340 piksel çiziliyor. Bu yüzden
 * {@code thumb} 600 (306'yı DPR 2'de karşılar), {@code detail} 1200 (340'ı
 * DPR 3'te karşılar ve yakınlaştırmaya yer bırakır).
 * </p>
 *
 * <p>
 * <b>Ham dosya saklanmıyor.</b> Operatör telefondan 4 MB'lık bir fotoğraf
 * atsa da saklanan iki türev oluyor; boyut sınırı bir kabul kriteri değil,
 * yalnızca kötüye kullanım koruması.
 * </p>
 *
 * <p>
 * <b>Biçim JPEG.</b> WebP ~%25 daha küçük olurdu ama yerel kütüphane
 * (JNI) gerektiriyor ve bu, yükleme sırasında JVM'i düşürebilecek tek
 * bileşen olurdu. Görseller değişmez URL'lerle sonsuza kadar önbelleğe
 * alındığı için fark istemci başına bir kereliktir. {@code format} sütunu
 * ve URL'deki uzantı sayesinde WebP ileride <b>ek</b> bir biçim olarak
 * gelebilir — göç gerekmez.
 * </p>
 */
@Component
public class ImageProcessor {

    /** Karo görselinin uzun kenarı. */
    public static final int THUMB_MAX = 600;

    /** Detay görselinin uzun kenarı. */
    public static final int DETAIL_MAX = 1200;

    /**
     * JPEG kalitesi.
     *
     * <p>
     * 0.82 fotoğrafta gözle ayırt edilebilir kayıp bırakmayan en düşük
     * pratik değer; 0.9'a çıkmak dosyayı ~%40 büyütüp görünür bir şey
     * kazandırmıyor.
     * </p>
     */
    private static final float KALITE = 0.82f;

    /** Kabul edilen en büyük dosya; sadece kötüye kullanım koruması. */
    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    /**
     * Kabul edilen en büyük piksel sayısı.
     *
     * <p>
     * "Decompression bomb" koruması: 10 MB'lık bir dosya 30.000x30.000
     * piksele açılabilir ve çözerken gigabaytlarca yığın ister. Dosya
     * boyutu sınırı bunu <b>engellemiyor</b>, ayrı bir kontrol gerekiyor.
     * </p>
     */
    private static final long MAX_PIXELS = 50_000_000L;

    /** İşlenmiş görsel: iki türev ve kimliği. */
    public record Sonuc(
            String contentHash,
            byte[] thumbBytes, int thumbWidth, int thumbHeight,
            byte[] detailBytes, int detailWidth, int detailHeight,
            int byteSize) {
    }

    /** Kaynak okunamadı ya da desteklenmeyen bir biçim. */
    public static class GecersizGorselException extends RuntimeException {
        public GecersizGorselException(String mesaj) {
            super(mesaj);
        }
    }

    /**
     * @param kaynak yüklenen dosyanın baytları
     * @throws GecersizGorselException dosya görsel değilse ya da çok büyükse
     */
    public Sonuc isle(byte[] kaynak) throws IOException {
        if (kaynak == null || kaynak.length == 0) {
            throw new GecersizGorselException("bos_dosya");
        }
        if (kaynak.length > MAX_UPLOAD_BYTES) {
            throw new GecersizGorselException("cok_buyuk");
        }

        BufferedImage orijinal = oku(kaynak);

        BufferedImage thumb = kucult(orijinal, THUMB_MAX);
        BufferedImage detail = kucult(orijinal, DETAIL_MAX);

        byte[] thumbBytes = jpegYaz(thumb);

        // Kaynak zaten thumb sınırından küçükse küçültme yapılmıyor (büyütmek
        // yok) ve iki türev BİREBİR AYNI oluyor. Aynı baytları iki kez
        // kodlamanın ve saklamanın anlamı yok: küçük bir ürün fotoğrafında
        // saklanan boyutu yarıya indiriyor.
        boolean ayni = thumb.getWidth() == detail.getWidth()
                && thumb.getHeight() == detail.getHeight();
        byte[] detailBytes = ayni ? thumbBytes : jpegYaz(detail);

        return new Sonuc(
                sha256(kaynak),
                thumbBytes, thumb.getWidth(), thumb.getHeight(),
                detailBytes, detail.getWidth(), detail.getHeight(),
                ayni ? thumbBytes.length : thumbBytes.length + detailBytes.length);
    }

    /**
     * Piksel sayısını çözmeden önce denetler.
     *
     * <p>
     * {@code ImageIO.read} her şeyi belleğe açar. Boyutu önce okuyup
     * reddetmek, kötü niyetli ya da yanlışlıkla dev bir dosyanın süreci
     * düşürmesini engelliyor.
     * </p>
     */
    private BufferedImage oku(byte[] kaynak) throws IOException {
        try (var giris = ImageIO.createImageInputStream(new ByteArrayInputStream(kaynak))) {
            if (giris == null) {
                throw new GecersizGorselException("okunamadi");
            }
            Iterator<ImageReader> okuyucular = ImageIO.getImageReaders(giris);
            if (!okuyucular.hasNext()) {
                throw new GecersizGorselException("desteklenmeyen_bicim");
            }

            ImageReader okuyucu = okuyucular.next();
            try {
                okuyucu.setInput(giris);
                long piksel = (long) okuyucu.getWidth(0) * okuyucu.getHeight(0);
                if (piksel > MAX_PIXELS) {
                    throw new GecersizGorselException("cok_buyuk_cozunurluk");
                }
                BufferedImage gorsel = okuyucu.read(0);
                if (gorsel == null) {
                    throw new GecersizGorselException("okunamadi");
                }
                return gorsel;
            } finally {
                okuyucu.dispose();
            }
        }
    }

    /**
     * Uzun kenarı {@code enBuyuk} olacak şekilde küçültür; en-boy oranı korunur.
     *
     * <p>
     * Kaynak zaten küçükse <b>büyütmez</b> — 300 pikselik bir fotoğrafı 1200'e
     * esnetmek dosyayı büyütüp görüntüyü bozardı.
     * </p>
     */
    private BufferedImage kucult(BufferedImage kaynak, int enBuyuk) {
        int g = kaynak.getWidth();
        int y = kaynak.getHeight();
        double oran = Math.min(1.0, (double) enBuyuk / Math.max(g, y));

        int yeniG = Math.max(1, (int) Math.round(g * oran));
        int yeniY = Math.max(1, (int) Math.round(y * oran));

        // TYPE_INT_RGB: JPEG saydamlık taşımıyor. Saydam PNG doğrudan
        // çevrilirse saydam pikseller SİYAH çıkar — ambalaj fotoğrafları
        // çoğu zaman saydam PNG olduğu için önce beyaza basılıyor.
        BufferedImage hedef = new BufferedImage(yeniG, yeniY, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = hedef.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, yeniG, yeniY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(kaynak, 0, 0, yeniG, yeniY, null);
        } finally {
            g2.dispose();
        }
        return hedef;
    }

    private byte[] jpegYaz(BufferedImage gorsel) throws IOException {
        Iterator<ImageWriter> yazicilar = ImageIO.getImageWritersByFormatName("jpg");
        if (!yazicilar.hasNext()) {
            throw new IllegalStateException("JPEG yazıcısı yok");
        }
        ImageWriter yazici = yazicilar.next();

        ByteArrayOutputStream cikis = new ByteArrayOutputStream();
        try (var akis = new MemoryCacheImageOutputStream(cikis)) {
            yazici.setOutput(akis);

            ImageWriteParam param = yazici.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(KALITE);
            }
            yazici.write(null, new IIOImage(gorsel, null, null), param);
        } finally {
            yazici.dispose();
        }
        return cikis.toByteArray();
    }

    /** Kaynak dosyanın SHA-256'sı; görselin kimliği ve URL'deki anahtar. */
    private String sha256(byte[] veri) {
        try {
            byte[] ozet = MessageDigest.getInstance("SHA-256").digest(veri);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : ozet) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JVM'de var; buraya düşmek imkânsız.
            throw new IllegalStateException(e);
        }
    }
}
