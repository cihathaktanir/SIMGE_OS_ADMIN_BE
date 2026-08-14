package com.simge.adminbackend.appdb.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bir görselin baytları (ADR D-142).
 *
 * <p>
 * Anahtarı kaynak dosyanın SHA-256'sı — yani <b>içerik adresli</b>. Aynı
 * fotoğraf beş farklı ürüne yüklenirse tabloda bir kez durur; bağı
 * {@link ImageLink} kuruyor.
 * </p>
 *
 * <p>
 * Ham dosya saklanmıyor, yalnızca iki türev. Gerekçe V17'de.
 * </p>
 */
@Entity
@Table(name = "SIMGE_IMAGE_BLOB")
@Getter
@Setter
public class ImageBlob {

    public static final String FORMAT_JPG = "jpg";

    @Id
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "format", nullable = false)
    private String format = FORMAT_JPG;

    @Column(name = "thumb_bytes", nullable = false)
    private byte[] thumbBytes;

    @Column(name = "thumb_width", nullable = false)
    private Integer thumbWidth;

    @Column(name = "thumb_height", nullable = false)
    private Integer thumbHeight;

    @Column(name = "detail_bytes", nullable = false)
    private byte[] detailBytes;

    @Column(name = "detail_width", nullable = false)
    private Integer detailWidth;

    @Column(name = "detail_height", nullable = false)
    private Integer detailHeight;

    @Column(name = "byte_size", nullable = false)
    private Integer byteSize;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
