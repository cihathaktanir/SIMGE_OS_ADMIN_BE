package com.simge.adminbackend.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.simge.adminbackend.appdb.model.StaffUser;
import com.simge.adminbackend.appdb.repository.StaffUserRepository;

/** Personelin kendi parolasını değiştirmesi (ADR D-123). */
class StaffPasswordServiceTest {

    private static final String MEVCUT = "baslangic-parolasi-1";
    private static final String YENI = "karanfil-pusula-9";

    private StaffUserRepository repository;
    private StaffSessionRevoker sessionRevoker;
    private PasswordEncoder encoder;
    private StaffPasswordService service;
    private StaffUser user;

    @BeforeEach
    void setUp() {
        repository = mock(StaffUserRepository.class);
        sessionRevoker = mock(StaffSessionRevoker.class);
        encoder = new BCryptPasswordEncoder();
        service = new StaffPasswordService(repository, encoder, new PasswordPolicy(),
                sessionRevoker);

        user = new StaffUser();
        user.setId(5L);
        user.setUsername("depo1");
        user.setFullName("Ahmet Yılmaz");
        user.setPasswordHash(encoder.encode(MEVCUT));
        user.setStatus(StaffUser.STATUS_ACTIVE);
        user.setMustChangePassword(true);
        user.setRoles(Set.of(StaffUser.ROLE_DEPO));

        when(repository.findById(5L)).thenReturn(Optional.of(user));
        when(repository.save(any(StaffUser.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Parola değişir ve zorunluluk bayrağı iner")
    void parolaDegisirBayrakIner() {
        StaffPrincipal refreshed = service.change(5L, MEVCUT, YENI, "oturum-1");

        assertTrue(encoder.matches(YENI, user.getPasswordHash()));
        assertFalse(user.isMustChangePassword());
        assertFalse(refreshed.isMustChangePassword(),
                "dönen oturum nesnesi de tazelenmiş olmalı");
    }

    @Test
    @DisplayName("Mevcut parola zorunlu girişte bile sorulur")
    void mevcutParolaYanlissaReddedilir() {
        assertThrows(StaffPasswordService.WrongCurrentPasswordException.class,
                () -> service.change(5L, "bambaska-birsey-42", YENI, "oturum-1"));

        assertTrue(encoder.matches(MEVCUT, user.getPasswordHash()), "parola değişmemeliydi");
        assertTrue(user.isMustChangePassword());
    }

    @Test
    @DisplayName("Aynı parola yeniden konulamaz")
    void ayniParolaReddedilir() {
        assertThrows(StaffPasswordService.SamePasswordException.class,
                () -> service.change(5L, MEVCUT, MEVCUT, "oturum-1"));
    }

    @Test
    @DisplayName("Kısa parola reddedilir")
    void kisaParolaReddedilir() {
        StaffPasswordService.WeakPasswordException e =
                assertThrows(StaffPasswordService.WeakPasswordException.class,
                        () -> service.change(5L, MEVCUT, "kisa1", "oturum-1"));

        assertEquals(PasswordPolicy.Violation.TOO_SHORT, e.getViolation());
    }

    @Test
    @DisplayName("Kullanıcı adını içeren parola reddedilir")
    void kullaniciAdiIcerenParolaReddedilir() {
        StaffPasswordService.WeakPasswordException e =
                assertThrows(StaffPasswordService.WeakPasswordException.class,
                        () -> service.change(5L, MEVCUT, "depo1-parolasi", "oturum-1"));

        assertEquals(PasswordPolicy.Violation.CONTAINS_PERSONAL_INFO, e.getViolation());
    }

    @Test
    @DisplayName("Ad soyaddan türetilen parola reddedilir — Türkçe harfler katlanarak")
    void adSoyadIcerenParolaReddedilir() {
        // "Yılmaz" soyadlı biri klavyeden çıkması kolay olan "yilmaz"ı yazar.
        StaffPasswordService.WeakPasswordException e =
                assertThrows(StaffPasswordService.WeakPasswordException.class,
                        () -> service.change(5L, MEVCUT, "yilmaz-2026-ev", "oturum-1"));

        assertEquals(PasswordPolicy.Violation.CONTAINS_PERSONAL_INFO, e.getViolation());
    }

    @Test
    @DisplayName("Değişiklikten sonra DİĞER cihazların oturumu düşer, kendisininki kalır")
    void digerOturumlarDuser() {
        service.change(5L, MEVCUT, YENI, "oturum-1");

        verify(sessionRevoker).revokeAllExcept("depo1", "oturum-1");
    }

    @Test
    @DisplayName("Üretilen geçici parola politikayı geçer — kullanıcı giriş yapabilmeli")
    void uretilenGeciciParolaPolitikayiGecer() {
        PasswordPolicy policy = new PasswordPolicy();
        for (int i = 0; i < 200; i++) {
            String temp = TempPassword.generate();
            assertEquals(14, temp.length(), temp);
            assertFalse(temp.matches(".*[il0o1].*"), "karışan karakter içeriyor: " + temp);
            assertEquals(null, policy.validate(temp, "depo1", "Ahmet Yılmaz"),
                    "politika reddetti: " + temp);
        }
    }
}
