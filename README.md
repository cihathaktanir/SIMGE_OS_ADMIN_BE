# SIMGE_OS_ADMIN_BE — Simge Yönetim API'si

Simge Online Selling'in **yönetim** backend'i. Spring Boot 3.4.5 / Java 17 / MSSQL.

> **Bu servis intranette çalışır. İnternete açılmaz.** (ADR D-122)

## Neden vitrin backend'inden ayrı

`SIMGE_OS_BE` (vitrin, port 8080) internete açık ve Mikro ERP'ye **yazamaz** — yazma yolu
kodda yok, olmadığı için derleme hatası (D-104). Bu servis ise ileride ERP'ye sınırlı yazma
yapacak: cari e-postası ekleme, yeni cari giriş talebi gibi. O yeteneği internete açık
sürecin içine koymak, oradaki her açığı ERP'ye taşıyan bir köprü kurardı.

Ayrımın üç somut karşılığı:

| | Vitrin (`SIMGE_OS_BE`) | Yönetim (bu servis) |
|---|---|---|
| Ağ | İnternet | İntranet |
| Mikro erişimi | Salt okuma (SELECT-only SQL kullanıcısı verilebilir) | Okuma + *ileride* sınırlı yazma |
| Kullanıcılar | `SIMGE_USER` — müşteriler, e-postayla girer | `SIMGE_STAFF_USER` — Simge personeli, kullanıcı adıyla girer |
| Oturum | `SPRING_SESSION` / `SIMGE_SESSION` çerezi | `SIMGE_STAFF_SESSION` / `SIMGE_ADMIN_SESSION` çerezi |

## ERP'ye yazma (ADR D-127)

Yazma **açıldı** ama dar: tek bir sınıf, iki işlem.

| | |
|---|---|
| Yazan tek sınıf | [`CariWriter`](src/main/java/com/simge/adminbackend/erp/CariWriter.java) |
| Yapabildiği | yeni cari açmak · var olan carinin **boş** e-posta alanını doldurmak |
| Yapamadığı | silmek · başka alan güncellemek · toplu işlem · dolu e-postanın üzerine yazmak |
| Tetikleyen | yalnızca personel onayı; otomatik yazma yok |

[`ReadOnlyRepository`](src/main/java/com/simge/adminbackend/erp/ReadOnlyRepository.java)
**değişmedi**: ERP tarafındaki her JPA repository'si hâlâ ondan türüyor, yani
`cariHesaplarRepository.save(...)` bugün de derleme hatası. Yazma yolu bilerek JPA'nın
dışında tutuldu ki "hangi kod ERP'ye yazabiliyor" sorusunun cevabı tek dosya kalsın.

