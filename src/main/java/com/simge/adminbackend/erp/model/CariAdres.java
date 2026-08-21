package com.simge.adminbackend.erp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Mikro cari adresi (salt okunur, ADR D-100/D-104).
 *
 * <p>
 * Panel bu tabloyu <b>okumak</b> için kullanıyor: cari güncelleme kuyruğunda
 * (D-173) bekleyen adresin yanında carinin mevcut adresleri gösteriliyor ve
 * benzerlik uyarısı buradan çıkıyor.
 * </p>
 *
 * <p>
 * <b>Yazma bu sınıftan geçmiyor.</b> Adres INSERT'i {@code CariWriter} içinde,
 * ham JDBC ile — "ERP'ye kim yazabiliyor" sorusunun cevabı tek dosya kalsın
 * diye (D-127).
 * </p>
 */
@Entity
@Table(name = "CARI_HESAP_ADRESLERI")
@Getter
@Setter
public class CariAdres {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adr_RECno")
    private Long id;

    @Column(name = "adr_cari_kod", nullable = false)
    private String cariKod;

    /** Cari içindeki adres sırası; sipariş satırındaki {@code sip_adresno} buna karşılık gelir. */
    @Column(name = "adr_adres_no")
    private Integer adresNo;

    @Column(name = "adr_Adres_kodu")
    private String adresKodu;

    @Column(name = "adr_cadde")
    private String cadde;

    @Column(name = "adr_mahalle")
    private String mahalle;

    @Column(name = "adr_sokak")
    private String sokak;

    @Column(name = "adr_Semt")
    private String semt;

    @Column(name = "adr_Apt_No")
    private String aptNo;

    @Column(name = "adr_Daire_No")
    private String daireNo;

    @Column(name = "adr_posta_kodu")
    private String postaKodu;

    @Column(name = "adr_ilce")
    private String ilce;

    @Column(name = "adr_il")
    private String il;

    @Column(name = "adr_ulke")
    private String ulke;

    @Column(name = "adr_tel_no1")
    private String telefon;

    @Column(name = "adr_iptal")
    private Boolean iptal;

    @Column(name = "adr_hidden")
    private Boolean hidden;
}
