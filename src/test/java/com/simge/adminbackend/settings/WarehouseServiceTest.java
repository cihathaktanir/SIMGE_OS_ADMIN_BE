package com.simge.adminbackend.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.simge.adminbackend.appdb.model.AppSetting;
import com.simge.adminbackend.appdb.repository.AppSettingRepository;
import com.simge.adminbackend.erp.model.Depo;
import com.simge.adminbackend.erp.repository.DepoDolulukRepository;
import com.simge.adminbackend.erp.repository.DepoRepository;

/**
 * Depo değiştirme kuralları (ADR D-152).
 *
 * <p>
 * Bu, panelde yapılabilecek en yıkıcı ayar değişikliği: vitrindeki <b>her
 * fiyat</b> ve <b>her stok</b> seçilen depodan okunuyor. Testler tek bir şeyi
 * koruyor — vitrini boşaltacak ya da yanlış fiyat gösterecek bir depo
 * kaydedilemez.
 * </p>
 *
 * <p>
 * Sayılar uydurma değil, Mikro'da ölçüldü: depo 4 (ELMADAG 3) 7.338 fiyatlı /
 * 5.990 stoklu ürünle en dolusu; depo 17 (SANAL DEPO) 0 / 0.
 * </p>
 */
class WarehouseServiceTest {

    private AppSettingRepository settingRepository;
    private DepoRepository depoRepository;
    private DepoDolulukRepository dolulukRepository;
    private WarehouseService service;

    @BeforeEach
    void setUp() {
        settingRepository = mock(AppSettingRepository.class);
        depoRepository = mock(DepoRepository.class);
        dolulukRepository = mock(DepoDolulukRepository.class);
        service = new WarehouseService(settingRepository, depoRepository, dolulukRepository);

        when(settingRepository.findById(AppSetting.KEY_WAREHOUSE)).thenReturn(Optional.empty());
    }

    private Depo depo(int no, String ad, boolean iptal) {
        Depo d = new Depo();
        d.setRecno((long) no);
        d.setNo(no);
        d.setAdi(ad);
        d.setIptal(iptal);
        return d;
    }

    private void mikroda(Depo... depolar) {
        when(depoRepository.secilebilirler()).thenReturn(List.of(depolar));
        for (Depo d : depolar) {
            when(depoRepository.findFirstByNo(d.getNo())).thenReturn(Optional.of(d));
        }
    }

    private void doluluk(Map<Integer, DepoDolulukRepository.Doluluk> harita) {
        when(dolulukRepository.hepsi()).thenReturn(harita);
    }

    // --- Reddedilen seçimler ---------------------------------------------

