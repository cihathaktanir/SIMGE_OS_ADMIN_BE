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

**ERP'ye yazma henüz açılmadı.** Repository'ler burada da
[`ReadOnlyRepository`](src/main/java/com/simge/adminbackend/erp/ReadOnlyRepository.java)
üzerinden türüyor. Açıldığında o taban sınıfa `save` eklenmeyecek; yazma gereken tablo için
ayrı ve dar bir arayüz yazılacak.

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
