package com.simge.adminbackend.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.mockito.ArgumentCaptor;

import com.simge.adminbackend.appdb.model.AppSetting;
import com.simge.adminbackend.appdb.repository.AppSettingRepository;
import com.simge.adminbackend.erp.model.Depo;
import com.simge.adminbackend.erp.repository.DepoDolulukRepository;
import com.simge.adminbackend.erp.repository.DepoRepository;

/**
 * Depo değiştirme kuralları (ADR D-152, D-156).
 *
 * <h2>Sınır nerede</h2>
 * <p>
 * Servis <b>yalnızca var olmayan depoyu</b> reddeder — seçilecek bir şey
 * olmadığı için. Boş depo, stok hareketi olmayan depo ve Mikro'da iptal
 * işaretli depo <b>seçilebilir</b>; panel uyarıyı gösterir, kararı yönetici
 * verir.
 * </p>
 *
 * <p>
 * Bu, D-152'deki davranışın düzeltilmiş hâli. Orada "vitrini boşaltır"
 * gerekçesiyle bir eşik konmuştu ve o eşiği kod uyduruyordu; depoyu tanıyan
 * insanın elini bağlıyordu. Uyarı bilgi vermek içindir, karar vermek için değil.
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

    /** Ayara yazılan değer. */
    private String yazilanDeger() {
        ArgumentCaptor<AppSetting> yakala = ArgumentCaptor.forClass(AppSetting.class);
        verify(settingRepository).save(yakala.capture());
        return yakala.getValue().getValue();
    }

    // --- Reddedilen: yalnızca var olmayan depo ---------------------------

    @Test
    @DisplayName("Mikro'da olmayan depo reddedilir — seçilecek bir şey yok")
    void olmayanDepoReddedilir() {
        when(depoRepository.findFirstByNo(99)).thenReturn(Optional.empty());

        assertThrows(WarehouseService.GecersizDepo.class, () -> service.degistir(99, 1L));
        verify(settingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Depo 0 reddedilir ve sebebi söylenir")
    void sifirReddedilir() {
        when(depoRepository.findFirstByNo(0)).thenReturn(Optional.empty());

        WarehouseService.GecersizDepo e = assertThrows(
                WarehouseService.GecersizDepo.class, () -> service.degistir(0, 1L));

        // "Depo bulunamadı" demek sebebi gizlerdi: 0 fiyat tarafında çalışıyor
        // gibi görünür ama STOK_HAREKETLERI'nde hiç geçmez.
        assertTrue(e.getMessage().contains("0 kullanılamaz"), e.getMessage());
        verify(settingRepository, never()).save(any());
    }

    // --- Kabul edilen: uyarılı olanlar dahil ------------------------------

    @Test
    @DisplayName("Boş depo SEÇİLEBİLİR; uyarı döner ama engellenmez")
    void bosDepoSecilebilir() {
        mikroda(depo(17, "SANAL DEPO", false));
        doluluk(Map.of(17, new DepoDolulukRepository.Doluluk(0, 0)));

        WarehouseService.DepoSatiri sonuc = service.degistir(17, 42L);

        assertEquals(17, sonuc.no());
        assertEquals("17", yazilanDeger(), "seçim gerçekten kaydedilmeli");
        assertNotNull(sonuc.uyari(), "uyarı yine de dönmeli");
        assertTrue(sonuc.uyari().contains("fiyatı olan ürün"), sonuc.uyari());
    }

    @Test
    @DisplayName("Stok hareketi olmayan depo SEÇİLEBİLİR; uyarı döner")
    void stoksuzDepoSecilebilir() {
        // Sinsi durum: ürünler vitrinde görünür ama hepsi stoksuz çıkar ve
        // "Sepete Ekle" açılmaz (D-137). Yönetici bunu bilerek seçebilmeli.
        mikroda(depo(15, "BATIKENT BUYUK DEPO", false));
        doluluk(Map.of(15, new DepoDolulukRepository.Doluluk(1214, 24)));

        WarehouseService.DepoSatiri sonuc = service.degistir(15, 1L);

        assertEquals("15", yazilanDeger());
        assertTrue(sonuc.uyari().contains("Sepete Ekle"), sonuc.uyari());
    }

    @Test
    @DisplayName("Mikro'da iptal işaretli depo SEÇİLEBİLİR")
    void iptalliDepoSecilebilir() {
        mikroda(depo(9, "KAPANMIS DEPO", true));
        doluluk(Map.of(9, new DepoDolulukRepository.Doluluk(5000, 5000)));

        service.degistir(9, 1L);

        assertEquals("9", yazilanDeger());
    }

    @Test
    @DisplayName("Dolu depo kabul edilir, uyarı dönmez ve kim değiştirdi yazılır")
    void doluDepoKabulEdilir() {
        mikroda(depo(4, "ELMADAG 3", false));
        doluluk(Map.of(4, new DepoDolulukRepository.Doluluk(7338, 5990)));

        WarehouseService.DepoSatiri sonuc = service.degistir(4, 42L);

        assertEquals(4, sonuc.no());
        assertEquals("ELMADAG 3", sonuc.ad());
        assertNull(sonuc.uyari());

        ArgumentCaptor<AppSetting> yakala = ArgumentCaptor.forClass(AppSetting.class);
        verify(settingRepository).save(yakala.capture());
        assertEquals(AppSetting.KEY_WAREHOUSE, yakala.getValue().getKey());
        assertEquals("4", yakala.getValue().getValue());
        assertEquals(42L, yakala.getValue().getUpdatedBy(), "kim değiştirdi yazılmalı");
    }

    // --- Liste -----------------------------------------------------------

    @Test
    @DisplayName("Listede her deponun doluluğu ve varsa uyarısı var")
    void listedeSayilarVeUyarilar() {
        mikroda(depo(4, "ELMADAG 3", false), depo(17, "SANAL DEPO", false));
        doluluk(Map.of(
                4, new DepoDolulukRepository.Doluluk(7338, 5990),
                17, new DepoDolulukRepository.Doluluk(0, 0)));

        List<WarehouseService.DepoSatiri> satirlar = service.depolar();

        WarehouseService.DepoSatiri iyi = satirlar.stream()
                .filter(s -> s.no() == 4).findFirst().orElseThrow();
        WarehouseService.DepoSatiri bos = satirlar.stream()
                .filter(s -> s.no() == 17).findFirst().orElseThrow();

        assertEquals(7338, iyi.fiyatliUrun());
        assertEquals(5990, iyi.stokluUrun());
        assertNull(iyi.uyari(), "dolu depoda uyarı olmamalı");

        // Panel bunu gösteriyor ama satırı seçilemez YAPMIYOR.
        assertNotNull(bos.uyari());
        assertTrue(!bos.uyari().isBlank());
    }

    @Test
    @DisplayName("Doluluk bilgisi dönmeyen depo uyarı alır, yine de seçilebilir")
    void bilinmeyenDepoUyariAlir() {
        mikroda(depo(3, "ELMADAG 2", false));
        doluluk(Map.of());

        assertNotNull(service.depolar().get(0).uyari());

        // ve yazma yine de çalışıyor
        service.degistir(3, 1L);
        assertEquals("3", yazilanDeger());
    }
}