    @Test
    @DisplayName("Depo 0 reddedilir — Mikro'da 0 numaralı depo yok")
    void sifirReddedilir() {
        // Fiyat tarafında 0 "genel liste" demek ve çalışıyor gibi görünür; ama
        // STOK_HAREKETLERI'nde 0 depolu hareket yok, yani her ürün stoksuz çıkar.
        WarehouseService.GecersizDepo e = assertThrows(
                WarehouseService.GecersizDepo.class, () -> service.degistir(0, 1L));

        assertTrue(e.getMessage().contains("0 kullanılamaz"), e.getMessage());
        verify(settingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mikro'da olmayan depo reddedilir")
    void olmayanDepoReddedilir() {
        when(depoRepository.findFirstByNo(99)).thenReturn(Optional.empty());

        assertThrows(WarehouseService.GecersizDepo.class, () -> service.degistir(99, 1L));
        verify(settingRepository, never()).save(any());
    }

    @Test
    @DisplayName("İptal edilmiş depo reddedilir")
    void iptalliDepoReddedilir() {
        mikroda(depo(9, "KAPANMIS DEPO", true));
        doluluk(Map.of(9, new DepoDolulukRepository.Doluluk(5000, 5000)));

        assertThrows(WarehouseService.GecersizDepo.class, () -> service.degistir(9, 1L));
        verify(settingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Boş depo reddedilir — SANAL DEPO seçilirse vitrin tamamen boşalırdı")
    void bosDepoReddedilir() {
        mikroda(depo(17, "SANAL DEPO", false));
        doluluk(Map.of(17, new DepoDolulukRepository.Doluluk(0, 0)));

        WarehouseService.GecersizDepo e = assertThrows(
                WarehouseService.GecersizDepo.class, () -> service.degistir(17, 1L));

        assertTrue(e.getMessage().contains("fiyatı olan ürün"), e.getMessage());
        verify(settingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Fiyatı olan ama stok hareketi olmayan depo reddedilir")
    void stoksuzDepoReddedilir() {
        // Bu durum sinsi: ürünler vitrinde GÖRÜNÜR ama hepsi stoksuz çıkar ve
        // "Sepete Ekle" hiç açılmaz. D-137'de tam olarak bu yaşandı.
        mikroda(depo(15, "BATIKENT BUYUK DEPO", false));
        doluluk(Map.of(15, new DepoDolulukRepository.Doluluk(1214, 24)));

        WarehouseService.GecersizDepo e = assertThrows(
                WarehouseService.GecersizDepo.class, () -> service.degistir(15, 1L));

        assertTrue(e.getMessage().contains("Sepete Ekle"), e.getMessage());
        verify(settingRepository, never()).save(any());
    }

    // --- Kabul edilen seçim ----------------------------------------------

    @Test
    @DisplayName("Dolu depo kabul edilir ve ayara yazılır")
    void doluDepoKabulEdilir() {
        mikroda(depo(4, "ELMADAG 3", false));
        doluluk(Map.of(4, new DepoDolulukRepository.Doluluk(7338, 5990)));

        WarehouseService.DepoSatiri sonuc = service.degistir(4, 42L);

        assertEquals(4, sonuc.no());
        assertEquals("ELMADAG 3", sonuc.ad());

        org.mockito.ArgumentCaptor<AppSetting> yakala =
                org.mockito.ArgumentCaptor.forClass(AppSetting.class);
        verify(settingRepository).save(yakala.capture());

        assertEquals(AppSetting.KEY_WAREHOUSE, yakala.getValue().getKey());
        assertEquals("4", yakala.getValue().getValue());
        assertEquals(42L, yakala.getValue().getUpdatedBy(), "kim değiştirdi yazılmalı");
    }

    // --- Liste ------------------------------------------------------------

    @Test
    @DisplayName("Listede her deponun doluluğu ve seçilebilirliği var")
    void listedeSayilarVar() {
        mikroda(depo(4, "ELMADAG 3", false), depo(17, "SANAL DEPO", false));
        doluluk(Map.of(
                4, new DepoDolulukRepository.Doluluk(7338, 5990),
                17, new DepoDolulukRepository.Doluluk(0, 0)));

        List<WarehouseService.DepoSatiri> satirlar = service.depolar();

        WarehouseService.DepoSatiri iyi = satirlar.stream()
                .filter(s -> s.no() == 4).findFirst().orElseThrow();
        WarehouseService.DepoSatiri bos = satirlar.stream()
                .filter(s -> s.no() == 17).findFirst().orElseThrow();

        assertTrue(iyi.uygun());
        assertEquals(7338, iyi.fiyatliUrun());
        assertEquals(5990, iyi.stokluUrun());

        // Panel bu satırı seçtirmiyor ve sebebini gösteriyor.
        assertFalse(bos.uygun(), "boş depo seçilebilir görünmemeli");
        assertTrue(bos.uyari() != null && !bos.uyari().isBlank());
    }

    @Test
    @DisplayName("Doluluk bilgisi hiç dönmeyen depo seçilemez sayılır")
    void bilinmeyenDepoUygunDegil() {
        // Sorgu o depo için satır döndürmediyse "bilmiyoruz" demektir; iyimser
        // davranmak, bilinmeyen bir depoyu seçilebilir göstermek olurdu.
        mikroda(depo(3, "ELMADAG 2", false));
        doluluk(Map.of());

        assertFalse(service.depolar().get(0).uygun());
    }
}
