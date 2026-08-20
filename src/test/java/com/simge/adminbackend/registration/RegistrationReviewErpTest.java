package com.simge.adminbackend.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simge.adminbackend.appdb.model.CompanyInvitation;
import com.simge.adminbackend.appdb.model.RegistrationRequest;
import com.simge.adminbackend.appdb.repository.RegistrationRequestRepository;
import com.simge.adminbackend.erp.CariKodUretici;
import com.simge.adminbackend.erp.CariLookupService;
import com.simge.adminbackend.erp.CariWriter;
import com.simge.adminbackend.erp.model.CariHesap;
import com.simge.adminbackend.staff.StaffPrincipal;
import com.simge.adminbackend.appdb.model.StaffUser;

/**
 * Onay akışının ERP'ye yazan kısmı (ADR D-127).
 *
 * <p>
 * Buradaki testlerin çoğu "yazmadığını" doğruluyor. ERP'ye yanlış satır yazmak,
 * yazmamaktan çok daha pahalı: kayıt Mikro'da kalır, geri alınması elle iş olur
 * ve mükerrer cari muhasebeyi bozar.
 * </p>
 */
class RegistrationReviewErpTest {

    private RegistrationRequestRepository requestRepository;
    private CompanyInviteService inviteService;
    private CariLookupService cariLookup;
    private CariWriter cariWriter;
    private CariKodUretici kodUretici;
    private RegistrationReviewService service;

    private StaffPrincipal personel;
    private RegistrationRequest basvuru;

    @BeforeEach
    void setUp() {
        requestRepository = mock(RegistrationRequestRepository.class);
        inviteService = mock(CompanyInviteService.class);
        cariLookup = mock(CariLookupService.class);
        cariWriter = mock(CariWriter.class);
        kodUretici = mock(CariKodUretici.class);
        service = new RegistrationReviewService(requestRepository, inviteService, cariLookup,
                cariWriter, kodUretici);

        StaffUser kullanici = new StaffUser();
        kullanici.setId(7L);
        kullanici.setUsername("satisci");
        kullanici.setFullName("Satış Personeli");
        kullanici.setPasswordHash("x");
        personel = new StaffPrincipal(kullanici);

        basvuru = new RegistrationRequest();
        basvuru.setId(42L);
        basvuru.setStatus(RegistrationRequest.STATUS_PENDING);
        basvuru.setEmail("yetkili@firma.test");
        basvuru.setFullName("Firma Yetkilisi");
        basvuru.setVergiNo("1234567890");
        basvuru.setBranch(RegistrationRequest.BRANCH_NO_CARI);

        when(requestRepository.findById(42L)).thenReturn(Optional.of(basvuru));
        when(requestRepository.save(any(RegistrationRequest.class)))
                .thenAnswer(i -> i.getArgument(0));
        // D-149: imzaya telefon eklendi (başvurudan davete taşınıyor).
        when(inviteService.invite(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyString(), any())).thenReturn(new CompanyInvitation());
        // Varsayılan: posta gönderilebiliyor. D-147 ile onay akışı ERP'ye
        // yazmadan önce bunu soruyor; sahte nesnenin varsayılanı false olduğu
        // için açıkça true dönmesi gerekiyor.
        when(inviteService.gonderilebilirMi()).thenReturn(true);
    }

    private CariWriter.YeniCari veri() {
        return new CariWriter.YeniCari("M-1000", "DENEME GIDA LTD", "ÇANKAYA", "1234567890",
                "yetkili@firma.test", false, "1. Cad. No:1", "Merkez Mah.", "Çankaya",
                "Ankara", "TÜRKİYE", "06100", "5551112233");
    }

    // ---------------------------------------------------------------- yeni cari

    @Test
    @DisplayName("Yeni cari açılır, başvuru ona bağlanır ve created_cari_kod dolar")
    void yeniCariAcilir() {
        when(kodUretici.ayniVergiNoluKodlar("1234567890")).thenReturn(List.of());
        when(cariWriter.yeniCari(any())).thenReturn(51413L);

        RegistrationRequest sonuc = service.yeniCariAcarakOnayla(personel, 42L, veri(), "not");

        verify(cariWriter).yeniCari(any());
        assertEquals(RegistrationRequest.STATUS_APPROVED, sonuc.getStatus());
        assertEquals("M-1000", sonuc.getCreatedCariKod());
        assertEquals("M-1000", sonuc.getMatchedCariKod());
        assertEquals(7L, sonuc.getReviewedBy());
    }

