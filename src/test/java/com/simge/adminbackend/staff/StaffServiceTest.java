package com.simge.adminbackend.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/** Personel hesabı yönetimi (ADR D-123). */
class StaffServiceTest {

    private StaffUserRepository repository;
    private StaffSessionRevoker sessionRevoker;
    private PasswordEncoder encoder;
    private StaffService service;

    @BeforeEach
    void setUp() {
        repository = mock(StaffUserRepository.class);
        sessionRevoker = mock(StaffSessionRevoker.class);
        encoder = new BCryptPasswordEncoder();
        service = new StaffService(repository, encoder, new UsernamePolicy(), sessionRevoker);

        when(repository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StaffUser user(Long id, String username, Set<String> roles, String status) {
        StaffUser u = new StaffUser();
        u.setId(id);
        u.setUsername(username);
        u.setPasswordHash(encoder.encode("baslangic-parolasi-1"));
        u.setStatus(status);
        u.setRoles(new LinkedHashSet<>(roles));
        return u;
    }

    // ---------------------------------------------------------------- açma

    @Test
    @DisplayName("Hesap tek adımda açılır ve geçici parola BİR KEZ döner")
    void hesapAcilirVeGeciciParolaDoner() {
        when(repository.existsByUsername("depo1")).thenReturn(false);

        StaffService.CreatedAccount created = service.create(
                7L, "DEPO1", "Ahmet Depocu", null, Set.of(StaffUser.ROLE_DEPO));

        assertEquals("depo1", created.user().getUsername(), "kullanıcı adı küçüğe inmeli");
        assertNotNull(created.temporaryPassword());
        assertTrue(created.temporaryPassword().length() >= 12);
        assertEquals(7L, created.user().getCreatedBy());
    }

    @Test
    @DisplayName("Geçici parola veritabanına AÇIK yazılmaz, yalnızca BCrypt özeti")
    void geciciParolaAcikSaklanmaz() {
        when(repository.existsByUsername(anyString())).thenReturn(false);

        StaffService.CreatedAccount created = service.create(
                1L, "ayse", null, null, Set.of(StaffUser.ROLE_SATIS));

        String hash = created.user().getPasswordHash();
        assertNotEquals(created.temporaryPassword(), hash);
        assertTrue(hash.startsWith("$2"), "BCrypt özeti bekleniyordu: " + hash);
        assertTrue(encoder.matches(created.temporaryPassword(), hash));
    }

    @Test
    @DisplayName("Yeni hesap must_change_password ile açılır")
    void yeniHesapParolaDegistirmeZorunlu() {
        when(repository.existsByUsername(anyString())).thenReturn(false);

        StaffService.CreatedAccount created = service.create(
                1L, "depo2", null, null, Set.of(StaffUser.ROLE_DEPO));

        assertTrue(created.user().isMustChangePassword());
    }

    @Test
    @DisplayName("Her hesapta farklı geçici parola üretilir")
    void geciciParolalarFarkli() {
        when(repository.existsByUsername(anyString())).thenReturn(false);

        String a = service.create(1L, "kul1", null, null, Set.of(StaffUser.ROLE_DEPO))
                .temporaryPassword();
        String b = service.create(1L, "kul2", null, null, Set.of(StaffUser.ROLE_DEPO))
                .temporaryPassword();

        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Alınmış kullanıcı adı reddedilir")
    void alinmisKullaniciAdi() {
        when(repository.existsByUsername("depo1")).thenReturn(true);

        assertThrows(StaffService.UsernameTakenException.class,
                () -> service.create(1L, "depo1", null, null, Set.of(StaffUser.ROLE_DEPO)));
    }

    @Test
    @DisplayName("Kurala uymayan kullanıcı adı reddedilir ve kayıt denenmez")
    void gecersizKullaniciAdi() {
        assertThrows(StaffService.InvalidUsernameException.class,
                () -> service.create(1L, "depo şef", null, null, Set.of(StaffUser.ROLE_DEPO)));

        verify(repository, never()).save(any(StaffUser.class));
    }

    @Test
    @DisplayName("Rolsüz hesap açılamaz — panelde hiçbir şey göremezdi")
    void rolsuzHesapAcilamaz() {
        assertThrows(StaffService.NoRolesException.class,
                () -> service.create(1L, "bosrol", null, null, Set.of()));
        assertThrows(StaffService.NoRolesException.class,
                () -> service.create(1L, "bosrol", null, null, null));
    }

    @Test
    @DisplayName("Tanınmayan rol reddedilir — uydurma rol sessizce kaydedilmesin")
    void taninmayanRol() {
        assertThrows(StaffService.UnknownRoleException.class,
                () -> service.create(1L, "hacker", null, null, Set.of("SUPERUSER")));
    }

    @Test
    @DisplayName("Rol adı büyük harfe normalize edilir")
    void rolBuyukHarfeIner() {
        when(repository.existsByUsername(anyString())).thenReturn(false);

        StaffService.CreatedAccount created = service.create(
                1L, "depo3", null, null, Set.of("depo"));

        assertTrue(created.user().hasRole(StaffUser.ROLE_DEPO));
    }

    // ------------------------------------------------- son yönetici koruması

    @Test
    @DisplayName("Tek yöneticinin ADMIN rolü alınamaz — panel kendini kilitlemesin")
    void sonYoneticininRoluAlinamaz() {
        StaffUser admin = user(1L, "admin", Set.of(StaffUser.ROLE_ADMIN), StaffUser.STATUS_ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.countByRoleAndStatus(StaffUser.ROLE_ADMIN, StaffUser.STATUS_ACTIVE))
                .thenReturn(1L);

        assertThrows(StaffService.LastAdminException.class,
                () -> service.updateRoles(1L, Set.of(StaffUser.ROLE_SATIS)));

        assertTrue(admin.hasRole(StaffUser.ROLE_ADMIN), "rol değişmemeliydi");
    }

    @Test
    @DisplayName("Tek yönetici kapatılamaz")
    void sonYoneticiKapatilamaz() {
        StaffUser admin = user(1L, "admin", Set.of(StaffUser.ROLE_ADMIN), StaffUser.STATUS_ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.countByRoleAndStatus(StaffUser.ROLE_ADMIN, StaffUser.STATUS_ACTIVE))
                .thenReturn(1L);

        assertThrows(StaffService.LastAdminException.class, () -> service.setStatus(1L, false));

        assertTrue(admin.isActive(), "hesap kapanmamalıydı");
    }

    @Test
    @DisplayName("İkinci yönetici varsa birincinin rolü alınabilir")
    void ikinciYoneticiVarsaIzinVerilir() {
        StaffUser admin = user(1L, "admin", Set.of(StaffUser.ROLE_ADMIN), StaffUser.STATUS_ACTIVE);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));
        when(repository.countByRoleAndStatus(StaffUser.ROLE_ADMIN, StaffUser.STATUS_ACTIVE))
                .thenReturn(2L);

        StaffUser updated = service.updateRoles(1L, Set.of(StaffUser.ROLE_SATIS));

        assertFalse(updated.hasRole(StaffUser.ROLE_ADMIN));
        assertTrue(updated.hasRole(StaffUser.ROLE_SATIS));
    }

    @Test
    @DisplayName("Yönetici olmayan biri serbestçe kapatılabilir — sayaç sorulmaz")
    void yoneticiOlmayanKapatilabilir() {
        StaffUser depo = user(2L, "depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(depo));

        StaffUser updated = service.setStatus(2L, false);

        assertEquals(StaffUser.STATUS_DISABLED, updated.getStatus());
        verify(repository, never()).countByRoleAndStatus(anyString(), anyString());
    }

    // --------------------------------------------------- oturum düşürme

    @Test
    @DisplayName("Hesap kapatılınca açık oturumlar düşer")
    void kapatincaOturumlarDuser() {
        StaffUser depo = user(2L, "depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(depo));

        service.setStatus(2L, false);

        verify(sessionRevoker).revokeAll("depo1");
    }

    @Test
    @DisplayName("Rol değişince oturumlar düşer — eski yetkiyle dolaşılmasın")
    void rolDegisinceOturumlarDuser() {
        StaffUser depo = user(2L, "depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_ACTIVE);
        when(repository.findById(2L)).thenReturn(Optional.of(depo));

        service.updateRoles(2L, Set.of(StaffUser.ROLE_SATIS));

        verify(sessionRevoker).revokeAll("depo1");
    }

    @Test
    @DisplayName("Hesap açılırken kilit sayacı sıfırlanır")
    void acarkenKilitSifirlanir() {
        StaffUser depo = user(2L, "depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_DISABLED);
        depo.setFailedLoginCount(5);
        depo.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        when(repository.findById(2L)).thenReturn(Optional.of(depo));

        StaffUser updated = service.setStatus(2L, true);

        assertEquals(0, updated.getFailedLoginCount());
        assertFalse(updated.isLocked());
    }

    // ------------------------------------------------------ parola sıfırlama

    @Test
    @DisplayName("Sıfırlama yeni parola üretir, bayrağı kaldırır ve oturumları düşürür")
    void parolaSifirlama() {
        StaffUser depo = user(2L, "depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_ACTIVE);
        depo.setMustChangePassword(false);
        depo.setFailedLoginCount(3);
        String eskiHash = depo.getPasswordHash();
        when(repository.findById(2L)).thenReturn(Optional.of(depo));

        StaffService.CreatedAccount reset = service.resetPassword(2L);

        assertNotEquals(eskiHash, depo.getPasswordHash());
        assertTrue(encoder.matches(reset.temporaryPassword(), depo.getPasswordHash()));
        assertTrue(depo.isMustChangePassword(), "sıfırlanan parola da değiştirilmeli");
        assertEquals(0, depo.getFailedLoginCount());
        verify(sessionRevoker).revokeAll("depo1");
    }
}
