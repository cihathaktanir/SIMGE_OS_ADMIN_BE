-- ---------------------------------------------------------------------------
--  Var olan bir cariye EK adres satiri. (ADR D-173)
--
--  insert-cari-adres.sql'den farki: orasi cari acilisinda calisiyor ve
--  adr_adres_no = 1 SABIT yaziyor. Burada numara PARAMETRE, cunku ikinci,
--  ucuncu ... adres eklenirken MAX+1 hesaplaniyor.
--
--  NUMARA 1'DEN BASLAMIYOR OLABILIR. Olculdu: 1.982 aktif satirin 205'inde
--  adr_adres_no = 0. Yani "yeni adres = mevcut sayi + 1" varsayimi yanlis;
--  cagiran taraf MAX(adr_adres_no)+1 hesapliyor (bkz. CariWriter.yeniAdres).
--  (cari_kod, adres_no) cifti Mikro'da UNIQUE indeksli
--  (NDX_CARI_HESAP_ADRESLERI_02), dolayisiyla es zamanli iki ekleme sessizce
--  bozmaz, ikincisi hata verir.
--
--  TELEFON UC ALANA BOLUNMUS geliyor. Mikro boyle tutuyor (olculdu: dolu
--  satirlarin hepsi '90' / '312' / '3976498'). Tek alanda birlestirip
--  yazmak, adr_tel_no1 10 karakter oldugu icin normal bir cep numarasinda
--  (11 hane) truncation hatasi veriyor.
--
--  Sabitler tahmin degil: sablon, adr_adres_no = 1 olan 1753 ana adres
--  satirindan olculdu (yontem D-127'de).
--
--  adr_RECno IDENTITY. adr_RECid_RECno insert aninda bilinemez; 0 yazilip
--  ayni islemde SCOPE_IDENTITY() ile guncellenir (bkz. CariWriter).
-- ---------------------------------------------------------------------------
INSERT INTO CARI_HESAP_ADRESLERI (
    [adr_RECid_DBCno],
    [adr_RECid_RECno],
    [adr_SpecRECno],
    [adr_iptal],
    [adr_fileid],
    [adr_hidden],
    [adr_kilitli],
    [adr_degisti],
    [adr_checksum],
    [adr_create_user],
    [adr_create_date],
    [adr_lastup_user],
    [adr_lastup_date],
    [adr_special1],
    [adr_special2],
    [adr_special3],
    [adr_cari_kod],
    [adr_adres_no],
    [adr_aprint_fl],
    [adr_cadde],
    [adr_mahalle],
    [adr_sokak],
    [adr_Semt],
    [adr_Apt_No],
    [adr_Daire_No],
    [adr_posta_kodu],
    [adr_ilce],
    [adr_il],
    [adr_ulke],
    [adr_Adres_kodu],
    [adr_tel_ulke_kodu],
    [adr_tel_bolge_kodu],
    [adr_tel_no1],
    [adr_tel_no2],
    [adr_tel_faxno],
    [adr_tel_modem],
    [adr_yon_kodu],
    [adr_uzaklik_kodu],
    [adr_temsilci_kodu],
    [adr_ozel_not],
    [adr_ziyaretperyodu],
    [adr_ziyaretgunu],
    [adr_gps_enlem],
    [adr_gps_boylam],
    [adr_ziyarethaftasi],
    [adr_ziygunu2_1],
    [adr_ziygunu2_2],
    [adr_ziygunu2_3],
    [adr_ziygunu2_4],
    [adr_ziygunu2_5],
    [adr_ziygunu2_6],
    [adr_ziygunu2_7],
    [adr_efatura_alias],
    [adr_eirsaliye_alias]
) VALUES (
    0,
    0,
    0,
    0,
    32,
    0,
    0,
    0,
    0,
    :mikroKullanici,
    :simdi,
    :mikroKullanici,
    :simdi,
    '',
    '',
    '',
    :cariKod,
    :adresNo,
    0,
    :cadde,
    :mahalle,
    :sokak,
    :semt,
    :aptNo,
    :daireNo,
    :postaKodu,
    :ilce,
    :il,
    :ulke,
    :adresKodu,
    :telUlke,
    :telBolge,
    :telNo,
    '',
    '',
    '',
    '',
    0,
    '',
    '',
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    0,
    '',
    ''
)