    @Test
    @DisplayName("Aynı vergi numarasıyla cari VARSA yazma HİÇ denenmez")
    void mukerrerCariYazilmaz() {
        // Başvuru 'cari yok' diye sınıflandırıldıktan sonra biri Mikro'da elle
        // açmış olabilir. İkinci cari mükerrer kayıt demek.
        when(kodUretici.ayniVergiNoluKodlar("1234567890")).thenReturn(List.of("M-500"));

        RegistrationReviewService.CariZatenVar hata = assertThrows(
                RegistrationReviewService.CariZatenVar.class,
                () -> service.yeniCariAcarakOnayla(personel, 42L, veri(), null));

        assertEquals(List.of("M-500"), hata.getKodlar());
        verify(cariWriter, never()).yeniCari(any());
        verify(inviteService, never()).invite(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Zaten incelenmiş başvuruda ERP'ye dokunulmaz")
    void incelenmisBasvuruYazmaz() {
        basvuru.setStatus(RegistrationRequest.STATUS_APPROVED);

        assertThrows(RegistrationReviewService.AlreadyReviewedException.class,
                () -> service.yeniCariAcarakOnayla(personel, 42L, veri(), null));

        verify(cariWriter, never()).yeniCari(any());
    }

    // ------------------------------------------------------- mevcut cari + eposta

    @Test
    @DisplayName("erp_eposta boşsa ERP'ye HİÇ yazılmaz — sadece hesap bağlanır")
    void epostaBosIseYazilmaz() {
        when(cariLookup.byKod("M-500")).thenReturn(Optional.of(cari()));

        service.approve(personel, 42L, "M-500", null, null);

        verify(cariWriter, never()).epostaYaz(anyString(), anyString());
        verify(inviteService).invite(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("erp_eposta doluysa cariye yazılır, küçük harfe çevrilerek")
    void epostaYazilir() {
        when(cariLookup.byKod("M-500")).thenReturn(Optional.of(cari()));
        when(cariWriter.epostaYaz("M-500", "yetkili@firma.test")).thenReturn(true);

        service.approve(personel, 42L, "M-500", "  Yetkili@Firma.TEST ", null);

        verify(cariWriter).epostaYaz("M-500", "yetkili@firma.test");
    }

    @Test
    @DisplayName("ERP e-postası yazılamazsa DAVET DE GİTMEZ")
    void yazilamazsaDavetGitmez() {
        // Ters sırada olsaydı, davet gitmiş ama ERP güncellenmemiş bir ara durum
        // kalırdı ve bunu kimse fark etmezdi.
        when(cariLookup.byKod("M-500")).thenReturn(Optional.of(cari()));
        when(cariWriter.epostaYaz(anyString(), anyString())).thenReturn(false);

        assertThrows(RegistrationReviewService.EpostaYazilamadi.class,
                () -> service.approve(personel, 42L, "M-500", "a@b.test", null));

        verify(inviteService, never()).invite(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Var olan cariye bağlarken created_cari_kod BOŞ kalır")
    void mevcutCarideCreatedKodBos() {
        when(cariLookup.byKod("M-500")).thenReturn(Optional.of(cari()));

        RegistrationRequest sonuc = service.approve(personel, 42L, "M-500", null, null);

        // İki alanın ayrı tutulmasının sebebi bu: "bu cariyi biz mi açtık"
        // sorusu sonradan cevaplanabilsin.
        assertNull(sonuc.getCreatedCariKod());
        assertEquals("M-500", sonuc.getMatchedCariKod());
    }

    @Test
    @DisplayName("Cari kodu Mikro'da yoksa ERP'ye yazılmaz")
    void olmayanCarideYazilmaz() {
        when(cariLookup.byKod("YOK")).thenReturn(Optional.empty());

        assertThrows(RegistrationReviewService.CariNotFoundException.class,
                () -> service.approve(personel, 42L, "YOK", "a@b.test", null));

        verify(cariWriter, never()).epostaYaz(anyString(), anyString());
    }

    // ------------------------------------------------- posta yoksa ERP'ye dokunma

    @Test
    @DisplayName("Posta gönderilemiyorsa YENİ CARİ AÇILMAZ (D-147)")
    void postaYoksaCariAcilmaz() {
        // Gerçekte yaşandı: SMTP yapılandırılmamışken onaylandı, Mikro'da cari
        // açıldı, davet gidemedi. Bu servisin transaction'ı geri sarıldı ama
        // Mikro BAŞKA bir veritabanı — oradaki yazma commit'lenmiş kaldı.
        when(kodUretici.ayniVergiNoluKodlar("1234567890")).thenReturn(List.of());
        when(inviteService.gonderilebilirMi()).thenReturn(false);

        assertThrows(CompanyInviteService.MailUnavailableException.class,
                () -> service.yeniCariAcarakOnayla(personel, 42L, veri(), "not"));

        verify(cariWriter, never()).yeniCari(any());
        verify(inviteService, never()).invite(anyString(), anyString(), anyLong(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Posta gönderilemiyorsa ERP e-posta alanına da YAZILMAZ (D-147)")
    void postaYoksaErpEpostaYazilmaz() {
        when(cariLookup.byKod("M-500")).thenReturn(Optional.of(cari()));
        when(inviteService.gonderilebilirMi()).thenReturn(false);

        assertThrows(CompanyInviteService.MailUnavailableException.class,
                () -> service.approve(personel, 42L, "M-500", "a@b.test", null));

        verify(cariWriter, never()).epostaYaz(anyString(), anyString());
    }

    @Test
    @DisplayName("Posta kontrolü, başvuru PENDING değilse ERP'den önce yapılmaz")
    void zatenIncelenmisBasvuruPostayiSinamaz() {
        // Sıra önemli: önce "bu başvuru işlenebilir mi", sonra posta. Aksi hâlde
        // her çift tıklama boşuna bir SMTP el sıkışması açardı.
        basvuru.setStatus(RegistrationRequest.STATUS_APPROVED);

        assertThrows(RegistrationReviewService.AlreadyReviewedException.class,
                () -> service.yeniCariAcarakOnayla(personel, 42L, veri(), null));

        verify(inviteService, never()).gonderilebilirMi();
    }

    private CariHesap cari() {
        CariHesap c = new CariHesap();
        c.setCariKod("M-500");
        c.setCariUnvan1("MEVCUT FİRMA LTD");
        return c;
    }
}
