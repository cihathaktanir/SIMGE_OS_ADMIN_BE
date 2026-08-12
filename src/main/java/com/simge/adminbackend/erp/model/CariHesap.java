package com.simge.adminbackend.erp.model;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.Setter;

/**
 * Mikro cari hesabı (müşteri/tedarikçi kaydı) — <b>salt okunur</b>.
 *
 * <p>
 * Vitrin backend'indeki aynı adlı sınıfın kopyasıdır. Ortak bir modüle
 * çıkarılmadı: iki servisin ERP'den ihtiyaç duyduğu alan kümesi zamanla
 * ayrışacak (panel cari detayına iner, vitrin listeye bakar) ve paylaşılan bir
 * modül, birinin ihtiyacı için yapılan değişikliği diğerine zorla taşır.
 * Kopyanın maliyeti sütun adlarının iki yerde durması; karşılığında iki servis
 * birbirinden bağımsız sürüm alabiliyor.
 * </p>
 */
@Entity
@Table(name = "CARI_HESAPLAR")
@Getter
@Setter
public class CariHesap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cari_RECno")
    private Long id;

    @Column(name = "cari_iptal")
    private Boolean iptal;

    @Column(name = "cari_hidden")
    private Boolean hidden;

    @Column(name = "cari_kilitli")
    private Boolean kilitli;

    @Column(name = "cari_kod", nullable = false)
    private String cariKod;

    @Column(name = "cari_unvan1")
    private String cariUnvan1;

    @Column(name = "cari_unvan2")
    private String cariUnvan2;

    @Column(name = "cari_vdaire_adi")
    private String vergiDairesiAdi;

    /**
     * Vergi numarası. İsminin çağrıştırdığının aksine {@code cari_VergiKimlikNo}
     * DEĞİL — o alan bu veritabanında boş.
     */
    @Column(name = "cari_vdaire_no")
    private String vergiDairesiNo;

    @Column(name = "cari_EMail")
    private String email;

    @Column(name = "cari_CepTel")
    private String cepTelefonu;

    @Column(name = "cari_kaydagiristarihi")
    @Temporal(TemporalType.TIMESTAMP)
    private Date kayitGirisTarihi;
}
