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

*Yeni ADR eklerken sıra numarası üç repoda ortak ilerler. ID'ler değişmez; silinen bir karar boşluk bırakır.*
