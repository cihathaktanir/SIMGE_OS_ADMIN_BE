# Mimari Kararlar — SIMGE_OS_ADMIN_BE

Bu repo 2026-08-12'de, yönetim panelinin vitrin backend'inden ayrılmasıyla açıldı.

**Numaralandırma üç repoda ORTAK.** D-100…D-121 arası kararlar vitrin reposunda
(`SIMGE_OS_BE/docs/decisions.md`) yazıldı ve bu servisi de bağlıyor — özellikle:

- **D-100** — Mikro şeması bize ait değil; `ddl-auto=none`.
- **D-104** — ERP'ye yazmak `ReadOnlyRepository` sayesinde derleme hatası. **Bu serviste de
  geçerli**; yazma açıldığında taban sınıfa değil, ayrı ve dar bir arayüze eklenecek.
- **D-106 / D-107** — Sunucu tarafı oturum (JWT yok), site tamamen girişe kapalı.
- **D-112 / D-113 / D-114** — E-posta gönderimi, parola kuralları, çok dilli iletiler.
- **D-120 / D-121** — Davet mekanizması ve onayın davete dönüşmesi (D-121 artık D-124 ile
  değiştirildi).

Aşağıdakiler bu ayrımla birlikte alınan kararlar. Üç repoda da aynı metin duruyor;
ilgilendirdikleri kod üç repoya da dağılmış durumda.

---

## D-122 — Yönetim paneli AYRI bir backend'e taşındı (`SIMGE_OS_ADMIN_BE`, intranet)

**Status:** Accepted · 2026-08-12

