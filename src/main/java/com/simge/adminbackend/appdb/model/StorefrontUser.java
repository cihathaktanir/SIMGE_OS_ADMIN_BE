package com.simge.adminbackend.appdb.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Vitrin kullanıcısı ({@code SIMGE_USER}) — panelde <b>yalnızca okunur</b>.
 *
 * <p>
 * Panelin buna ihtiyacı tek bir soru için var: "bu adresle zaten hesap açılmış
 * mı" — davet göndermeden önce bakılıyor. Yazma yok, bu yüzden eşleme de
 * kasten eksik: parola özeti, kilit sayacı, oturum alanları burada tanımlı
 * değil. Panelin bunlara dokunacak bir sebebi olmamalı.
 * </p>
 */
@Entity
@Table(name = "SIMGE_USER")
@Getter
public class StorefrontUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "cari_kod")
    private String cariKod;

    @Column(name = "status", nullable = false)
    private String status;
}