INSERT elle yazılmadı, **ölçülerek üretildi**: `CARI_HESAPLAR`'ın 183 sütununun yalnızca ikisi
`NOT NULL` ve hiçbirinin varsayılanı yok — ama mevcut 2440 satırın 2439'unda tek bir NULL da
yok. Yani şema serbest, Mikro değil. Üretilen betikler
[`src/main/resources/erp/`](src/main/resources/erp) altında; şema değişirse yeniden
üretilmeli (yöntem D-127'de).

**Doğrulama:** `CariWriterCanliTest` gerçek şemada yazıp **geri alarak** hiçbir sütunun NULL
kalmadığını denetler. Canlı veritabanı istediği için varsayılan olarak kapalı:

```powershell
.\mvnw.cmd test "-Dsimge.erp.canli-test=true" "-Dtest=CariWriterCanliTest"
```

## Veritabanı

İki datasource:

- **`MikroDB_V15_2021`** — Mikro ERP. Şema ERP'ye ait, `ddl-auto=none`.
- **`SIMGE_OS_APP`** — kendi veritabanımız, **vitrinle paylaşılıyor**. Tablo sahipliği ayrı:

| Sahip | Tablolar | Şema |
|---|---|---|
| `SIMGE_OS_BE` | `SIMGE_USER`, `SIMGE_REGISTRATION_REQUEST`, `SIMGE_COMPANY_INVITATION`, `SIMGE_HOME_SECTION*`, `SPRING_SESSION*` | o reponun `db/app` klasörü |
| bu servis | `SIMGE_STAFF_USER`, `SIMGE_STAFF_ROLE`, `SIMGE_STAFF_SESSION*` | [`db/admin`](src/main/resources/db/admin) |

Migration'lar elle çalıştırılıyor (Flyway yok):

```
sqlcmd -S localhost -U sa -P <parola> -f 65001 -i src/main/resources/db/admin/V1__staff_users_and_sessions.sql
```

`-f 65001` şart — Türkçe karakterler bozulmasın diye.

## Komutlar

```
./mvnw test               # testler — veritabanı gerekmez
./mvnw package            # jar
```

**Çalıştırmak için önce parolalar.** `application.properties` içinde kimlik bilgisi yok;
parolalar ortam değişkeninden okunuyor ve **varsayılanı yok** — tanımlı değilse uygulama
açılışta `Could not resolve placeholder` ile durur. Boş bir varsayılan, bağlantı hatasını
veritabanı katmanına erteler ve "neden bağlanmıyor" sorusunu zorlaştırırdı.

PowerShell:

```powershell
$env:SIMGE_MIKRO_PASSWORD  = '...'
$env:SIMGE_APP_DB_PASSWORD = '...'
.\mvnw.cmd spring-boot:run          # port 8081
```

Her seferinde yazmamak için `run-local.cmd.example` dosyasını `run-local.cmd` olarak
kopyalayıp doldurun; `.gitignore` onu dışarıda tutuyor.

İki ayrı değişken olmasının sebebi: canlıda Mikro'ya **SELECT-only**, `SIMGE_OS_APP`'e yazma
yetkili iki **farklı** MSSQL kullanıcısı verilecek. Tek değişken olsaydı bu ayrım
yapılamazdı.

Swagger arayüzü: <http://localhost:8081/> (kök yol oraya yönlendiriliyor).

## İlk giriş

Personel tablosu **tamamen boşsa** açılışta tek bir `ADMIN` hesabı açılır ve geçici parolası
**bir kereye mahsus log'a** yazılır:

```
==================================================================
 İLK YÖNETİCİ HESABI AÇILDI (personel tablosu boştu)
   kullanıcı adı : admin
   geçici parola : ....-....-....
==================================================================
```

Parola yapılandırmada tutulmaz. İlk girişte değiştirilmesi **zorunlu**; değiştirilene kadar
`/api/auth/**` dışındaki her uç `403 password_change_required` döner.

Kullanıcı adını değiştirmek için: `SIMGE_ADMIN_USERNAME=...`

## Roller

`ADMIN`, `SATIS`, `DEPO`, `MUHASEBE`, `ICERIK` — bir kişide birden fazla olabilir. Rolü
olmayan hesap giriş yapamaz. Son aktif yöneticinin rolü alınamaz ve hesabı kapatılamaz.

## Parola unutulursa

> **Önce bunu yapın: her zaman EN AZ İKİ yönetici hesabı bulundurun.** Biri parolasını
> unutursa diğeri panelden (Personel → Şifre sıfırla) halleder ve aşağıdaki son çareye hiç
> ihtiyaç olmaz.

Sıfırlayacak başka yönetici yoksa panel kilitlenir: personelde e-posta zorunlu olmadığı için
"şifremi unuttum" bağlantısı yok. Çıkış yolu (ADR D-125):

```powershell
$env:SIMGE_ADMIN_RESET = 'admin'     # sıfırlanacak kullanıcı adı
.\mvnw.cmd spring-boot:run
```

Açılışta o hesabın parolası sıfırlanır ve yeni geçici parola **bir kereye mahsus log'a**
yazılır:

```
==================================================================
 PAROLA SIFIRLANDI (SIMGE_ADMIN_RESET)
   kullanıcı adı : admin
   geçici parola : ....-....-....
 >>> SIMGE_ADMIN_RESET DEĞİŞKENİNİ ŞİMDİ KALDIRIN. <<<
==================================================================
```

**Değişkeni hemen kaldırın.** Tanımlı kaldığı sürece her açılışta parola yeniden sıfırlanır ve
kullanıcı belirlediği parolayla giriş yapamaz.

Bu mekanizma bilerek **hesap açmaz, rol vermez, kapalı hesabı açmaz** — verseydi "herhangi bir
kullanıcıyı yönetici yap" anahtarına dönüşürdü. Yeni bir saldırı yüzeyi de açmıyor: bunu
tanımlayabilen kişi zaten `SIMGE_APP_DB_PASSWORD`'ü okuyup aynı satırı SQL ile
güncelleyebilir.

## Canlıya çıkmadan önce

- [ ] Bu servisi internete açmayın; ters vekil yalnızca iç ağdan erişilebilir olsun.
- [ ] Mikro bağlantısına **ayrı** bir MSSQL kullanıcısı verin (vitrininki SELECT-only kalsın).
- [ ] `SIMGE_COOKIE_SECURE=true` (HTTPS)
- [ ] `SIMGE_API_DOCS=false`
- [ ] `SIMGE_MAIL_USERNAME` / `SIMGE_MAIL_PASSWORD` tanımlayın — yoksa başvuru onayı
      `503 mail_unavailable` döner (davet gitmeden başvuru onaylanmış görünmesin diye).
- [ ] `SIMGE_SITE_URL` vitrinin gerçek adresi olsun; davet bağlantıları oraya gidiyor.

## İlgili kararlar

`docs/decisions.md` — özellikle **D-122** (neden ayrı servis), **D-123** (personel kimliği),
**D-124** (başvuru onayının taşınması). D-100/D-104 vitrin reposunda.
