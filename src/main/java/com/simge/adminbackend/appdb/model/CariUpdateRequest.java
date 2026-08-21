package com.simge.adminbackend.appdb.model;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cari güncelleme talebi (ADR D-173).
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın eşi; tablo ortak
 * ({@code SIMGE_OS_APP.SIMGE_CARI_UPDATE_REQUEST}): <b>vitrin yazar, panel
 * okur ve karar verir</b>. Onaylanan talebi ERP'ye yazan taraf bu servisteki
 * {@link com.simge.adminbackend.erp.CariWriter}.
 * </p>
 *
 * <h2>Neden tek tablo, üç tür</h2>
 * <p>
 * Adres ekleme, fatura adresi değiştirme ve cari bilgisi güncelleme aynı
 * akıştan geçiyor: e-posta kodu → talep → panelde inceleme → ERP. Üç ayrı
 * tablo, aynı onay akışını üç kez yazmak olurdu.
 * </p>
 *
 * <h2>Alan genişlikleri Mikro'nunkiyle aynı</h2>
 * <p>
 * Bilerek: sınırı girişte uygulamazsak panele <b>ERP'ye yazılamayan</b> bir
 * talep düşer ve hata operatörün düzeltemeyeceği bir anda çıkar.
 * {@code adres_baslik} 10, {@code adres_tel_no} 10 karakter — ikisi de
 * beklenenden dar.
 * </p>
 */
@Entity
@Table(name = "SIMGE_CARI_UPDATE_REQUEST")
@Getter
@Setter
public class CariUpdateRequest {

    /** Cariye yeni adres satırı ekle. */
    public static final String TYPE_ADRES_EKLE = "ADRES_EKLE";

    /** Hangi adresin fatura adresi olduğunu değiştir. */
    public static final String TYPE_FATURA_ADRESI = "FATURA_ADRESI";

    /** Unvan / telefon / e-posta. */
    public static final String TYPE_CARI_BILGI = "CARI_BILGI";

    public static final String STATUS_BEKLIYOR = "BEKLIYOR";
    public static final String STATUS_AKTARILDI = "AKTARILDI";
    public static final String STATUS_REDDEDILDI = "REDDEDILDI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_type", nullable = false)
    private String requestType;

    /**
     * Hedef cari.
     *
     * <p>
     * <b>İstemciden alınmaz</b> — oturumdaki kullanıcının cari kodundan
     * yazılır. Aksi halde bir müşteri, gövdedeki kodu değiştirerek başka bir
     * firmanın ERP kaydını değiştirtebilirdi.
     * </p>
     */
    @Column(name = "cari_kod", nullable = false)
    private String cariKod;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    /** Kodun gittiği adres; "kim, hangi posta kutusuyla doğruladı" kaydı. */
    @Column(name = "requested_email", nullable = false)
    private String requestedEmail;

    @Column(name = "status", nullable = false)
    private String status;

    /** Talep ancak kod doğrulandıktan sonra oluşuyor; bu yüzden NOT NULL. */
    @Column(name = "otp_verified_at", nullable = false)
    private OffsetDateTime otpVerifiedAt;

    // --- ADRES_EKLE ---

    @Column(name = "adres_baslik")
    private String adresBaslik;

    @Column(name = "adres_cadde")
    private String adresCadde;

    @Column(name = "adres_mahalle")
    private String adresMahalle;

    @Column(name = "adres_sokak")
    private String adresSokak;

    @Column(name = "adres_semt")
    private String adresSemt;

    @Column(name = "adres_apt_no")
    private String adresAptNo;

    @Column(name = "adres_daire_no")
    private String adresDaireNo;

    @Column(name = "adres_ilce")
    private String adresIlce;

    @Column(name = "adres_il")
    private String adresIl;

    @Column(name = "adres_ulke")
    private String adresUlke;

    @Column(name = "adres_posta_kodu")
    private String adresPostaKodu;

    /** Telefon üç parça; Mikro böyle tutuyor (D-173). */
    @Column(name = "adres_tel_ulke")
    private String adresTelUlke;

    @Column(name = "adres_tel_bolge")
    private String adresTelBolge;

    @Column(name = "adres_tel_no")
    private String adresTelNo;

    // --- FATURA_ADRESI ---

    @Column(name = "hedef_adres_no")
    private Integer hedefAdresNo;

    // --- CARI_BILGI ---

    @Column(name = "cari_unvan")
    private String cariUnvan;

    @Column(name = "cari_telefon")
    private String cariTelefon;

    @Column(name = "cari_email")
    private String cariEmail;

    // --- Sonuç ---

    /** Aktarıldıysa Mikro'da oluşan {@code adr_adres_no}. */
    @Column(name = "sonuc_adres_no")
    private Integer sonucAdresNo;

    @Column(name = "red_nedeni")
    private String redNedeni;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    public boolean isBekliyor() {
        return STATUS_BEKLIYOR.equals(status);
    }
}