**Context:** Panelin kapsamı büyüdü. Artık yalnızca kayıt başvurularını değil; carileri
(yeni cari giriş talebi, e-posta ekleme — yani **ERP'ye yazma**), Simge iç kullanıcılarını,
sipariş ilerlemesini, vitrin ayarlarını (ana sayfa JSON'u), backend'in kullanacağı depoyu ve
fiyat listesini yönetecek. Soru şuydu: bunlar vitrin backend'inin (`SIMGE_OS_BE`) içinde mi
dursun?

**Decision:** Hayır. Yönetim, kendi süreci olan ayrı bir Spring Boot servisine taşındı:
`SIMGE_OS_ADMIN_BE`, port 8081, **intranet**. `SIMGE_OS_BE` içindeki `/api/admin/**` yüzeyi,
`AdminBootstrap` ve `CariHesaplarRepository.search` kaldırıldı. İki servis `SIMGE_OS_APP`
veritabanını **paylaşır**, tablo sahipliği ayrıdır (`db/app` ↔ `db/admin`).

**Rationale:**
- **En güçlü gerekçe `ReadOnlyRepository` (D-104).** Bugün ERP'ye yazmak *derleme hatası*;
  `save`/`delete` API'de hiç yok. Aynı backend'e tek bir yazılabilir repository eklemek bu
  korumayı "imkânsız"dan "hangi repository'yi kullandığına bağlı"ya düşürürdü. Projedeki en
  sert kontrolü, en riskli özelliği eklemek için gevşetmek olurdu.
- **Maruz kalma yüzeyi.** `SIMGE_OS_BE` internete açık. Bugün tamamen ele geçirilse bile ERP
  bozulamaz, çünkü kodda yazma yolu yok. Yazma yeteneğini o sürecin içine koymak, "ERP'ye
  yazdığı için bu sistem intranette çalışacak" kararıyla doğrudan çelişirdi. Aynı JVM'de iki
  güven bölgesi olmaz; Spring Security ile ayırmak "aynı binada kilitli oda"dır, ağ
  seviyesinde ayırmak kadar iyi değildir.
- **Kimlik bilgisi ayrımı.** Tek uygulama = tek bağlantı havuzu = tek MSSQL kullanıcısı. Ayrı
  servis, vitrinin Mikro girişini SELECT-only yapmaya izin verir; yazma yetkisi yalnızca
  intranetteki serviste, yalnızca gereken tablolarda olur. Uygulama katmanı hata yapsa bile
  veritabanı reddeder.
- **Veri hassasiyeti, yazmadan bağımsız olarak.** Firma listesi, cari arama, personel
  yönetimi internete açık bir süreçten servis edilmemeli. Ayrımın gerekçesi sadece yazma
  değil.
- **Taşımanın en ucuz olduğu an buydu:** taşınan yüzey iki controller + iki servisti.

**Veritabanı neden paylaşılıyor (API ile ayrılmıyor):** Kullanıcılar, davetler, başvurular ve
vitrin ayarları tek yerde dursun; iki servis arasına senkronizasyon API'si yazmak gerekmesin.
Bedeli şema sahipliğinin bulanıklaşması, karşılığı **tablo bazlı sahiplik**: her tablonun
migration'ı yalnızca tek repoda yaşar.

**ERP'ye yazma HENÜZ AÇILMADI.** Bu serviste de repository'ler `ReadOnlyRepository` üzerinden
türüyor. Açıldığında bu taban sınıfa `save` eklenmeyecek; yazma gereken tablo için ayrı, dar
bir arayüz yazılacak — "hangi repository yazabiliyor" sorusu grep'lenebilir kalsın diye.

**Açık soru (doğrulanmadı):** Mikro'nun tablolarına doğrudan INSERT, ERP'nin kendi iş
mantığını atlar (RECno sıra üreticileri, denetim kolonları, ilişkili kayıtlar, trigger'lar).
Yeni cari açmak tek tabloya satır atmak değildir. Mikro'nun desteklenen bir entegrasyon yolu
(API / entegratör / veri aktarım modülü) olup olmadığı **bayiye sorulmalı**. O netleşene
kadar: cari **açma** elle kalır, yazma `cari_EMail` gibi tek kolonluk düşük riskli alanlarla
başlar.

**Mitigation if violated:** `SIMGE_OS_BE` içine yönetim ucu eklenmemeli. Her iki repoda da
`ReadOnlyRepository`'ye yazma metodu eklenmemeli. Panel internete açılmamalı.

**Revisit when:** Mikro'nun desteklenen entegrasyon yolu öğrenildiğinde; ilk gerçek ERP
yazma işi yapılacağında; iki servisin paylaştığı `SIMGE_OS_APP` şemasında çakışma yaşandığında.

---

## D-123 — Panel kullanıcıları ayrı tabloda, kullanıcı adıyla giriyor, geçici parolayla açılıyor

**Status:** Accepted · 2026-08-12

**Context:** Panelde Simge iç kullanıcıları olacak (depocu, satışçı, muhasebe) ve rol bazlı
görünürlük gerekiyor. Panel yöneticisinin hesabı **hızlıca** açabilmesi istendi: e-posta
gerekmeden, doğrulama beklemeden, takma ad gibi bir kullanıcı adıyla.

**Decision:** Personel `SIMGE_STAFF_USER` tablosunda (vitrinin `SIMGE_USER`'ından ayrı),
roller `SIMGE_STAFF_ROLE`'de (çoklu), oturumlar `SIMGE_STAFF_SESSION`'da (ayrı çerez:
`SIMGE_ADMIN_SESSION`). Giriş **kullanıcı adıyla**. Hesap açılırken sistem 14 karakterlik
geçici parola üretir, ekranda **bir kez** gösterilir ve `must_change_password` bayrağıyla
işaretlenir.

**Rationale:**
- **Neden ayrı tablo:** (1) Farklı kimlik — müşteri e-postayla, personel kullanıcı adıyla
  girer. (2) Farklı güven bölgesi — `SIMGE_USER` internete açık servisin okuduğu tablo;
  personel kimlik bilgisi orada dursaydı vitrindeki bir açık yönetim hesaplarını da kapsardı.
  (3) Tek tablo olsaydı "personel vitrine giremez" kuralı bir `WHERE` koşuluna kalırdı;
  unutulduğu an yetki tırmanması.
- **Neden kullanıcı adı:** Depo görevlisinin kurumsal e-posta adresi olmak zorunda değil;
  olmayan bir şeyi uydurmak gerekirdi. Bedeli: parola sıfırlama kendi kendine yapılamaz,
  yöneticiye düşer — kapalı bir iç ağda, sayısı onlarla ölçülen kullanıcı için doğru takas.
- **Neden yalnızca ASCII kullanıcı adı:** Giriş ekranında elle yazılan bir alanda ı/i ya da
  ş/s ayrımı destek yükünden başka bir şey üretmiyor. Ad soyad ayrı alanda ve orada Türkçe
  serbest. Küçük harfe indirgeme `Locale.ROOT` ile — Türkçe locale'de `"ADMIN".toLowerCase()`
  noktasız `admın` üretirdi.
- **Neden parolayı sistem üretiyor:** Elle yazılan geçici parolalar her yerde aynı kalıba
  düşüyor (`Simge2026`, firma adı + yıl). Alfabede karışan karakterler yok (`0/O`, `1/l/I`);
  parola telefonda sözlü aktarılacak. Yönetici tek tuşla kopyalıyor.
- **Neden `must_change_password` sunucuda zorlanıyor:** Geçici parolayı en az iki kişi biliyor
  (açan yönetici + kullanıcı). Arayüzde yönlendirme yalnızca tarayıcıyı ikna ederdi; o
  parolayla API'ye doğrudan istek atılabildiği sürece bayrak süs olurdu. `StaffPasswordChangeGate`
  filtresi `/api/auth/**` dışındaki her isteği 403 `password_change_required` ile durduruyor.
- **Neden çoklu rol:** Bir kişi hem depo hem satış olabiliyor. Tek `role` kolonu olsaydı ilk
  çift rol ihtiyacında "DEPO_SATIS" gibi birleşik değerler türerdi.
- **Rolsüz hesap giriş yapamaz:** Panelde hiçbir şey göremezdi; girişi kabul edip her ekranda
  403 vermek kullanıcıya "sistem bozuk" hissi verirdi.
- **Son aktif yönetici korunuyor:** Rolü alınamaz, hesabı kapatılamaz. Aksi halde panele kimse
  personel ekleyemez ve kilit yalnızca veritabanından açılırdı.
- **Hesap silinmiyor, kapatılıyor:** "Kim ne yaptı" kaydı, hesap kaybolunca anlamını yitirir.

**Bunun vitrindeki karşılığı neden farklı:** Müşteri tarafında onay bir **davete** dönüşüyor
ve parolayı kullanıcı kendisi kuruyor (D-120/D-124) — çünkü orada parola posta kutusundan
geçerdi. Personelde parola elden geçiyor ve e-posta zorunlu değil; geçici parola bu yüzden
kabul edilebilir.

**Yan etki — `SIMGE_COMPANY_INVITATION.invited_by` belirsizleşti:** Artık iki farklı tablonun
id'sini taşıyabiliyor. `invited_by_type` (`CUSTOMER` | `STAFF`) sütunu eklendi (V12). Denetim
kaydının yanlış olması, hiç olmamasından kötüdür.

**Mitigation if violated:** Personel `SIMGE_USER`'a taşınmamalı. `must_change_password`
denetimi arayüze bırakılmamalı. Geçici parola açık saklanmamalı ya da ikinci kez
gösterilmemeli. Son yönetici koruması kaldırılmamalı.

**Revisit when:** Rol kapsamı gerektiğinde ("yalnızca 3 numaralı depo"); personel sayısı
kendi kendine parola sıfırlamayı gerektirecek kadar arttığında; kurumsal kimlik sağlayıcı
(LDAP/AD) devreye alındığında.

---

## D-124 — Kayıt başvurusu onayı panele taşındı; davet üretimi iki servise bölündü

**Status:** Accepted · 2026-08-12 · **Supersedes D-121**

**Context:** D-121, kayıt başvurusu onayını `SIMGE_OS_BE` içinde `/api/admin/**` altında
konumlandırmıştı. D-122 ile yönetim ayrı bir servise taşınınca bu akışın yeri değişti.

**Decision:** Onay/ret artık `SIMGE_OS_ADMIN_BE` içinde `/api/registration-requests` altında
ve `ADMIN` **ya da** `SATIS` rolü yeterli. Davet kaydını ve e-postayı panel üretiyor; daveti
**kabul eden** taraf vitrin backend'i olarak kaldı.

**Rationale:**
- Onay akışı Mikro'dan cari okuyor, davet üretiyor ve ileride "yeni cari açma talebi"ne
  dönüşecek — hepsi intranette kalması gereken işler.
- `SATIS` rolünün de görmesi: başvuruyu değerlendiren kişi çoğu zaman müşteriyi tanıyan
  satışçı, sistemi yöneten kişi değil.
- **Kabul akışı neden vitrinde kaldı:** Daveti kabul eden kişi bir müşteri ve hesabını
  vitrinde açıyor; parola orada belirleniyor. Panelin müşteri parolasıyla hiçbir teması
  olmamalı. Bağlantı da vitrini gösteriyor (`simge.site-url`) — panel intranette, oraya
  işaret eden bir bağlantı kimsenin açamayacağı bir adres olurdu.
- **`SecretCodes` iki serviste kopya ve öyle kalmalı:** Token'ı panel üretip
  `token_hash`'e yazıyor, vitrin aynı özeti hesaplayıp satırı buluyor. Algoritmadaki bir
  değişiklik gönderilmiş bütün davet bağlantılarını sessizce geçersiz kılar.
- `MailTemplates`'in tamamı kopyalanmadı, yalnızca davet şablonu: kullanılmayan şablon,
  güncellenmeyen şablondur.
- `RegistrationRequest.reviewed_by` sütununun anlamı değişti (artık `SIMGE_STAFF_USER.id`).
  Geçmiş kayıtlarda eski anlam geçerli; sütunda FK yok, denetim için okunuyor.

**Mitigation if violated:** Hesap açma/parola belirleme panele taşınmamalı. Cari kodu
doğrulaması kaldırılmamalı. `SecretCodes.hash` tek taraflı değiştirilmemeli.

**Revisit when:** Yeni cari açma talebi akışı eklendiğinde; ret bildirimi istendiğinde.

---


## D-125 — Kilitlenmiş panelden çıkış: ortam değişkeniyle açılışta parola sıfırlama

**Status:** Accepted · 2026-08-12

**Context:** D-123 personelde e-postayı zorunlu tutmadı (depo görevlisinin kurumsal adresi
olmak zorunda değil). Bunun kaçınılmaz sonucu: "şifremi unuttum" bağlantısı yok, parolayı
ancak **başka bir yönetici** sıfırlayabiliyor. Aynı ADR ayrıca son aktif yöneticinin rolünün
alınmasını ve hesabının kapatılmasını engelliyor — ama **"son yönetici parolasını unutursa"**
için hiçbir yol bırakmamıştı.

Bu gerçek bir kilitlenme; kurgu değil, kullanımda yaşandı. Tek çare veritabanına elle BCrypt
özeti yazmaktı ve kimse yanında BCrypt üreteciyle dolaşmıyor.

**Decision:** `SIMGE_ADMIN_RESET=<kullanıcı adı>` ortam değişkeniyle servis bir kez
başlatıldığında, o hesabın parolası sıfırlanır; yeni geçici parola bir kereye mahsus log'a
yazılır ve `must_change_password` açılır. Değişken normalde **boştur**.

**Rationale:**
- **E-posta gerektirmiyor.** D-123'ün "personelde e-posta zorunlu değil" kararını bozmadan
  çalışıyor. Alternatifi (personele zorunlu e-posta + sıfırlama bağlantısı) olmayan adreslerin
  uydurulmasına yol açardı.
- **Yetki seviyesi doğru ve yeni saldırı yüzeyi açmıyor.** Bunu çalıştırabilmek için sunucuda
  ortam değişkeni tanımlayabilmek gerekiyor. O yetkiye sahip olan kişi zaten
  `SIMGE_APP_DB_PASSWORD`'ü okuyup aynı satırı SQL ile güncelleyebilir. Mekanizma yetki
  eklemiyor, yalnızca meşru yolu kullanılabilir hâle getiriyor.
- **Mevcut makineyi yeniden kullanıyor.** İlk yönetici hesabının açılışındaki desenin
  (`StaffBootstrap`) aynısı: geçici parola üretimi, log'a bir kez yazma,
  `must_change_password` ile ilk girişte değiştirme zorunluluğu. Yeni bir kod yolu değil.
- **Sessiz arka kapı değil.** Sıfırlama gerçekleştiğinde log'a yüksek sesle düşüyor ve
  "değişkeni şimdi kaldırın" uyarısı veriyor.

**Kasıtlı olarak yapmadıkları — bunlar mekanizmanın sınırı:**
- **Hesap AÇMAZ.** Kullanıcı adı yanlış yazılırsa hiçbir şey yapmaz ve hata log'lar. Açsaydı
  bir yazım hatası sessizce yeni bir yönetici doğururdu.
- **Rol VERMEZ.** Verseydi bayrak "herhangi bir kullanıcıyı yönetici yap" anahtarına
  dönüşürdü — asıl tehlikeli olan bu olurdu, parola sıfırlamak değil.
- **Kapalı hesabı AÇMAZ.** Bu iki durumda parola yine sıfırlanır ama giriş çalışmaz; sebebi
  log'da açıkça yazılır ki kullanıcı "parolayı denedim yine olmadı" diye dönmesin.

**Bu ASIL çözüm değil.** Asıl çözüm **her zaman en az iki yönetici hesabı** bulundurmak; biri
unutursa diğeri panelden sıfırlar ve bu değişkene hiç ihtiyaç olmaz. Mekanizma, o disiplinin
tutmadığı durum için son çare.

**Riski:** Değişken sunucuda unutulursa **her açılışta** parolayı sıfırlar ve kullanıcı kendi
belirlediği parolayla giremez. Karşı önlem: sıfırlama her gerçekleştiğinde log'a büyük harfle
"DEĞİŞKENİ ŞİMDİ KALDIRIN" uyarısı düşüyor ve README'de aynı madde var. Kalıcı bir zarar
vermiyor (parola her seferinde yeniden üretiliyor, hesap kaybolmuyor) ama fark edilmesi
gecikirse kafa karıştırır.

**Mitigation if violated:** Bu mekanizmaya rol verme ya da hesap açma yeteneği eklenmemeli.
Sıfırlama sessizleştirilmemeli (log'lar kaldırılmamalı). `must_change_password` zorunluluğu
atlanmamalı — geçici parola log'da duruyor.

**Revisit when:** Kurumsal kimlik sağlayıcıya (LDAP/AD) geçildiğinde bu mekanizma gereksizleşir.
Personele zorunlu e-posta gelirse normal sıfırlama akışı yazılabilir ve bu son çare kalabilir.

---

## D-127 — ERP'ye yazma açıldı: cari açma ve boş e-posta doldurma (yalnızca panelden, yalnızca onayla)

**Status:** Accepted · 2026-08-12 · D-104'ü **daraltarak** günceller (kaldırmaz)

**Context:** D-122 bu servisi tam da bunun için ayırmıştı: "ileride ERP'ye sınırlı yazma
yapacak, o yüzden internete açık vitrinden ayrı ve intranette". O gün geldi. İki somut ihtiyaç:

1. **Cari kaydı olmayan başvurular.** `NO_CARI` dalındaki başvuruyu onaylamak için cariyi
   önce Mikro'da elle açmak gerekiyordu. Panel "cariyi açın, sonra kodu buraya yazın"
   diyordu — iki ayrı programda, iki kez veri girişi.
2. **Cari var ama e-postası yok.** 2.440 aktif carinin yalnızca 252'sinde e-posta var. Kalan
   ~%90 için adres zaten başvuruda geliyor; onaylarken ERP'ye de yazılabilir.

**Kararı vermeden önce şema ölçüldü** (varsayımla ERP'ye satır yazılmaz):

| Ölçüm | Sonuç |
|---|---|
| `CARI_HESAPLAR` sütun sayısı | 183 |
| Şemanın `NOT NULL` tuttuğu | 2 (`cari_RECid_DBCno`, `cari_RECid_RECno`) |
| Varsayılanı olan sütun | **0** |
| **NULL içeren mevcut satır** | **2440'ta 1** — o da elle atılmış bir deneme kaydı |
| Gerçekten değişken sütun | 15 |
| Sabit sütun | 168 (94'ü `''`, 59'u `0`, 15'i özel sabit) |
| Tetikleyici / yabancı anahtar | yok |

Kritik bulgu son satırlarda: **şema neredeyse hiçbir şeyi zorunlu tutmuyor ama Mikro
tutuyor.** Beş sütunluk bir INSERT teknik olarak çalışır ve geriye 170'ten fazla NULL sütun
bırakır; Mikro'nun yazdığı hiçbir satır öyle değil. Bütünlük veritabanında değil, ERP
uygulamasında — yani doğru satırı yazmak tamamen bizim sorumluluğumuz.

**Decision:**

**1. `ReadOnlyRepository` olduğu gibi kalıyor.** ERP tarafındaki her JPA repository'si hâlâ
ondan türüyor; `cariHesaplarRepository.save(...)` bugün de derleme hatası. Yazma yolu bilerek
JPA'nın **dışında**, tek bir sınıfta: `CariWriter`. Böylece "hangi kod ERP'ye yazabiliyor"
sorusunun cevabı tek dosya ve grep'lenebilir kalıyor. D-104'e `save` eklemek tüm ERP'yi tek
hamlede yazılabilir yapardı — asıl kaçınılan buydu ve hâlâ kaçınılıyor.

**2. Yapabildiği iki şey var, üçüncüsü yok.** Yeni cari açmak ve var olan bir carinin **boş**
e-posta alanını doldurmak. Silme yok, başka alan güncelleme yok, toplu işlem yok.

**3. Dolu e-postanın üzerine yazılmıyor.** `UPDATE ... WHERE LTRIM(RTRIM(cari_EMail)) = ''`.
Mikro'da elle girilmiş bir adresi ezmek, ERP'yi doğru kabul eden her akışı (fatura,
mutabakat) sessizce bozardı. Doluysa 0 satır etkilenir ve çağıran bunu görür.

**4. INSERT elle yazılmadı, ölçümden üretildi.** 182 sütunun tamamı yazılıyor; sabitler her
sütun için mevcut satırlarda en çok geçen değer. Üretilen dosyalar
`src/main/resources/erp/insert-cari.sql` ve `insert-cari-adres.sql`. Şema değişirse
(Mikro sürüm yükseltmesi) yeniden üretilmeli.

**5. Ana adres satırı aynı işlemde yazılıyor.** `CARI_HESAPLAR`'da adres yok; orada yalnızca
`cari_fatura_adres_no = 1` ve `cari_sevk_adres_no = 1` işaretçileri var, gerçek adres
`CARI_HESAP_ADRESLERI`'nde `adr_adres_no = 1` satırında. Adres yazılamazsa cari de geri
alınıyor — yoksa faturada adresi boş bir cari kalır ve kimse fark etmez.

**6. İki alan tahmin edilmiyor, personele soruluyor:**
- **`cari_kod`** — şemadan türetilemeyen tek alan. Mevcut veride tek bir düzen yok: `S-` (950),
  `M-` (878), harf önekli gruplar, doğrudan vergi numarası kullanılmış kayıtlar. Sistem
  sıradaki boş numarayı **öneriyor**, kodu personel onaylıyor.
- **`cari_efatura_fl`** — VKN'ye bağlı bir mükellefiyet. Mevcut carilerde dağılım %59/%41;
  çoğunluk yeterince baskın değil ve yanlış değer fatura kesilemez hâle getirir. Onay
  ekranında açıktan işaretleniyor, varsayılanı kapalı.

**7. Otomatik yazma yok.** Hiçbir başvuru personel dokunmadan ERP'ye satır yazdıramıyor.
Vitrin backend'i bu alanları yalnızca **topluyor**; yazma yetkisi orada yok ve olmayacak.

**Rationale:** Alternatif, Mikro'nun kendi entegrasyon arayüzünü kullanmaktı. Tercih
edilmedi çünkü elimizde belgelenmiş bir arayüz yok (D-122'nin açık sorusu hâlâ açık: Mikro
bayisine sorulmalı). Bu karar o soruyu kapatmıyor — desteklenen bir yol çıkarsa `CariWriter`
onun arkasına geçirilebilir; çağıranların hiçbiri değişmez, çünkü yazma yolu zaten tek
noktadan geçiyor.

**Risk 1 — dağıtık işlem yok.** Onay `appTransactionManager`'da, `CariWriter` Mikro'nun kendi
transaction'ında; ERP yazması dönüşte commit'lenmiş oluyor. Sonrasında bir şey patlarsa
(örneğin davet e-postası gönderilemezse) başvuru `PENDING` kalır ama cari Mikro'da açılmış
olur. **Sessiz değil:** ikinci denemede aynı vergi numarası bulunur, personel `cari_zaten_var`
uyarısıyla "var olan cariye bağla" yoluna yönlendirilir. XA kurmak, tek bir onay ekranı için
ödenecek bedelden çok daha pahalı olurdu.

**Risk 2 — mükerrer cari.** Başvuru "cari yok" diye sınıflandırıldıktan sonra biri Mikro'da
elle açmış olabilir. Karşı önlem: cari açmadan hemen önce vergi numarası tekrar sorgulanıyor.

**Risk 3 — şablon eskimesi.** Sabitler bugünün verisinden ölçüldü. Mikro sürüm yükseltmesi
sütun eklerse yeni sütunlar INSERT'te yer almaz ve NULL kalır. Karşı önlem: `CariWriterCanliTest`
gerçek şemada yazıp **geri alarak** tek bir NULL sütun kalmadığını doğruluyor. Varsayılan
olarak kapalı (`-Dsimge.erp.canli-test=true`), çünkü canlı veritabanı istiyor.

**Doğrulama:** Üretilen satır gerçek bir cariden (M-838) yalnızca iki sütunda farklı, ikisi de
ölçülen çoğunluk değeri. Adres satırında tek fark `adr_tel_bolge_kodu`: ölçümde `'312'`
çıkmıştı (müşterilerin çoğu Ankara), her yeni cariye Ankara alan kodu yazmak yanlış olacağı
için boş bırakıldı.

**Bu ADR'siz yapılmaması gerekenler:** `ReadOnlyRepository`'ye yazma metodu eklemek.
`CariWriter`'a üçüncü bir yazma metodu eklemek. Dolu e-posta korumasını kaldırmak.
`cari_efatura_fl`'ye veriden tahmin edilen bir varsayılan koymak. Vitrin backend'ine
herhangi bir ERP yazma yolu açmak.

**Revisit when:** Mikro'nun desteklenen entegrasyon arayüzü öğrenildiğinde (D-122 açık
sorusu) — `CariWriter` onun arkasına geçirilir. Ayrıca Mikro sürüm yükseltmesinde şablon
yeniden üretilmeli.

## D-142 — Ürün görselleri: baytlar veritabanında, servis disk önbelleğinden, adres içerik özetinden

**Tarih:** 2026-08-14
**Durum:** Kabul edildi
**Bağlam:** Mikro görsel tutmuyor; 8.238 ürünün tamamı vitrinde yer tutucu gösteriyordu
(backlog madde 14). Kullanıcının önerisi: *"veritabanında tutalım, admin panelinde
arayarak ürün seçsin ve resmini ordan yükleyip değiştirebilsin"*, ardından
*"veritabanında blob olarak tutmak çok mu maliyetli, dosyadan okumak yazmak daha mı
hızlı"* ve son olarak *"boyut sınırı getirelim, thumbnail oluşturalım, listede
thumbnail, detayda asıl resim"*.

**Ölçüm önce geldi.** Vitrinde görsellerin gerçekte kaç piksel çizildiğine bakıldı:

| Nerede | Çizilen | Adet/sayfa |
|---|---|---|
| Ana sayfa karosu | 190×190 | 54 |
| Ana sayfa ikinci şerit | 236×236 | 15 |
| Liste karosu | 306×306 | 12 |
| **Ürün detayı** | **340×340** | 1 |
| Kategori ikonu | 34×34 | 24 |

Sonuç sürpriz oldu: **sitedeki en büyük ürün görseli 340 piksel.** Yani "detayda asıl
resmi göster" derken o "asıl resim" ham yükleme olamazdı — 3000 piksellik telefon
fotoğrafını 340 piksellik kutuya basmak tam da kaçınılmak istenen israftı.

**Karar — iki türev, ham dosya YOK:**

| Türev | Piksel | Gerekçe |
|---|---|---|
| `thumb` | 600 | 306'lık en büyük karoyu DPR 2'de karşılar |
| `detail` | 1200 | 340'lık kutuyu DPR 3'te karşılar, yakınlaştırmaya pay bırakır |

Ham dosya saklansaydı veritabanı ~25 GB'a çıkardı (8.238 × ~3 MB); iki türevle
**~1,5 GB**. 1200 piksel en büyük kullanımın 3,5 katı, geri dönmek için fazlasıyla yer.

**Boyut sınırı bir kabul kriteri değil.** 10 MB'ı geçen reddediliyor ama bu yalnızca
kötüye kullanım koruması; geçen her dosya **küçültülüyor**. Sadece reddetseydik
operatörün 9,9 MB'lık fotoğrafı geçer ve 9,9 MB saklanırdı. Ayrıca dosya boyutu sınırı
"decompression bomb"u engellemiyor — 10 MB'lık bir dosya 30.000×30.000 piksele
açılabilir — bu yüzden ayrıca 50 megapiksel sınırı var ve **çözmeden önce** denetleniyor.

**Blob mu dosya mı — cevap "ikisi de":**

Kullanıcının ısrarı haklı bir sezgiye dayanıyordu: tek yedek, öksüz dosya yok, işlemsel
tutarlılık. Ham okuma hızı zaten belirleyici değil (SQL Server da aynı diske yazıyor).
Asıl maliyetler başkaydı ve bu kurulumda somut:

1. **Bağlantı havuzu** — Hikari ayarı yok, yani havuz başına 10. Her görsel akışı bir
   bağlantı tutar ve o havuz fiyat/stok sorgularıyla paylaşılıyor.
2. **Tampon havuzu** — iki veritabanı da **aynı SQL Server örneğinde**
   (`localhost:1433`), yani aynı tampon havuzunu paylaşıyorlar. Görsel sayfaları
   `STOKLAR`'ın sıcak sayfalarını dışarı atardı: *ürün sorguları, görsel trafiği
   yüzünden yavaşlardı.*

Çözüm ikisini de veriyor: **baytların tek doğru kaynağı veritabanı, servis ilk okumada
yazılan atılabilir bir disk önbelleğinden.** Önbelleği silmek veri kaybı değil.

**Bu tasarımın beklenmedik ikinci faydası:** panel (intranet, D-122) ile vitrinin
**paylaşımlı klasöre ihtiyacı kalmadı.** Panel veritabanına yazıyor, her vitrin sunucusu
kendi yerel önbelleğini kendisi oluşturuyor. Servisler ayrı makinelere çıkarsa da çalışır.

**Adres içerik özetinden üretiliyor:** `/api/images/{sha256}/thumb.jpg`. Sonuçları:

- Bir yıllık `immutable` önbellek **güvenli** — adres aynıysa baytlar da aynı. Tarayıcı
  bir görseli ömründe bir kez indiriyor.
- Panelden görsel değişince hash de değişiyor, yani *"resmi güncelledim ama tarayıcıda
  eskisi duruyor"* sorunu hiç doğmuyor. `?v=2` gibi el işine gerek yok.
- Aynı fotoğraf beş ürüne yüklenirse **bir kez** saklanıyor (içerik adresli). Gıda
  toptancılığında aynı ürünün farklı gramajları sık.

**İki tablo:** `SIMGE_IMAGE_BLOB` (hash birincil anahtar, baytlar) ve
`SIMGE_IMAGE_LINK` (hangi görsel kimin, sıra 0 birincil). Ayrı olmalarının sebebi
servis ucunun **tek bir birincil anahtar okumasıyla** cevap verebilmesi.

**Biçim JPEG, WebP değil.** WebP ~%25 daha küçük olurdu ama yerel (JNI) kütüphane
istiyor ve bu, yükleme sırasında JVM'i düşürebilecek tek bileşen olurdu. Görseller
değişmez adreslerle sonsuza kadar önbellekte kaldığı için fark **istemci başına bir
kereliktir**. `format` sütunu ve URL'deki uzantı sayesinde WebP ileride **ek** bir
biçim olarak gelebilir; göç gerekmez.

**Uç kamuya açık.** Vitrinin geri kalanı giriş istiyor (D-107) ama gizli olan fiyat ve
stok; ürün fotoğrafı değil. Açık olması önüne CDN ya da nginx konabilmesini sağlıyor —
asıl kazanç orada. Adresler tahmin edilemez (256 bit özet).

**Toplu yükleme bir kolaylık değil zorunluluk.** 8.238 üründe tek tek yükleme aylar
sürerdi. Dosya adından SKU eşleştiriliyor (`ABC123.jpg` → `ABC123`), sondaki sıra
ekleri atılıyor (`ABC123-2.jpg`, `ABC123 (2).jpg`), eşleşmeyenler **atlanıp
raporlanıyor** — kısmi başarı normal, parti geri alınmıyor. Eşleşme büyük/küçük harf
duyarsız ve tüm SKU'lar **tek sorguda** çözülüyor.

**`sto_kod` anahtar, `sto_RECno` değil:** operatör dosyayı SKU ile adlandırıyor ve
RECno Mikro'nun iç sıra numarası.

**Güvenlik ayrıntısı:** hash bir dosya yoluna giriyor. Denetlenmezse `../` içeren bir
istek önbellek klasörünün dışına yazdırabilirdi; bu yüzden `[0-9a-f]{64}` kalıbı hem
serviste hem önbellekte zorunlu.

**Yol boyunca çıkan iki hata:**

- **`CHAR(64)` uygulamayı açılışta düşürdü** (`found [char], but expecting
  [varchar(64)]`). Sabit uzunluk mantıklı görünmüştü; ayrıca CHAR okurken değeri
  boşlukla doldurup karşılaştırmaları bozabilirdi. `NVARCHAR(64)`'e çevrildi.
- Görsel detay ucunda da bağlanmalıydı; liste ucundaki toplu okuma o yolu kapsamıyor.
  **D-137'de stokla yaşanan hatanın aynısı** — listede görünen bir ürünün detayında
  yer tutucu çıkardı.

**Vitrin şablonunda iki ölü satıcı bileşeni çıktı.** Ürün detayındaki ana görsel
yuvası, görsel yüklenir yüklenmez **bomboş** kaldı. Sebep tek değil ikiydi ve ikisi de
bugüne kadar hiç çalışmamıştı — çünkü hiçbir ürünün galerisi yoktu, o dal ilk kez
şimdi çalıştı:

- `owl-carousel-o` kendini açılışta ölçüyor; kap o an yerleşmemiş oluyor.
  Ölçüldü: `.product-slick` 443x380 iken `owl-stage`, `owl-item` ve
  dilimlerin hepsi **genişlik 0**. Karusel bir daha toparlamıyor.
- İçindeki `lib-ngx-image-zoom` aynı sebeple **0x0** kuruluyor.

Ana görsel artık karusel değil düz bir `<img>`; seçimi alttaki küçük görsel şeridi
yapıyor (`activeSlide`). Kaybedilen tek şey tıklayınca büyütme — kimse istemedi ve
görselin hiç görünmemesine yeğdir. **Aynı bileşenler diğer 8 ürün-detay düzeninde de
duruyor;** tema o düzenlere geçerse orada da değiştirilmeli.

**Doğrulama:**

| Adım | Sonuç |
|---|---|
| Birim testleri (küçültme, oran, saydam PNG, hash, ret) | 8/8 geçti |
| Gerçek şemada entegrasyon testi (yazma, tekrarlamama, galeri, birincil) | 3/3 geçti |
| Panelden gerçek yükleme (178 kB JPEG) | **178.572 → 19.181 bayt (9,3×)** |
| Vitrin `thumb` (girişsiz) | 200, 4.712 bayt, `immutable` |
| Vitrin `detail` | 200, 14.469 bayt |
| ETag ile ikinci istek | **304** |
| Yol kaçışı denemesi | 400 |
| Bilinmeyen variant | 404 |
| Disk önbelleği | iki dosya da yazıldı |
| Ürün listesi DTO'su | `product_thumbnail` + `product_galleries` dolu |
| Ürün detay ucu | ikisi de dolu |
| Vitrin ürün detayı (tarayıcı) | ana görsel **340x340**, şerit çalışıyor |
| Vitrin liste sayfası (tarayıcı) | `thumb.jpg` 306 px çiziliyor |

**Mitigation if violated:** Ham dosya veritabanına yazılmamalı — 25 GB'lık bir MSSQL
yedeği bu tasarımı geri alır. Disk önbelleği "tek doğru kaynak" muamelesi görmemeli;
silinebilir olması tasarımın parçası. Öksüz bayt silen otomatik bir iş **bilerek
yazılmadı**: ham dosya saklanmadığı için yanlış bir sorgunun geri dönüşü yok.

**Revisit when:**
- Vitrin görsellere `loading="lazy"` ve `width`/`height` özniteliklerini
  vermiyor olabilir; ana sayfada 54 görsel var, düzen kaymasını önlemek için bakılmalı.
- Kategori görselleri (12 ana + 73 alt) altyapıya dahil ama panelde ekranı yok;
  ikonlar 34 piksel, `thumb` fazlasıyla yeter.
- Detay sayfasında `thumb` zaten önbellekte olduğu için anında gösterilip `detail`
  arkada değiştirilebilir — bedava algılanan hız, henüz yapılmadı.
- WebP/AVIF eklemek isterse: `format` sütunu ve uzantı hazır, göç gerekmiyor.
- Canlıda `SIMGE_IMAGE_BLOB` büyüdükçe yedek süresi izlenmeli.

---
---
*Yeni ADR eklerken sıra numarası üç repoda ortak ilerler. IDler değişmez; silinen bir karar boşluk bırakır.*


## D-147 — Posta gönderilemeyecekse ERP'ye hiç dokunma; e-posta ayarları `config/` altında

**Tarih:** 2026-08-18
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı paneldan yeni cari açarak bir başvuruyu onaylarken
*"E-posta gönderimi yapılandırılmamış; davet gönderilemedi."* hatası aldı ve ardından
*"mail göndermeden de cariyi kaydetme"* dedi.

### 1. Öksüz cari sorunu

`RegistrationReviewService` iki yerde önce ERP'ye yazıp sonra davet gönderiyordu:

- `yeniCariAcarakOnayla` → `cariWriter.yeniCari(...)` sonra `inviteService.invite(...)`
- `approve` → `cariWriter.epostaYaz(...)` sonra `inviteService.invite(...)`

Davet başarısız olunca `MailUnavailableException` fırlıyor ve `appTransactionManager`
işlemi geri sarıyor — **ama Mikro başka bir veritabanı** ve oradaki yazma çoktan
COMMIT'lenmiş oluyor. Sonuç: başvuru `PENDING`, cari ortada.

Bu teorik değil, **yaşandı**: `M-2001` "Cihat test" 18.08.2026 09:50'de Mikro'da
açılmış, başvuru #12 `PENDING` kalmış, `created_cari_kod` boş. Kullanıcının isteğiyle
silindi (cari + adres satırı, tek transaction; hareket/sipariş/stok bağı yoktu,
yedeği `scratchpad/m2001-yedek-*.json`).

**Karar: postayı ERP'ye yazmadan ÖNCE sına.** `MailService.hazirMi()` eklendi —
`isEnabled()`'ın ötesinde `JavaMailSenderImpl.testConnection()` ile sunucuya bağlanıp
kimlik doğrulamayı da deniyor, yani yanlış uygulama şifresini ve ulaşılamayan sunucuyu
da yakalıyor. `CompanyInviteService.gonderilebilirMi()` bunu dışarı veriyor ve her iki
onay metodu ERP'ye dokunmadan önce çağırıyor.

Fırlatılan istisna gönderim anındakiyle **aynı** (`MailUnavailableException`), panel
mesajı değişmiyor — değişen tek şey hatanın ERP yazmasından **önce** olması.

**Yarış aralığını kapatmaz, daraltır.** Kontrolden sonra gönderime kadar sunucu
düşerse cari yine açılmış olur. Gerçek çözüm dağıtık işlem (XA) olurdu; tek bir onay
ekranı için ödenecek bedelden çok daha pahalı. Kalan durumda ikinci deneme
`CariZatenVar` ile personeli "var olan cariye bağla" yoluna yönlendiriyor.

Sıra da önemli: **önce** "başvuru işlenebilir mi", **sonra** posta sınaması. Aksi
hâlde her çift tıklama boşuna bir SMTP el sıkışması açardı — test bunu sabitliyor.

### 2. E-posta ayarlarının yeri

`SIMGE_OS_ADMIN_BE/config/application.properties` oluşturuldu; `SIMGE_OS_BE`'de zaten
vardı. Spring Boot `./config/application.properties` dosyasını kendiliğinden okuyup
`src/main/resources/application.properties`'i **ezer**. `.gitignore` ikisinde de bu
dosyayı dışarıda tutuyor; yanına yer tutuculu `application.properties.example` kondu.

**Tek Gmail hesabı iki servisi de karşılıyor** (vitrin: OTP + şifre sıfırlama; panel:
davet). Admin tarafında bu dosya **zorunlu**: `spring.datasource.password` ve
`simge.app-datasource.password` `${...}` biçiminde ve varsayılansız, dosya yoksa
uygulama hiç açılmıyor. Vitrinde DB parolaları doğrudan `application.properties`'te
(D-103).

Öncelik sırası (yüksekten alçağa): komut satırı → sistem özelliği → **ortam
değişkeni** → `config/application.properties` → `classpath:application.properties` →
`${...:}` varsayılanı. Ortam değişkeniyle başlatılan bir süreçte config dosyası devre
dışı kalır; sorun ararken buna dikkat edilmeli.

### Doğrulama

| Adım | Sonuç |
|---|---|
| `mvnw test` (yönetim) | **82 test, 0 hata** (5 atlandı — canlı DB isteyenler) |
| Posta yokken yeni cari | `cariWriter.yeniCari` **hiç çağrılmıyor** (birim testi) |
| Posta yokken ERP e-posta yazma | `cariWriter.epostaYaz` **hiç çağrılmıyor** (birim testi) |
| Zaten incelenmiş başvuru | posta sınaması yapılmıyor (birim testi) |
| İki servis config dosyasıyla açılış | ikisi de UP, "gönderim KAPALI" uyarısı yok |
| Gerçek Gmail gönderimi | `E-posta gönderildi: konu='Simge — şifre sıfırlama'` |
| M-2001 silindi | cari 0, adres 0, aktif cari 2.440 |

**Kapsam notu:** olumsuz yol (posta yokken ERP'ye dokunulmaması) **birim testiyle**
kanıtlandı, canlı ortamda denenmedi — canlı deneme başarısız olsaydı Mikro'da yeni bir
çöp cari bırakacaktı.

**Mitigation if violated:** ERP yazması posta sınamasının önüne alınırsa öksüz cari
sorunu birebir geri gelir. `hazirMi()` yalnızca `isEnabled()`'a indirgenirse yanlış
şifre/ulaşılamayan sunucu durumları yine ERP'ye yazdıktan sonra patlar.

**Revisit when:**
- Sipariş yazma açılırsa (backlog 18) aynı sıra sorunu orada da doğar; sipariş ERP'ye
  yazılmadan önce yan etkilerin sınanması gerekir.
- `testConnection()` her onayda bir SMTP el sıkışması demek. Onay sıklığı artarsa
  sonucu kısa süreli önbelleğe almak gerekebilir.
- Gmail uygulama şifresi bu turda konuşma geçmişine düştü; döndürülmesi öneriliyor.
- Kalan yarış aralığı için telafi mekanizması (posta başarısızsa açılan cariyi işaretle
  ya da personele "şu cari açıldı ama davet gitmedi" bildirimi) yazılmadı.

---


## D-149 — Kayıt/davet akışında veri tekrarı, parola tekrarı ve şahıs-firma ayrımı

**Tarih:** 2026-08-19
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı davet bağlantısıyla hesap açarken beş şey bildirdi: telefon
ikinci kez soruluyor, KVKK onayları ikinci kez soruluyor, parola tek kez alınıyor,
girdiği TCKN `cari_vdaire_no`ya yazılmış, ve şahıs/firma ayrımı hiç yapılmıyor.

### 0. Biri hata değildi — TCKN'nin yeri

Ölçüldü: `cari_vdaire_no` **2.217 kayıtta dolu; 1.712'si 10 haneli (VKN),
485'i 11 haneli (TCKN)**. Adı çağrıştırsa da `cari_VergiKimlikNo` alanı
**0 kayıtta** dolu. Yani Mikro ikisini de aynı alanda tutuyor ve bizim yazdığımız
yer mevcut 485 şahıs kaydıyla birebir aynı. Değiştirilmedi.

Ama şikâyetin altındaki tespit doğruydu: **şahıs/firma ayrımı hiçbir yerde
yapılmıyordu.** Mikro'da da açık bir bayrak yok — ayrım hane sayısında gizli.

### 1. Şahıs / firma: tahmin değil, seçim

Kullanıcı kararıyla **açıkça soruluyor** (segment düğmesi: Limited/Anonim Şirket —
Şahıs Şirketi). Hane sayısından türetmek de mümkündü ama sormak, doğru uzunluğu ve
sağlamayı zorunlu kılmayı sağlıyor.

Seçime göre değişenler: alan etiketi (Vergi Numarası ↔ T.C. Kimlik Numarası), ikon,
`maxlength`, ipucu metni ve 2. adımdaki "Firma unvanı" → "Unvan / Ad Soyad".

**Doğrulama artık gerçek.** Önceki desen `^[0-9 .\-]{10,20}$` idi: 12, 15, hatta
20 haneyi kabul ediyor ve hiçbir sağlama yapmıyordu. Şimdi tam 10 (VKN) ya da tam
11 (TCKN) hane ve **her ikisinde sağlama toplamı** zorunlu.

Algoritmalar uydurma örneklerle değil **Mikro'daki 2.196 gerçek numarayla**
sınandı: VKN'de **%98,5**, TCKN'de **%90,9** geçti. Geçmeyenler `1111111111`,
`11111111112` gibi sahte kayıtlar — yani sağlama tam da üretilmesini istemediğimiz
şeyi yakalıyor. Sağlama toplamı yanlış negatif üretmediği için sert engel yapıldı:
geçerli bir numara her zaman geçer.

### 2. Parola tekrarı

İki formda da (kayıt self-servis dalı + davet kabul) tekrar alanı eklendi;
eşleşme kontrolü `passwordMatchValidator` ile ve hata **tekrar alanının altında**
gösteriliyor. Öncesinde tek kez alınıyordu: yazım hatası yapan kullanıcı hesabına
giremiyor ve tek çıkışı parola sıfırlama oluyordu.

### 3. Telefon tekrarı

`SIMGE_COMPANY_INVITATION` tablosunda telefon alanı **yoktu**; panel daveti
oluştururken başvurudan yalnızca e-posta ve ad-soyad taşınıyordu. `V18` ile
`phone NVARCHAR(30) NULL` eklendi, panel `request.getPhone()` geçiriyor,
`/api/auth/invitation/validate` döndürüyor ve form **hazır dolu** geliyor.
Meslektaş davetinde null — orada ortada başvuru yok. "(isteğe bağlı)" ibaresi de
kaldırıldı.

### 4. Onay tekrarı

`ConsentService.zorunlularTamamMi(email)` eklendi ve `/validate` yanıtına
`consent_needed` olarak yansıtıldı. Kutular yalnızca gerekiyorsa gösteriliyor.

**"Hep sor" da "hiç sorma" da yanlıştı:** aynı ekran meslektaş davetinde de
kullanılıyor ve oradan gelen kişi hiç başvurmamış, hiç onay vermemiştir — ona
sormak zorunlu.

**Metin sürümü de karşılaştırılıyor**, yalnızca "onay var mı" değil. Sözleşme ya da
aydınlatma metni onay verildikten sonra değiştiyse kişi yeni metni görmemiş sayılır
ve yeniden sorulur; eski sürüme verilmiş onayla yeni metni dayatmak KVKK m.10
bilgilendirme yükümlülüğünü karşılamaz. Yürürlükteki metin bulunamazsa güvenli
taraf seçiliyor (yeniden sor).

Kutular gizlenirken **doğrulayıcıları da kaldırılıyor** — yalnızca `@if` ile
gizlemek, `requiredTrue` formda kaldığı için hiç gönderilemeyen bir forma yol
açardı.

### Doğrulama

| Adım | Sonuç |
|---|---|
| Sağlama algoritmaları, Mikro'nun 2.196 gerçek numarası | VKN %98,5 · TCKN %90,9 (kalanlar sahte kayıtlar) |
| Tüzel + sağlaması hatalı 10 hane | "Girdiğiniz numara geçerli değil" |
| Tüzel + 11 hane | "Vergi kimlik numarası 10 haneli olmalı" |
| Şahıs seçilince etiket | "T.C. Kimlik Numarası" |
| Şahıs + 10 hane | "TC kimlik numarası 11 haneli olmalı" |
| Tüzel + geçerli VKN (`2920015146`) | hata yok |
| `typecheck` + üretim derlemesi | temiz |
| İki backend derleme | temiz |
| Konsol istisnası | 0 |

**Mitigation if violated:** Sağlama toplamı gevşetilirse `1111111111` gibi kayıtlar
yeniden üretilmeye başlar. Onay kutuları gizlenirken doğrulayıcıları kaldırılmazsa
form hiç gönderilemez. `consent_needed` metin sürümüne bakmayı bırakırsa güncellenen
sözleşme kimseye gösterilmez.

**Revisit when:**
- **Davet kabul ekranı uçtan uca denenmedi.** Telefon ön doldurma ve onay atlama
  gerçek bir davet bağlantısıyla test edilmeli; birim testi de yok (vitrinin Karma
  paketi ayrı bir sebeple kırık, bkz. backlog 33).
- Sunucu tarafı `mukellef_turu` bilgisini **almıyor**; doğrulama şu an yalnızca
  istemcide. Sunucuda da hane/sağlama kontrolü yapılmalı — istemciye güvenilmez.
- Şahıs şirketinde de vergi dairesi soruluyor ve bu **doğru** (485 TCKN kaydının
  384'ünde vdaire dolu), ama "Firma unvanı" dışındaki metinler hâlâ firma dilinde.
- `cari_VergiKimlikNo` alanı boş duruyor; ileride e-fatura tarafı isterse burası
  kullanılabilir.

---

---

## D-151 — Panel ürün araması tüm kelimeleri arar

**Tarih:** 2026-08-19
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı görsel yüklerken ürünün tam adını vitrinden kopyalayıp
panele yapıştırdı ve ürünü bulamadı: *"Admin panelinde ürün görselleri yüklerken
kullandığım search algoritması hiç iyi değil... şunu denesene TOZ SEKER 50KG"*.

**Sebep:** `ImageAdminController.urunAra` yalnızca **ilk kelimeyi** kullanıyordu.
Kodda bunu meşrulaştıran bir yorum bile vardı — *"Tek kelimeyle sınırlı: bu bir
katalog araması değil, 'şu ürünü bul' kutusu"*. Oysa `TurkishSearch.tokenize`'ın
kendi dokümanı "koşullar AND'lenir" diyor: yardımcı sınıf doğru tasarlanmış,
çağıran taraf onu yok saymış.

Ölçüm: `TOZ SEKER 50KG` sorgusu `TOZ` olarak çalışıyor, katalogda "TOZ" içeren
**107 ürün** var ve sonuç sınırı 40. Aranan ürün sınırın dışında kalıyor; ekranda
toz biber, kabartma tozu, Calgon toz deterjan çıkıyor.

**Karar: tüm kelimeler AND'lenir.** Koşul **sayısı** girdiye bağlı olduğu için
sabit bir `@Query` yetmiyor; `StokAramaRepository` sorgu metnini kelime sayısına
göre kuruyor. **Kullanıcı girdisi sorguya gömülmüyor** — değişen tek şey koşul
sayısı, kelimelerin kendisi `:k0`, `:k1` … olarak bağlanıyor.

### Doğrulama

| Arama | Önce | Sonra |
|---|---|---|
| `TOZ SEKER 50KG` | 40 alakasız sonuç, ürün yok | **1 sonuç: `GIMAT-01673`** |

`TurkishSearchTest` (6 test) tam da bu kullanımı kilitliyor: tam ad yapıştırma,
fazla boşluk, ve Türkçe/ASCII eşleşmesinin **her iki yönü** ("SEKER" yazınca
"ŞEKER"i, "ŞEKER" yazınca "SEKER"i bulmalı).

**Not:** doğrulama sırasında PowerShell'de yazılan taklit sorgu "TOZ ŞEKER 50KG"
için 0 sonuç verdi ve bir an ikinci bir hata sanıldı. Java tarafında test
edilince uygulamanın doğru çalıştığı, hatanın betikte olduğu görüldü. Bu ADR'nin
kaydettiği ders: **taklit sorgu bir doğrulama aracı değil**; şüphe varsa asıl
kodun testi yazılır.

---

## D-152 — Vitrinin deposu panelden yönetilir; istemci depoyu seçemez

**Tarih:** 2026-08-19
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı: *"SIMGE_OS_BE nin kullandığı default depo seçimi admin
panelinden yapılsın gerekirse onuda veri tabanına yazıp okuyalım vitrin
backendidde sorgu öncesi oradan okusun. Burda hata yapılmaması çok önemli çünkü
ürünler ve fiyatlar depo bazlı bunu unutma yapıyı sağlam kur."*

Depo, sistemdeki en geniş etkili tek sayı: vitrindeki **her fiyat** ve **her
stok** ondan okunuyor (D-137). Ayar `application.properties`'te olduğu sürece
değiştirmek "dosyayı düzenle + sunucuyu yeniden başlat" demekti; bunu yapabilecek
kişi de operatör değil geliştiriciydi.

### 1. Ayar veritabanında: `SIMGE_SETTING`

Tek satırlık anahtar/değer tablosu (`V19__settings.sql`), `SIMGE_OS_APP` içinde.
**Panel yazar, vitrin okur.** Mikro'ya dokunulmuyor.

**Ayar BÖLÜNMEZ.** Bu tabloya "fiyat deposu" ve "stok deposu" diye iki anahtar
eklenmeyecek: ikisi ayrışırsa vitrin, o depoda olmayan bir ürünü başka bir
deponun fiyatıyla satar. `ProductServiceImpl` depoyu **bir kez** okuyup aynı
değişkeni hem fiyat hem stok sorgusuna veriyor; bunu bir test kilitliyor.

### 2. Okuma: önbellek yok, yedek var

`WarehouseSettingService` değeri **her sorgudan önce** okuyor. Önbellek
"panelden değiştirdim ama vitrin hâlâ eskisini gösteriyor" penceresi açardı ve o
pencerede vitrin **yanlış fiyat** gösterirdi. Okuma tek satırlık bir tablodan
birincil anahtarla yapılıyor; ürün sorgusunun yanında ölçülemeyecek kadar ucuz.

`simge.default-warehouse` **silinmedi** ama artık ayarın kaynağı değil: tablo
okunamazsa (göç uygulanmamış, bağlantı düşmüş) kullanılan son çare. Amaç,
ayarsız kalan vitrinin depo **0**'a düşmemesi — 0 numaralı depo `DEPOLAR`'da hiç
yok ve `STOK_HAREKETLERI`'nde geçmiyor, yani katalogdaki her ürün stoksuz
görünür. Yedek de bozuksa 4'e (ELMADAG 3) düşülüyor.

### 3. `?warehouse=` kaldırıldı

`GET /api/products` bu parametreyi kabul ediyor ve **verilirse onu
kullanıyordu**: adrese bir sayı ekleyen herkes başka bir deponun fiyat listesini
görebiliyor ve o fiyatla sepete ekleyebiliyordu. Vitrinin deposu bir
yapılandırma kararı, istemcinin seçimi değil. `ProductSearchCriteria`'daki alan
duruyor (repository'nin depoyu bir yerden alması gerekiyor) ama çağıran `null`
bırakıyor; değeri servis dolduruyor.

### 4. Yazma: panel, üç denetimle

`WarehouseService.degistir` sırayla bakıyor — depo `DEPOLAR`'da var mı, iptalli
mi, ve **içinde vitrine çıkacak ürün var mı**. Son denetim asıl kritik olanı;
Mikro'da ölçülen tablo:

| depo | ad | fiyatlı ürün | stok hareketli ürün |
|---|---|---|---|
| 4 | ELMADAG 3 | 7.338 | 5.990 |
| 7 | CAYYOLU | 7.351 | 4.436 |
| 15 | BATIKENT BÜYÜK | 1.214 | **24** |
| 17 | SANAL DEPO | **0** | **0** |

Depo 15 sinsi olanı: ürünler vitrinde **görünür** ama hepsi stoksuz çıkar ve
"Sepete Ekle" hiç açılmaz — D-137'de tam olarak bu yaşandı. Bu yüzden panel
seçenekleri isimle değil **sayılarla** gösteriyor; "SANAL DEPO" adı masum
görünür, yanındaki `0 / 0` onu seçilemez yapar.

Uç `/api/settings/**` yalnızca **ADMIN**. Görsel yüklemek ICERIK'e açık, depo
değiştirmek değil: bu bir içerik kararı değil, sistem kararı.

### Doğrulama

Dört servis ayakta, gerçek Mikro ve gerçek `SIMGE_OS_APP` ile:

| Kontrol | Sonuç |
|---|---|
| Panelden depo 4 → 5 | ayar yazıldı |
| Vitrin, **yeniden başlatılmadan** aynı sorgu | 3/3 üründe stok değişti (18→0, 18→5, 16→15) |
| `?warehouse=12` ile geçersiz kılma | **yok sayıldı**, sonuç hâlâ depo 5 |
| Depo 4'e geri alma | başlangıç değerleri birebir geri geldi |
| `PUT {"depo":17}` (SANAL DEPO) | **400** + "fiyatı olan ürün sayısı 0…" |
| `PUT {"depo":0}` | **400** + "Mikro'da 0 numaralı depo yok…" |
| Birim testleri | vitrin 11, panel 8 — hepsi geçti |

**Kalan boşluk:** vitrin ile panel arasında dağıtık kilit yok. İki operatör aynı
anda farklı depo seçerse son yazan kazanır. Tek kişilik bir ayar ekranı için
kilit mekanizması kurmanın bedeli faydasından yüksek; değişiklik INFO seviyesinde
"kim, ne zaman, hangi depodan hangisine" diye loglanıyor ve `updated_by`
tabloda saklanıyor.

---

## D-153 — Kategori görselleri; görsel adresi iki kökten gelir

**Tarih:** 2026-08-19
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı: *"Kategoriler içinde resim yükleyebilelim."*

### 1. Kategori görseli hiç görünmüyordu

Görsel altyapısı (D-142) `owner_type = 'CATEGORY'` bağını zaten destekliyordu ve
panelde yükleme ucu vardı. Eksik olan iki şeydi:

* `CategoryServiceImpl` `category_icon` alanını **hiç doldurmuyordu**;
* `AttachmentDTO`'da `originalUrl` alanı **yoktu**, oysa vitrin şablonu
  `category_icon.original_url` okuyor (Multikart'ın Laravel sözleşmesi).

İkincisi sessiz bozulmanın ders niteliğinde bir örneği: yalnızca `url` doldurmak
hiçbir hata üretmez, şablon tanımsız alanı yer tutucuya çevirir ve kimse
farkında olmaz. Bu yüzden alan eklendi ve testle değil **gerçek uçla** doğrulandı.

Görseller ürünlerdeki desenle **tek sorguda** okunuyor (`birincilGorseller`);
kategori başına sorgu 12 kategoride 12 gidiş-dönüş demekti. Alt kategoriler de
kapsanıyor.

Anahtar olarak **grup kodu** kullanılıyor, recno değil: recno Mikro'nun iç sıra
numarası ve ana/alt gruplarda çakışıyor; kod ise operatörün panelde gördüğü ve
kalıcı olan değer.

Görseli olmayan kategoride `category_icon` **null bırakılıyor** — boş bir nesne
göndermek "ikon var ama adresi yok" gibi görünürdü. Ürünler sayfasındaki çip
şeridi ikonu zaten yalnızca gerçekten varsa çiziyor (D-144).

### 2. Görsel adresi artık İKİ kökten gelebiliyor

Vitrinde bugüne kadar tek tür görsel adresi vardı: pakete gömülü dosyalar
(`/images/simge/...`), şablonlarda `StorageURL + image_url` ile çiziliyordu.
Panelden yükleme geldiğinde ikinci bir tür doğdu: veritabanındaki görseller
(`/api/images/<hash>/detail.jpg`). Eskisi gibi ön ek eklemek bunları
`/assets/api/images/...` yapar ve **404** üretirdi.

`gorselAdresi()` ayrımı kaynağa değil **adresin kendisine** bakarak yapıyor:
mutlak adres, `data:` ve `/api/` olduğu gibi geçiyor, kalanına `storageURL`
ekleniyor. `image-link` şablonundaki 12 tekrar eden ifade tek bir `gorsel`
getirici ile değiştirildi.

Panelde aynı ayrım **iki farklı sunucu** demek ve bu karıştırılmamalı:
`/api/images/...` vitrin backend'inde (8080), `/assets/images/...` vitrin
uygulamasında (4200). `core/vitrin.ts` ikisini ayrı sabit olarak tutuyor.

### Doğrulama

Gerçek servislerle, uçtan uca:

| Kontrol | Sonuç |
|---|---|
| Panelden SIGARALAR (kod 01) kategorisine görsel | yüklendi, 9.357 → 16.054 bayt (iki türev) |
| `GET /api/categories` | `category_icon.original_url = /api/images/<hash>/thumb.jpg` |
| Görseli olmayan 11 kategori | `category_icon` **null** |
| Bağ kaldırıldıktan sonra | alan yeniden **null** |

**Not:** doğrulama sırasında `/api/getcategories` denendi ve **500** alındı —
gerçek yol `/api/categories`. Adres hatasıydı, ama vitrin backend'inin var
olmayan bir yolu 404 yerine 500 ile karşılaması (`NoResourceFoundException`
`GlobalExceptionHandler`'a düşüyor) ayrı ve **açık bir kusur**. Bu ADR kapsamında
düzeltilmedi; kayda geçiriliyor.

---

## D-154 — Vitrin ana sayfası panelden yönetiliyor; bölümler eklenip silinmiyor

**Tarih:** 2026-08-19
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı: *"Admin panelinde veri tabanında tuttuğumuz bütün vitrin
konfigürasyonları editleyebilelim... Ürün tanıtımı olan banner-section ve
gift-card-section da ki görselleri değiştirebilelim. User friendly olsun
kategoriasyonunu güzel yap."*

Ana sayfa D-13x'te mock JSON'dan `SIMGE_OS_APP`'e taşınmıştı ve o zaman
*"ileride admin panel aynı tabloları yazacak"* diye not düşülmüştü. Bu ADR o
notun karşılığı.

### 1. Bölümler eklenip silinmiyor — bilerek

Her `section_key` vitrin şablonundaki bir **yuvanın adı**. Şablonda karşılığı
olmayan bir anahtar eklemek hiçbir şey çizmez; var olanı silmek o yuvayı sessizce
boşaltır ve geri getirmek için veritabanına elle satır yazmak gerekir. Panel
yuvaların **içeriğini** yönetiyor: başlık, sıra, açık/kapalı ve öğeler.

Bu bir sınırlama değil, yanlış anlaşılmayı önleyen bir sınır: "bölüm ekle"
düğmesi olsaydı operatör ekler, vitrinde hiçbir şey görmez ve nedenini bulamazdı.

### 2. Anahtarlar insanca adlandırıldı

Veritabanındaki adlar şablonun iç adları: `offer_banner_1`, `products_list_3`.
Panelde çıplak göstermek, operatörden şablon kaynağını bilmesini istemek olurdu —
özellikle `offer_banner_1` / `offer_banner_2` ikilisinde, çünkü ikisi de "banner"
ve sayfanın çok farklı yerlerinde duruyorlar. `BolumEtiketleri` her anahtara bir
**ad** ve **nerede göründüğü** veriyor:

* `offer_banner_1` → "Ürün tanıtım banner'ları — kategori şeridinin altındaki
  banner şeridi (banner-section)"
* `offer_banner_2` → "Alt tanıtım banner'ları — ikinci ürün listesinin altındaki
  kutulu banner şeridi (gift-card-section)"

Kullanıcının adıyla andığı iki bölüm tam olarak bunlar. Listede olmayan bir
anahtar hata değil; o durumda anahtarın kendisi gösteriliyor.

### 3. Referanslar isimle gösteriliyor

Ana sayfa ürünleri `sto_RECno`, kategorileri `san_RECno` ile referanslıyor.
Panelde "83308" göstermek, operatörden 8.238 ürünlük katalogda o numarayı
hatırlamasını istemek olurdu. İsimler Mikro'dan **tek sorguda** çözülüyor; dört
ürün listesi × sekizer ürün = 32 gidiş-dönüş yerine bir.

Referans ERP'den kalkmışsa `ref_ad` null geliyor ve panel bunu *"Mikro'da
bulunamadı — #83308"* diye **görünür** kılıyor. Sessiz bir boşluk, operatörün
göremeyeceği bir bozukluk demekti.

Yeni referans eklenirken hedefin Mikro'da var olduğu **doğrulanıyor**: olmayan
bir numara kaydedilirse vitrinde sessizce eksik bir karo çıkardı.

### 4. Öğe görselleri

Banner ve hizmet ikonları ürün görselleriyle **aynı depoya** yazılıyor
(`SIMGE_IMAGE_BLOB`): aynı küçültme, aynı içerik adresli tekilleştirme.
Öğenin `image_tr` / `image_en` sütununa yazılan şey **adres**, ve çizimde okunan
tek yer orası — tek doğru kaynak.

Yanına ayrıca `owner_type = 'HOME'` bağı kuruluyor, anahtarı `"<öğe id>:<dil>"`.
Bu bağ çizim için kullanılmıyor; **baytların sahipsiz görünmemesi** için var.
V17'nin sonundaki bakım sorgusu hiçbir bağı olmayan baytları "öksüz" sayıyor ve
bağ kurulmasaydı yayında olan bir banner'ın baytları o listede çıkardı.

Dil ayrı tutuluyor çünkü banner'ların üzerinde yazı var; TR ve EN aynı dosya
olamıyor. EN boş bırakılırsa vitrin TR görselini kullanıyor.

### 5. Yetki

Uçlar `ADMIN` **ve** `ICERIK`'e açık: ana sayfada hangi banner'ın ya da hangi
ürünün görüneceğine karar vermek içerik işi, cari açma yetkisi gerektirmiyor.
Depo ayarı buna **dahil değil** (D-152).

### Doğrulama

Dört servis ayakta, gerçek veriyle:

| Kontrol | Sonuç |
|---|---|
| `GET /api/storefront/home` | 11 bölüm, etiketleriyle |
| Ürün referansları | `5481 → TOZ SEKER 50KG`, `8313 → KOMILI 5LT SIZMA Z.YAGI` |
| Kategori referansları | `1 → SIGARALAR [01]`, `2 → SUT-KAHVALTILIK URUNLER [02]` |
| `offer_banner_2` öğeleri | 3 banner, TR/EN görselleri ayrı |
| Yeni banner öğesi + görsel yükleme | `gorsel_tr = /api/images/<hash>/detail.jpg` |
| Görsel vitrin backend'inden | **HTTP 200**, `image/jpeg`, 9.691 bayt |
| Test öğesi silindikten sonra | bölüm öğe sayısı başlangıca döndü (3) |

**Denenmemiş:** panel arayüzü tarayıcıda tıklanarak gezilmedi; doğrulama HTTP
uçları üzerinden yapıldı. Angular derlemesi (`npm run build`) temiz, ama
sürükle-sıra, dosya seçme ve önizleme davranışı ekranda görülmedi.

---

## D-155 — JSON alan adları snake_case; kablo sözleşmesi testle sabitlendi

**Tarih:** 2026-08-20
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı panelde depo ekranını açtı ve şu hatayı aldı:

```
TypeError: Cannot read properties of undefined (reading 'toLocaleString')
    at _SettingsComponent.sayi (settings.component.ts:99:14)
```

**Sebep:** `application.properties` içinde
`spring.jackson.property-naming-strategy=SNAKE_CASE` var. Java'daki
`fiyatliUrun` kabloya **`fiyatli_urun`** olarak çıkıyor; panel camelCase
okuyordu, alan `undefined` geliyordu.

### Asıl tehlikeli olan: yazma tarafı

Aynı ayar **deserialization**'a da uygulanıyor. `OgeIstegi` ve `BolumGuncelle`
alanları (`baslikTr`, `refId`, `bagTuru`…) istek gövdesinde `baslik_tr`,
`ref_id`, `bag_turu` olarak bekleniyordu; panel camelCase gönderiyordu.

Jackson bilinmeyen alanda **istisna fırlatmıyor** (`FAIL_ON_UNKNOWN_PROPERTIES`
Spring Boot'ta kapalı): alan `null` kalıyor ve servisin "null bırakılan alan
değişmez" kuralıyla birleşince **kayıt sessizce hiçbir şey yapmıyordu**.
Operatöre hata da gösterilmiyordu. Bu, D-150'de bedeli ödenen hata sınıfının
aynısı: ekran "oldu" diyor, hiçbir şey olmuyor.

### Neden uçtan uca doğrulamada kaçtı

İki sebep, ikisi de kayda değer:

1. **Gönderilen gövdelerin hepsi tek kelimelikti** — `aktif`, `depo`, `ogeler`.
   Tek kelimede SNAKE_CASE hiçbir şeyi değiştirmiyor. Çok kelimeli hiçbir alan
   denenmemişti.
2. **Hata ekrana yazdı ve görmezden gelindi.** İlk depo listesi çıktısında
   fiyatlı/stoklu sütunları boştu; bu bir PowerShell biçimlendirme hatasına
   yoruldu. Boş sütun aslında `undefined` alanın kendisiydi.

### Düzeltme

* Panel arayüzü okuma ve yazmada snake_case kullanıyor.
* **Okuma/yazma asimetrisi giderildi:** yanıt `altbaslik_tr` diyordu, istek
  `alt_baslik_tr` bekliyordu. İkisi de `alt_baslik_tr` oldu. Bu asimetri tam
  olarak bu hata sınıfını üreten şeydi ve bırakılsaydı bir sonrakini de
  üretecekti.

### springdoc yanlış söylüyor

Çalışan uygulamanın `/v3/api-docs` çıktısı `baslikTr` diyor — springdoc
adlandırma stratejisini uygulamıyor. Yani **API dokümantasyonu bu konuda
yanlış**; kabloya çıkan ad Jackson'ın ürettiğidir. Şemaya bakıp camelCase
gönderen bir sonraki geliştirici aynı sessiz kayba düşerdi.

Bu yüzden iki ayrı test var:

| Test | Cevapladığı soru |
|---|---|
| `JsonAlanAdlariTest` | SNAKE_CASE bu record'larda hangi adları üretir? |
| `JacksonYapilandirmaTest` (`@JsonTest`) | **Bu uygulamada** SNAKE_CASE gerçekten açık mı? |

İkincisi Spring Boot'un `application.properties`'ten kurduğu mapper'ı enjekte
ediyor, elle kurulanı değil — springdoc'a güvenilemeyeceği anlaşıldıktan sonra
eklendi. camelCase gövdenin sessizce yok sayıldığı da testle **belgelendi**;
amaç davranışı savunmak değil, mekanizmayı görünür tutmak.

### Doğrulama

| Kontrol | Sonuç |
|---|---|
| Uygulamanın mapper'ı yanıtta | `fiyatli_urun`, `stoklu_urun` |
| Uygulamanın mapper'ı istekte | `baslik_tr`, `ref_id`, `bag_turu` okunuyor |
| camelCase gövde | istisna yok, alan **null** (sessiz kayıp belgelendi) |
| Panel paketi | **104 test, 0 hata** (5 atlandı) |
| Panel arayüzü derlemesi | temiz |

**Denenmemiş:** düzeltme HTTP uçlarından tekrar geçirilmedi. Doğrulama için
açtığım geçici personel hesabı kullanıcının isteğiyle silindi ve geriye parolasını
bilmediğim `admin` kaldı. Kablo sözleşmesi artık uygulamanın gerçek
yapılandırmasıyla test altında; ekranda son onayı kullanıcı verecek.

---

## D-156 — Depo seçimini kod engellemez; uyarı bilgi verir, kararı yönetici verir

**Tarih:** 2026-08-20
**Durum:** Kabul edildi
**Bağlam:** Kullanıcı: *"Hangi deponun seçilemeyeceğini neden kafana göre karar
verdin uyarı yazıları kalsın admin isterse yine seçebilir"*.

D-152'de depo seçimine bir eşik konmuştu: 100'den az fiyatlı ya da stok
hareketli ürünü olan depo hem panelde seçilemiyor hem de sunucu tarafından
reddediliyordu. **Eşiği kod uyduruyordu.** Gerekçe ("vitrini boşaltır") doğru
ama sonuç yanlıştı: yeni açılmış, taşınmakta olan ya da geçici olarak
boşaltılmış bir depoya bilerek geçmek isteyen yönetici, kodun seçtiği bir sayıya
takılıyordu. Bu bir güvenlik sınırı değil, bir tahmindi.

### Yeni sınır

Reddedilen tek şey **var olmayan depo** — Mikro'nun `DEPOLAR` tablosunda
karşılığı yoksa seçilecek bir şey yoktur. Depo 0 da bu kapsamda; tabloda hiç
geçmiyor ve ayrı bir mesajla söyleniyor çünkü 0 yazmak yaygın bir hata ve
"depo bulunamadı" demek sebebi gizlerdi.

Bunun dışında hiçbir şey engellenmiyor:

| Durum | Önce | Şimdi |
|---|---|---|
| Boş depo (SANAL DEPO, 0/0) | reddediliyordu | **seçilebilir**, uyarı gösterilir |
| Stok hareketi az depo (15: 1.214/24) | reddediliyordu | **seçilebilir**, uyarı gösterilir |
| Mikro'da iptal işaretli depo | reddediliyordu | **seçilebilir** |
| Mikro'da olmayan depo / 0 | reddediliyor | reddediliyor |

`DepoSatiri`'ndeki `uygun` alanı **kaldırıldı**. "Seçilebilir mi" sorusunun
cevabını sunucu vermiyor artık; yalnızca `uyari` metni dönüyor.

### Uyarı nereye gidiyor

Kaybolmuyor, üç yerde duruyor:

* depo kartında **Dikkat** rozeti ve açıklama satırı,
* onay kutusunda `DİKKAT: …` satırı olarak — yönetici kararı bilerek versin,
* sunucu günlüğünde `WARN` olarak. Engellemiyoruz ama iz bırakıyoruz: "vitrin
  neden boşaldı" diye sorulduğunda cevabın günlükte olması gerekiyor. İptal
  işaretli depo seçimi de ayrıca loglanıyor.

Panelde kart artık `disabled` değil; uyarılı kart yalnızca **renk** olarak
ayrışıyor (kenarlık ve zemin), geometri değişmiyor.

### Ders

Ölçüm iyiydi, ondan çıkarılan kural fazlaydı. Sayıyı göstermek operatörü
bilgilendirir; sayıya bakıp onun yerine karar vermek, alan bilgisini kodun
tahminine feda etmektir. Bu ayrım bundan sonraki eşikler için de geçerli.

### Doğrulama

`WarehouseServiceTest` yeniden yazıldı; testler artık **seçilebildiğini**
doğruluyor (uyarı dönüyor ama ayara gerçekten yazılıyor). Panel paketi:
**104 test, 0 hata**.

---

## D-172 — Hakkımızda metni veritabanına taşındı ve İngilizcesi eklendi

**Tarih:** 2026-08-21
**Durum:** Kabul edildi
**Kapsam:** `SIMGE_OS_BE`, `SIMGE_OS_FE`, `SIMGE_OS_ADMIN_BE`, `SIMGE_OS_FE_ADMIN`

### Bağlam

Hakkımızda sayfasının metni vitrin paketindeki
`public/assets/data/themeOptions.json` dosyasının `about_us` anahtarındaydı.
İki ayrı sorun üretiyordu ve ikisi de aynı sebepten:

1. **İngilizcesi yoktu.** Alanlar `title` / `description` biçimindeydi, yani
   ortada bir *çeviri* değil tek bir metin vardı. Dil İngilizce'ye
   çevrildiğinde sayfanın geri kalanı çevriliyor ama gövde metni Türkçe
   kalıyordu. `ngx-translate` sözlüklerine de konamazdı: 1.482 karakterlik
   bir tanıtım metni i18n anahtarı değil, içerik.
2. **Değiştirmek için yeniden yayın gerekiyordu.** Bir telefon numarasını
   güncellemek "dosyayı düzenle, derle, dağıt" demekti; bunu yapabilecek kişi
   operatör değil geliştiriciydi.

Ana sayfa aynı yolu D-154'te geçmişti (`SIMGE_HOME_SECTION`, TR/EN sütunlar,
panelden düzenleme). Bu, o kararın düz sayfalar için karşılığı.

### Karar

İçerik `SIMGE_OS_APP.SIMGE_PAGE_BLOCK` tablosuna taşındı: **panel yazar,
vitrin okur**.

```
page_key   block_key    block_type   image_url  title_tr/en  body_tr/en
about      intro        RICH_TEXT    banner     başlık       5 paragraf
about      feature_1..4 FEATURE      ikon       kutu başlığı tek satır
```

Türkçe metin `themeOptions.json`dan **olduğu gibi** taşındı (1.482 karakter,
birebir aynı) — bu bir yeniden yazım değil, yer değiştirme. İngilizcesi yeni:
daha önce hiç yoktu.

### Neden yeni bir tablo, neden `SIMGE_HOME_SECTION` değil

- O tablonun anahtarı `theme_slug`: bölümler bir vitrin **temasına** ait.
  Hakkımızda temaya ait değil, siteye ait.
- Oradaki metin sütunları `NVARCHAR(255)` / `NVARCHAR(500)`. Tanıtım metni
  bugün bile 1.482 karakter; oraya sığdırmak metni kesmek olurdu. `body_*`
  bu yüzden `NVARCHAR(MAX)`.

`page_key` sütunu, bir sonraki düz sayfa için yeni bir tablo açılması
gerekmesin diye var — ama **bu sürümde yalnızca `about` tohumlandı ve
sunucu beyaz listeyle yalnızca onu kabul ediyor**. Boş bir genellik
üretmemek için diğer sayfalar eklenmedi.

### Görsel neden dile göre ayrılmadı

Ana sayfa banner'larında `image_tr` / `image_en` var, çünkü o görsellerin
**üzerinde yazı** duruyor. Hakkımızda'daki tanıtım fotoğrafı ve hizmet
ikonlarında yazı yok — aynı fotoğraf iki dilde de doğru. Tek sütun
(`image_url`) tutuldu; ikiye bölmek operatöre iki kez aynı dosyayı
yükletmekten başka bir şey yapmazdı.

### Eksik çeviri: boşluk değil, Türkçe

Vitrin ucu bir blokta İngilizce alan boşsa **Türkçesine düşüyor**. Sonuç:
yeni bir blok eklenip çevirisi henüz girilmemişse İngilizce sayfada boş bir
kutu değil, Türkçe metin görünüyor. Eksik çeviri kayıp içerikten iyidir.

Bunun bedeli, eksik çevirinin sessiz kalması. Panel bu yüzden yan yana iki
dil gösteriyor ve eksik olanları **"İngilizcesi eksik"** rozetiyle
işaretliyor.

Ters yön serbest değil: **açık bir bloğun Türkçe içeriği tamamen
boşaltılamaz** (sunucu reddediyor). Türkçe hem varsayılan dil hem de
İngilizcenin yedeği; boşaltılırsa iki dilde birden boş bir kutu kalır.
Bloğu göstermemenin yolu onu kapatmak.

### Uçlar

| | |
|---|---|
| `GET /api/pages/{key}?lang=` | Vitrin (8080). Tek dile çözülmüş, paragraflara bölünmüş. Girişe kapalı — sayfa zaten `authGuard`ın arkasında (D-107). |
| `GET /api/pages?sayfa=` | Panel (8081). İki dil bir arada, pasif bloklar dahil. `ADMIN` + `ICERIK`. |
| `PUT /api/pages/blocks/{id}` | Metin/başlık/açık-kapalı. Null alan değişmez, boş dize temizler. |
| `POST/DELETE /api/pages/blocks/{id}/image` | Görsel; baytlar ürün görselleriyle aynı depoda (`SIMGE_IMAGE_BLOB`). |

Yetki sınırı vitrin ana sayfasıyla aynı: bir tanıtım metnini yazmak içerik
işi, sistem işi değil — `ICERIK` rolü de erişebiliyor. Depo ayarının
(`/api/settings`, yalnızca `ADMIN`) aksine bu değişikliğin fiyat ya da stok
üzerinde etkisi yok.

### Paragraflara bölme sunucuda

Metin, boş satırlarda (ve tek satır sonlarında) paragraflara **sunucu
tarafında** bölünüyor; vitrin hazır bir dizi alıyor. Bölme daha önce Angular
bileşenindeydi. Tek bir yerde durması gerekiyordu çünkü aynı kuralı panel de
önizliyor ("vitrinde N paragraf olarak çıkacak") — iki ayrı bölme kuralı er
geç ayrışır.

### Silinenler

- `themeOptions.json` → `about_us` anahtarı (30 satır). Ölü veri bırakmak bir
  sonraki turda "hangisi doğru" sorusunu doğuruyor.
- `theme-option.interface.ts` → `AboutUs`, `AboutSection` ve yalnızca onlar
  üzerinden erişilen `Team`, `Member`, `Testimonial`, `Review` (D-145'te
  bölümler kaldırılmış ama tipler kalmıştı). `AboutFutures` **duruyor**: ana
  sayfanın hizmet şeridi onu kullanıyor.

### Panelde

Yeni menü girdisi: **Hakkımızda** (`/hakkimizda`). Her blok bir kart:
görsel + TR/EN başlık + TR/EN metin, yan yana.

Yan yana olmasının sebebi yer değil görünürlük: sekmeye ya da alt alta
koymaya kıyasla daha çok yer kaplıyor, ama eksik çeviriyi görünür kılan tek
düzen bu — ekranın var olma sebebi de zaten tek dilli bir sayfayı iki dile
çıkarmaktı.

Kaydet düğmesi yalnızca gerçekten değişiklik varsa açılıyor. Hiçbir şey
değiştirmeden kaydetmek `updated_at`i yalancı biçimde ilerletirdi; o alan
"bu metne en son ne zaman dokunuldu" sorusunun tek cevabı.

Açık/kapalı anahtarı ise **anında** kaydediyor, "Kaydet"i beklemiyor: bu bir
metin düzenlemesi değil, bir yayın kararı — metni yarım bırakmışken bloğu
gizleyebilmek gerekiyor.

### Açık kalan

Sayfa girişe kapalı. Hakkımızda çoğu sitede herkese açık bir tanıtım sayfası,
ama bu vitrinin **tamamı** B2B ve giriş arkasında (D-107); ayrıcalık tanımak
bu kararı tek bir sayfa için delmek olurdu. Sayfa ileride herkese açılacaksa
uç da `permitAll` listesine alınmalı — ikisi birlikte değişir.

---

## D-173 — Cari güncelleme kuyruğu: vitrin talep eder, panel ERP'ye yazar

**Tarih:** 2026-08-21
**Durum:** Kabul edildi
**Kapsam:** `SIMGE_OS_BE`, `SIMGE_OS_FE`, `SIMGE_OS_ADMIN_BE`, `SIMGE_OS_FE_ADMIN`

### Bağlam

Müşteri sipariş verirken teslimat adresi seçmek zorunda ve `sip_adresno`
yalnızca **o carinin adres listesinde önceden var olan** bir satırı
gösterebiliyor. Yani yeni bir şubeye sevk yapılabilmesi için adresin önce
`CARI_HESAP_ADRESLERI`'ne girmiş olması şart. Bugün böyle bir yol yok:
adres yalnızca cari açılışında, bir kez yazılıyor (D-127).

Ölçüm:

| | |
|---|---|
| Aktif cari | 2.441 |
| En az bir adresi olan | 1.949 (**492'sinin hiç adresi yok**) |
| Birden fazla adresi olan | 18 |
| Bir caride görülen en yüksek adres sayısı | 16 |
| `sip_adresno` değerleri (343.013 sipariş) | **hepsi 1** |

Yani çok adresli sevk şemada var, pratikte hiç kullanılmamış.

### Reddedilen seçenek: vitrine kısıtlı yazma yetkisi

"Vitrin yalnızca `CARI_HESAP_ADRESLERI`'ne yazsın, yetkisi o tabloyla
sınırlansın" seçeneği **ölçülerek** elendi.

Mikro'nun kimlik değişmezi: `adr_RECno` IDENTITY ve `adr_RECid_RECno` ona
eşit olmak zorunda. Sayıldı:

```
adr_RECid_RECno = adr_RECno  →  1.982 satır
adr_RECid_RECno = 0          →      0 satır
```

İstisnasız. IDENTITY değeri INSERT anında bilinemediği için tek yol: 0 yaz,
sonra aynı işlemde `SCOPE_IDENTITY()` ile **UPDATE** et. Dolayısıyla
**INSERT-only yetki yetmiyor**, `UPDATE` de gerekiyor.

SQL Server'da yetki tablo düzeyinde. `GRANT UPDATE ON CARI_HESAP_ADRESLERI`
şu demek: internete açık servis, ERP'deki **1.982 adresin tamamını** —
bütün gerçek müşterilerin fatura ve sevk adreslerini — yeniden yazabilir.
"Yalnızca kendi carisinin satırları" kısıtı ancak Row-Level Security ya da
saklı yordamla kurulabilir; ikisi de **Mikro'nun şemasına nesne eklemek**
demek, yani D-100'ün yasakladığı şey.

En dar verilebilecek yetki bile özelliğin kendisinden kat kat geniş.
Vitrinin Mikro bağlantısı ayrıca bugün `sa` (sysadmin) ve yazmayı engelleyen
tek şey `ReadOnlyRepository` sözleşmesi — o da derleme zamanı, sağlam değil:
`ProductRepositoryImpl` içinde `@PersistenceContext(unitName = "mikro")` ile
canlı bir `EntityManager` zaten duruyor.

### Karar

Tek bir kuyruk tablosu: `SIMGE_OS_APP.SIMGE_CARI_UPDATE_REQUEST`.

```
Vitrin --(talep)--> SIMGE_OS_APP --(panelde onay)--> CariWriter --> Mikro
       ERP'ye hiç             kendi DB'miz          tek yazma noktası
       dokunmaz
```

Üç talep türü, **tek tablo, tek ekran, tek onay akışı**:

| Tür | Onaylanınca ERP'de |
|---|---|
| `ADRES_EKLE` | `CARI_HESAP_ADRESLERI`'ne **INSERT** |
| `FATURA_ADRESI` | `CARI_HESAPLAR.cari_fatura_adres_no` **UPDATE** |
| `CARI_BILGI` | `CARI_HESAPLAR` unvan / telefon / e-posta **UPDATE** |

Ayrı iki mekanizma (adres kuyruğu + cari güncelleme kuyruğu) yapmak, aynı
onay akışını iki kez yazmak olurdu.

### Adres satırı düzenlenmiyor — ekle ve işaretçiyi çevir

"Firmam taşındı" durumunda mevcut adres satırının metnini değiştirmiyoruz.
Sebep geçmiş: `SIPARISLER.sip_adresno` ve `STOK_HAREKETLERI.sth_adres_no` o
satırı **numarayla** gösteriyor. İçeriği değiştirilirse üç yıl önceki
irsaliye, malın hiç gitmediği bir adresi gösterir.

Doğru işlem: **yeni adres ekle (INSERT) + `cari_fatura_adres_no`'yu ona
çevir (UPDATE)**. Eski satır olduğu gibi kalır.

Bunun yan etkisi değerli: `CARI_HESAP_ADRESLERI` **yalnızca INSERT** kalıyor.

```
CARI_HESAP_ADRESLERI  →  yalnızca INSERT
CARI_HESAPLAR         →  yalnızca işaretçi ve bilgi alanları UPDATE
hiçbir yerde          →  DELETE yok
```

### E-posta kodu: talep ancak doğrulanınca oluşur

Talep göndermeden önce kullanıcının **hesabına kayıtlı adrese** kod gidiyor
ve kod girilmeden satır oluşmuyor (`otp_verified_at` NOT NULL). Mevcut
altyapı kullanılıyor: `EmailOtp` + `SecretCodes` + `MailService`, yeni
`purpose = CARI_UPDATE`.

Kullanıcı zaten giriş yapmış durumda, dolayısıyla kod "kimsin" sorusunu
değil **"hesabın e-postasına hâlâ erişiyor musun"** sorusunu cevaplıyor —
ERP kaydını değiştiren bir işlem için ek adım. Çöp veriyi engellemez; onu
panel onayı engelliyor. İki katman iki ayrı iş yapıyor.

### Alan genişlikleri ERP'nin, doğrulama girişte

Mikro sütunları beklenenden dar; ölçüldü:

| Mikro sütunu | Karakter |
|---|---|
| `adr_Adres_kodu` (başlık) | **10** |
| `adr_cadde` / `adr_mahalle` / `adr_sokak` | 50 |
| `adr_ilce` / `adr_il` / `adr_ulke` | 15 |
| `adr_posta_kodu` | 8 |
| `adr_tel_no1` | **10** |
| `cari_unvan1` | 50 |
| `cari_EMail` | 80 |

Kuyruk tablosu **bu genişliklerde** tanımlandı ve vitrin formu aynı sınırları
uyguluyor. Sebep: sınırı girişte uygulamazsak, panele **ERP'ye yazılamayan**
bir talep düşer ve hata operatörün karşısına, düzeltemeyeceği bir anda çıkar.

### Telefon üç alana bölünüyor — ve mevcut bir hata düzeltildi

Mikro telefonu bölerek tutuyor; ölçüldü, dolu satırların hepsi böyle:
`adr_tel_ulke_kodu`='90', `adr_tel_bolge_kodu`='312', `adr_tel_no1`='3976498'
(7 hane).

`insert-cari-adres.sql` ise telefonun **tamamını** `adr_tel_no1`'e yazıyor ve
`RegistrationReviewController` o alanı `@Size(max = 50)` ile kabul ediyor.
`adr_tel_no1` 10 karakter: normal bir cep numarası (`05321234567`, 11 hane)
INSERT'i **truncation hatasıyla düşürür**. Cari açılışında bugüne kadar
patlamamış olması, girilen numaraların kısa olmasından. D-173 kapsamında
telefon bölme ortak bir yardımcıya alındı ve iki yol da onu kullanıyor.

### Adres başlığı: gerçekçi olalım

`adr_Adres_kodu` 10 karakter ve fiilen kullanılmıyor — 1.982 satırın
**1.975'i boş**, dolu olan 7 tanesi de "1" ve "06540" gibi kodlar, insan
etiketi değil.

Dolayısıyla "Çankaya Şube" (12 karakter) oraya sığmıyor. İki karar:

1. Kullanıcı isterse ≤10 karakterlik kısa bir kod girebiliyor.
2. **Görünen etiket her zaman ilçe/il'den üretiliyor** — başlık boşsa
   "Çankaya / ANKARA" yazıyor. Aksi halde 30 şubeli bir müşteri ödeme
   ekranında 30 boş satır görürdü.

### Ödeme ekranı: otomatik seçim kapatıldı

`address-block` bugün `addresses[0]`ı sessizce seçiyor ("çoğu caride tek
adres var" gerekçesiyle). Tek adreste doğru, çok adreste **siparişin yanlış
şubeye gitmesi** demek. Yeni davranış:

- 1 adres → otomatik seçili (davranış korunuyor),
- >1 adres → **açık seçim zorunlu**, varsayılan olarak son siparişin
  `sip_adresno`'su işaretli gelir.

Fatura adresi ayrıca **gösteriliyor ama seçilmiyor**: siparişte fatura adresi
alanı yok, fatura carinin `cari_fatura_adres_no`'suna kesiliyor. Seçtirmek,
gerçek faturayı değiştirmeyen bir kontrol göstermek olurdu.

Uyarı: 2.441 carinin **690'ında** `cari_fatura_adres_no` var olmayan bir
satırı gösteriyor. Gösterim, işaretçi çözülmezse en küçük numaralı adrese
düşüyor.
