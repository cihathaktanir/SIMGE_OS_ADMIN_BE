package com.simge.adminbackend.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/** Personel giriş doğrulaması (ADR D-123). */
class StaffAuthServiceTest {

    private static final String PAROLA = "karanfil-pusula-9";

    private StaffUserRepository repository;
    private StaffLoginAttemptService attempts;
    private PasswordEncoder encoder;
    private StaffAuthService service;

    @BeforeEach
    void setUp() {
        repository = mock(StaffUserRepository.class);
        attempts = mock(StaffLoginAttemptService.class);
        encoder = new BCryptPasswordEncoder();
        service = new StaffAuthService(repository, encoder, attempts, new UsernamePolicy());
    }

    private StaffUser user(String username, Set<String> roles, String status) {
        StaffUser u = new StaffUser();
        u.setId(3L);
        u.setUsername(username);
        u.setPasswordHash(encoder.encode(PAROLA));
        u.setStatus(status);
        u.setRoles(roles);
        return u;
    }

    @Test
    @DisplayName("Doğru parolayla giriş yapılır ve sayaç sıfırlanır")
    void basariliGiris() {
        when(repository.findByUsername("depo1"))
                .thenReturn(Optional.of(user("depo1", Set.of(StaffUser.ROLE_DEPO),
                        StaffUser.STATUS_ACTIVE)));

        StaffPrincipal principal = service.authenticate("depo1", PAROLA);

        assertEquals("depo1", principal.getUsername());
        assertTrue(principal.hasRole(StaffUser.ROLE_DEPO));
        verify(attempts).recordSuccess(3L);
    }

    @Test
    @DisplayName("Kullanıcı adı büyük harfle yazılsa da bulunur")
    void kullaniciAdiNormalizeEdilir() {
        when(repository.findByUsername("depo1"))
                .thenReturn(Optional.of(user("depo1", Set.of(StaffUser.ROLE_DEPO),
                        StaffUser.STATUS_ACTIVE)));

        assertEquals("depo1", service.authenticate("  DEPO1 ", PAROLA).getUsername());
    }

    @Test
    @DisplayName("Olmayan kullanıcı: 'kullanıcı yok' demeden aynı hata")
    void olmayanKullanici() {
        when(repository.findByUsername("yok")).thenReturn(Optional.empty());

        assertThrows(StaffAuthService.InvalidCredentialsException.class,
                () -> service.authenticate("yok", PAROLA));
        // Sayaç artırılacak bir hesap yok; kayıt da tutulmamalı.
        verify(attempts, never()).recordFailure(anyLong());
    }

    @Test
    @DisplayName("Yanlış parola sayacı artırır")
    void yanlisParolaSayaciArtirir() {
        when(repository.findByUsername("depo1"))
                .thenReturn(Optional.of(user("depo1", Set.of(StaffUser.ROLE_DEPO),
                        StaffUser.STATUS_ACTIVE)));

        assertThrows(StaffAuthService.InvalidCredentialsException.class,
                () -> service.authenticate("depo1", "yanlis-parola-123"));
        verify(attempts).recordFailure(3L);
    }

    @Test
    @DisplayName("Kapatılmış hesap giriş yapamaz — parola doğru olsa bile")
    void kapaliHesapGiremez() {
        when(repository.findByUsername("depo1"))
                .thenReturn(Optional.of(user("depo1", Set.of(StaffUser.ROLE_DEPO),
                        StaffUser.STATUS_DISABLED)));

        assertThrows(StaffAuthService.InvalidCredentialsException.class,
                () -> service.authenticate("depo1", PAROLA));
    }

    @Test
    @DisplayName("Rolü olmayan hesap giriş yapamaz — panelde hiçbir şey göremezdi")
    void rolsuzHesapGiremez() {
        when(repository.findByUsername("bosrol"))
                .thenReturn(Optional.of(user("bosrol", Set.of(), StaffUser.STATUS_ACTIVE)));

        assertThrows(StaffAuthService.InvalidCredentialsException.class,
                () -> service.authenticate("bosrol", PAROLA));
    }

    @Test
    @DisplayName("Kilitli hesapta parola hiç denenmez")
    void kilitliHesap() {
        StaffUser locked = user("depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_ACTIVE);
        locked.setLockedUntil(Instant.now().plusSeconds(600));
        when(repository.findByUsername("depo1")).thenReturn(Optional.of(locked));

        assertThrows(StaffAuthService.AccountLockedException.class,
                () -> service.authenticate("depo1", PAROLA));
        verify(attempts, never()).recordFailure(anyLong());
    }

    @Test
    @DisplayName("Süresi geçmiş kilit engel değil")
    void suresiGecmisKilit() {
        StaffUser wasLocked = user("depo1", Set.of(StaffUser.ROLE_DEPO), StaffUser.STATUS_ACTIVE);
        wasLocked.setLockedUntil(Instant.now().minusSeconds(60));
        when(repository.findByUsername("depo1")).thenReturn(Optional.of(wasLocked));

        assertEquals("depo1", service.authenticate("depo1", PAROLA).getUsername());
    }

    @Test
    @DisplayName("Oturum nesnesi rolleri ROLE_ önekiyle yetkiye çevirir")
    void yetkilerOnekliUretilir() {
        StaffUser u = user("mudur", Set.of(StaffUser.ROLE_ADMIN, StaffUser.ROLE_SATIS),
                StaffUser.STATUS_ACTIVE);
        when(repository.findByUsername("mudur")).thenReturn(Optional.of(u));

        StaffPrincipal principal = service.authenticate("mudur", PAROLA);

        Set<String> authorities = principal.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_SATIS"), authorities);
    }
}
