package com.simge.adminbackend.erp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simge.adminbackend.erp.repository.CariHesaplarRepository;

/** Cari kodu önerisi (ADR D-127). */
class CariKodUreticiTest {

    private CariHesaplarRepository repository;
    private CariKodUretici uretici;

    @BeforeEach
    void setUp() {
        repository = mock(CariHesaplarRepository.class);
        uretici = new CariKodUretici(repository, "M-");
    }

    @Test
    @DisplayName("En büyük numaranın bir fazlasını önerir")
    void enBuyugunBirFazlasi() {
        when(repository.kodlariBul("M-")).thenReturn(List.of("M-001", "M-838", "M-999"));
        assertEquals("M-1000", uretici.oner("M-"));
    }

    @Test
    @DisplayName("Basamak sayısını seriden alır — M-001 serisinde M-002 önerir")
    void basamakKorunur() {
        when(repository.kodlariBul("M-")).thenReturn(List.of("M-001"));
        assertEquals("M-002", uretici.oner("M-"));
    }

    @Test
    @DisplayName("Numarasız kodlar seriyi bozmaz (M-TEST01 gibi)")
    void numarasizKodlarAtlanir() {
        // M-TEST01 sondaki '01' yüzünden numara gibi görünür ama 838'den küçük,
        // dolayısıyla seriyi geriye çekmez. Asıl sınav: patlamamak.
        when(repository.kodlariBul("M-")).thenReturn(List.of("M-838", "M-TEST01", "M-DENEME"));
        assertEquals("M-839", uretici.oner("M-"));
    }

    @Test
    @DisplayName("Seri boşsa 1'den başlar")
    void bosSeri() {
        when(repository.kodlariBul("X-")).thenReturn(List.of());
        assertEquals("X-001", uretici.oner("X-"));
    }

    @Test
    @DisplayName("Önek verilmezse yapılandırmadaki varsayılan kullanılır")
    void varsayilanOnek() {
        when(repository.kodlariBul("M-")).thenReturn(List.of("M-005"));
        assertEquals("M-006", uretici.oner(null));
        assertEquals("M-006", uretici.oner("  "));
    }

    @Test
    @DisplayName("Önek büyük harfe çevrilir — kodlar tabloda büyük harf")
    void onekBuyukHarf() {
        when(repository.kodlariBul("M-")).thenReturn(List.of("M-005"));
        assertEquals("M-006", uretici.oner("m-"));
    }

    @Test
    @DisplayName("Vergi numarası sorgusu rakam dışını ayıklar")
    void vergiNoNormalize() {
        when(repository.kodlariVergiNoIle("1234567890")).thenReturn(List.of("M-100"));
        assertEquals(List.of("M-100"), uretici.ayniVergiNoluKodlar("123 456 78-90"));
    }

    @Test
    @DisplayName("Boş vergi numarasıyla sorgu yapılmaz — tüm tabloyu taramasın")
    void bosVergiNoSorgulanmaz() {
        assertTrue(uretici.ayniVergiNoluKodlar("").isEmpty());
        assertTrue(uretici.ayniVergiNoluKodlar(null).isEmpty());
        assertTrue(uretici.ayniVergiNoluKodlar("---").isEmpty());
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .kodlariVergiNoIle(anyString());
    }
}
