# AGENTS.md — SIMGE_OS_ADMIN_BE

Bu dosya, bu repoda çalışan geliştirici ve yapay zekâ ajanları için bağlayıcıdır.
Genel iş bölümü için `../SIMGE_OS_BE/AGENTS.md` ile birlikte okunmalı; çelişki varsa
**bu dosya bu repo için geçerlidir**.

## 1. Proje

| | |
|---|---|
| Ad | Simge Yönetim API'si (`SIMGE_OS_ADMIN_BE`) |
| Sahip | cihathaktanir — Simge Online Selling |
| Yığın | Java 17, Spring Boot 3.4.5, Maven, MSSQL, Spring Security + Session JDBC |
| Ağ | **İntranet.** İnternete açılmaz (ADR D-122). |
| Port | 8081 (vitrin 8080'de) |
| REQ öneki | `REQ-ADM-NNN` |
| ADR numaralandırma | Üç repoda **ortak**; D-100…D-124 için `docs/decisions.md` |

## 2. Değiştirilemez kurallar

Bunlar tercih değil, sınır. İhlal eden değişiklik geri alınır.

1. **Mikro ERP'ye yazma yolu TEK noktadan geçer: `CariWriter`** (D-104 / **D-127**).
   Repository'ler hâlâ `com.simge.adminbackend.erp.ReadOnlyRepository` üzerinden türer;
   `save` / `delete` orada yoktur, yani `cariHesaplarRepository.save(...)` **derleme
   hatasıdır** ve öyle kalır. Yazma bilerek JPA'nın dışında, tek sınıfta.
   *`CariWriter`'ın yapabildiği iki şey var:* yeni cari açmak ve var olan bir carinin **boş**
   e-posta alanını doldurmak. Silme yok, başka alan güncelleme yok, toplu işlem yok.
   *Bunlar ADR yazmadan ve kullanıcıya sormadan yapılmaz:* `ReadOnlyRepository`'ye yazma
   metodu eklemek · `CariWriter`'a üçüncü bir yazma metodu eklemek · dolu e-posta korumasını
   (`WHERE LTRIM(RTRIM(cari_EMail)) = ''`) kaldırmak · vitrin backend'ine herhangi bir ERP
   yazma yolu açmak.
   *ERP'ye yazan her değişiklik `CariWriterCanliTest` ile denenir* — gerçek şemada yazıp geri
   alarak. `src/main/resources/erp/*.sql` elle düzenlenmez, ölçümden üretilir (D-127).

2. **`SIMGE_OS_APP` şemasında tablo sahipliği vardır.** Bu servis yalnızca
   `SIMGE_STAFF_*` tablolarının şemasını değiştirir (`src/main/resources/db/admin`).
   `SIMGE_USER`, `SIMGE_COMPANY_INVITATION`, `SIMGE_REGISTRATION_REQUEST`,
   `SIMGE_HOME_SECTION*`, `SPRING_SESSION*` → migration'ları `SIMGE_OS_BE` reposunda yazılır.
   Bu servis onları okur/satır yazar ama **DDL'ine dokunmaz**.

3. **Personel vitrine, müşteri panele giremez.** `SIMGE_STAFF_USER` ile `SIMGE_USER` ayrı
   tablolardır ve birleştirilmez (D-123).

4. **Geçici parola açık saklanmaz** ve ikinci kez gösterilmez. Yalnızca oluşturma/sıfırlama
   yanıtında, bir kez döner.

5. **`must_change_password` denetimi sunucudadır** (`StaffPasswordChangeGate`). Arayüzdeki
   yönlendirme onun yerine geçmez.

6. **`SecretCodes.hash` tek taraflı değiştirilmez.** Davet token'ının özetini bu servis
   yazıyor, vitrin okuyor; algoritma değişirse gönderilmiş bütün davet bağlantıları sessizce
   geçersiz olur (D-124).

7. **Yetkilendirme uçta uygulanır** (`@PreAuthorize`). Arayüzde menü gizlemek yetki değildir.

8. **Kurtarma mekanizması (`SIMGE_ADMIN_RESET`, D-125) genişletilmez.** Yalnızca var olan bir
   hesabın parolasını sıfırlar. Hesap açma, rol verme ya da kapalı hesabı etkinleştirme
   **eklenmez** — eklenirse "herhangi bir kullanıcıyı yönetici yap" anahtarına dönüşür.
   Sıfırlama log'ları da sessizleştirilmez.

## 3. Komutlar

```
./mvnw spring-boot:run       # API (8081)
./mvnw test                  # testler
./mvnw test -Dtest=StaffServiceTest
./mvnw package
```

Şema değişikliği: `src/main/resources/db/admin/VNN__ad.sql` yazılır ve elle çalıştırılır —

```
sqlcmd -S localhost -U sa -P <parola> -f 65001 -i src/main/resources/db/admin/VNN__ad.sql
```

`-f 65001` şart (Türkçe karakter). `SET QUOTED_IDENTIFIER ON;` DML için gerekli.
**Dikkat:** T-SQL blok yorumları iç içe geçer; yorum içine `/` + `*` ikilisi (örn. bir dosya
maskesi) yazmayın, batch bozulur.

## 4. Katman haritası

| Paket | Sorumluluk |
|---|---|
| `config/` | İki datasource, güvenlik, dil, OpenAPI |
| `erp/` | Mikro okuma katmanı — `ReadOnlyRepository`, entity'ler, `CariLookupService` |
| `appdb/` | `SIMGE_OS_APP` entity ve repository'leri |
| `staff/` | Personel kimliği, rolleri, parolası |
| `registration/` | Kayıt başvurusu onayı + davet üretimi |
| `mail/` | SMTP gönderimi ve şablon |
| `common/` | `SecretCodes` |

**`@Transactional` tuzağı:** İki transaction manager var (`mikroTransactionManager`,
`appTransactionManager`). Farklı manager isteyen bir metodu **aynı sınıf içinden çağırmayın** —
Spring proxy'si devreye girmez ve anotasyon sessizce etkisiz kalır. Mikro okuması gereken
`appTransactionManager` servisleri `CariLookupService`'e delege eder.

## 5. Vitrin ile sözleşme

Bu servis vitrin backend'ini çağırmaz; ikisi `SIMGE_OS_APP` üzerinden buluşur.

| Akış | Yazan | Okuyan |
|---|---|---|
| Kayıt başvurusu | vitrin | panel (onaylar/reddeder) |
| Davet (`SIMGE_COMPANY_INVITATION`) | panel (`invited_by_type='STAFF'`) ve vitrin (`'CUSTOMER'`) | vitrin (kabul akışı) |
| Hesap açma / parola | **yalnızca vitrin** | — |

## 6. Kimlik bilgisi

`application.properties` içinde **parola yoktur ve yazılmaz.** `SIMGE_MIKRO_PASSWORD` ve
`SIMGE_APP_DB_PASSWORD` ortam değişkenlerinden okunur, **varsayılanları yoktur** — tanımlı
değilse uygulama açılışta durur. Yerelde `run-local.cmd` (gitignore'da) kullanılır.

Bu repo, vitrin reposundaki D-103 sorunuyla (düz metin parola geçmişte) **doğmadı**; öyle
kalması gerekiyor. Bir parolayı yapılandırma dosyasına yazmayın, log'a basmayın, kullanıcıya
dönen çıktıda tam olarak tekrarlamayın.

## 7. Bilinen açıklar

- Governance iskeleti (permission-matrix, subagent-profiles, scripts/bootstrap-check,
  `.github/workflows`) bu repoya **henüz kopyalanmadı**; diğer üç repoda var.
- ERP yazma yolu yok; Mikro'nun desteklenen entegrasyon yolu olup olmadığı bayiye
  sorulmadı (D-122 açık sorusu).
