package com.simge.adminbackend.erp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Mikro stok ana grubu = vitrindeki <b>kategori</b> — salt okunur (ADR D-153).
 *
 * <p>
 * Vitrin backend'indeki {@code Category} sınıfının dar bir kopyası. Panelin
 * ihtiyacı iki şey: kategoriyi listelemek (görsel yüklemek için) ve ana
 * sayfadaki kategori şeridinde hangi kategorinin seçildiğini isimle göstermek.
 * </p>
 *
 * <p>
 * {@code recno} da {@code kod} da taşınıyor çünkü ikisi farklı yerlerde
 * kullanılıyor: ana sayfa şeridi kategorileri <b>recno</b> ile referanslıyor
 * (vitrin şablonu sayısal id bekliyor), görseller ise <b>kod</b> ile
 * bağlanıyor (kod operatörün gördüğü, kalıcı olan değer).
 * </p>
 */
@Entity
@Table(name = "STOK_ANA_GRUPLARI")
@Getter
@Setter
public class AnaGrup {

    @Id
    @Column(name = "san_RECno")
    private Long recno;

    @Column(name = "san_kod")
    private String kod;

    @Column(name = "san_isim")
    private String isim;

    @Column(name = "san_iptal")
    private Boolean iptal;
}
