package com.simge.adminbackend.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/** Kilitlenmiş panelden çıkış yolu (ADR D-125). */
class StaffPasswordRecoveryTest {

    private static final String ESKI_PAROLA = "unutulan-parola-42";

    private StaffUserRepository repository;
    private StaffSessionRevoker sessionRevoker;
    private PasswordEncoder encoder;
    private StaffService staffService;
    private StaffUser admin;

    @BeforeEach
    void setUp() {
        repository = mock(StaffUserRepository.class);
        sessionRevoker = mock(StaffSessionRevoker.class);
        encoder = new BCryptPasswordEncoder();
        staffService = new StaffService(repository, encoder, new UsernamePolicy(), sessionRevoker);

        when(repository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));

        admin = new StaffUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash(encoder.encode(ESKI_PAROLA));
        admin.setStatus(StaffUser.STATUS_ACTIVE);
        admin.setMustChangePassword(false);
        admin.setRoles(new LinkedHashSet<>(Set.of(StaffUser.ROLE_ADMIN)));
    }

    private StaffPasswordRecovery recovery(String resetUsername) {
        return new StaffPasswordRecovery(repository, staffService, new UsernamePolicy(),
                resetUsername);
    }

    @Test
    @DisplayName("Değişken boşsa hiçbir şey yapılmaz — normal açılışta sessiz")
    void bosDegiskenSessiz() {
        recovery("").run(null);
        recovery(null).run(null);
        recovery("   ").run(null);

        verify(repository, never()).findByUsername(anyString());
        verify(repository, never()).save(any(StaffUser.class));
    }

    @Test
    @DisplayName("Parola sıfırlanır ve ilk girişte değiştirme zorunlu olur")
    void parolaSifirlanir() {
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        recovery("admin").run(null);

        assertTrue(admin.isMustChangePassword(),
                "sıfırlanan parola ilk girişte değiştirilmeli");
        assertNotEquals(true, encoder.matches(ESKI_PAROLA, admin.getPasswordHash()),
                "eski parola artık çalışmamalı");
    }

    @Test
    @DisplayName("Kullanıcı adı büyük harfle/boşluklu verilse de bulunur")
    void kullaniciAdiNormalizeEdilir() {
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        recovery("  ADMIN ").run(null);

        assertTrue(admin.isMustChangePassword());
    }

    @Test
    @DisplayName("Kilitli hesabın kilidi açılır — kilitliyken sıfırlamanın anlamı olmaz")
    void kilitAcilir() {
        admin.setFailedLoginCount(5);
        admin.setLockedUntil(java.time.Instant.now().plusSeconds(900));
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        recovery("admin").run(null);

        assertEquals(0, admin.getFailedLoginCount());
        assertTrue(!admin.isLocked());
    }

    @Test
    @DisplayName("Açık oturumlar düşer — eski oturum parola değişince devam etmesin")
    void oturumlarDuser() {
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        recovery("admin").run(null);

        verify(sessionRevoker).revokeAll("admin");
    }

    @Test
    @DisplayName("Olmayan kullanıcı adı HESAP AÇMAZ — yazım hatası yönetici doğurmasın")
    void olmayanKullaniciHesapAcmaz() {
        when(repository.findByUsername("yanlisyazim")).thenReturn(Optional.empty());

        recovery("yanlisyazim").run(null);

        verify(repository, never()).save(any(StaffUser.class));
    }

    @Test
    @DisplayName("ROL VERMEZ — 'herhangi birini yönetici yap' anahtarına dönüşmesin")
    void rolVermez() {
        StaffUser depocu = new StaffUser();
        depocu.setId(2L);
        depocu.setUsername("depo1");
        depocu.setPasswordHash(encoder.encode(ESKI_PAROLA));
        depocu.setStatus(StaffUser.STATUS_ACTIVE);
        depocu.setRoles(new LinkedHashSet<>(Set.of(StaffUser.ROLE_DEPO)));

        when(repository.findById(2L)).thenReturn(Optional.of(depocu));
        when(repository.findByUsername("depo1")).thenReturn(Optional.of(depocu));

        recovery("depo1").run(null);

        assertEquals(Set.of(StaffUser.ROLE_DEPO), depocu.getRoles(),
                "roller değişmemeliydi");
    }

    @Test
    @DisplayName("KAPALI HESABI AÇMAZ — parola sıfırlanır ama giriş yine çalışmaz")
    void kapaliHesabiAcmaz() {
        admin.setStatus(StaffUser.STATUS_DISABLED);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.findByUsername("admin")).thenReturn(Optional.of(admin));

        recovery("admin").run(null);

        assertEquals(StaffUser.STATUS_DISABLED, admin.getStatus(),
                "durum değişmemeliydi");
        assertTrue(admin.isMustChangePassword(), "parola yine de sıfırlanmalı");
    }
}
