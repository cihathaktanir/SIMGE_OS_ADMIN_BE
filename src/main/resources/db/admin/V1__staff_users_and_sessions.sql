/* =====================================================================
   SIMGE_OS_APP — Simge personeli (yönetim paneli kullanıcıları) ve
   yönetim paneli oturumları.                          ADR D-122 / D-123
   ---------------------------------------------------------------------
   BU TABLOLAR SIMGE_OS_ADMIN_BE'YE AİTTİR. Vitrin backend'i (SIMGE_OS_BE)
   bunlara dokunmaz; onun tabloları db/app/ altındaki script'lerle
   yönetilir — dosya maskesi yazmıyoruz, T-SQL blok yorumları iç içe
   geçebildiği için yorum içindeki bir yıldız-eğik çizgi ikilisi batch'i
   bozuyor. İki servis
   aynı veritabanını paylaşır ama tablo sahipliği ayrıdır — hangi şemayı
   kimin değiştireceği belirsiz kalmasın.

   NEDEN AYRI TABLO (SIMGE_USER'a eklenmedi):
     1. Farklı kimlik: müşteri e-postayla, personel KULLANICI ADIYLA girer.
        Personelin e-posta adresi olmak zorunda değil (depo görevlisi).
     2. Farklı güven bölgesi: SIMGE_USER internete açık servisin okuduğu
        tablo. Personel kimlik bilgisi orada durursa, vitrindeki bir açık
        yönetim hesaplarını da kapsar.
     3. Tek tablo olsaydı "type='STAFF' satırı vitrine giriş yapamaz"
        kuralı bir WHERE koşuluna kalırdı; unutulduğu an yetki tırmanması.

   Çalıştırma:
     sqlcmd -S localhost -U sa -P <parola> -f 65001 -i V1__staff_users_and_sessions.sql
   ===================================================================== */

USE SIMGE_OS_APP;
GO

SET QUOTED_IDENTIFIER ON;
GO

/* ---------------------------------------------------------------------
   SIMGE_STAFF_USER — panele giren Simge çalışanı.

   username: e-posta DEĞİL. Panel yöneticisi "depo1", "ayse" gibi kısa bir
   ad verip hesabı anında açabilsin diye (D-123). Küçük harfe indirgenmiş
   ASCII saklanır; Türkçe karakter kabul edilmez — giriş ekranında yazılan
   bir alanda ı/i ayrımı destek yükünden başka bir şey üretmiyor.

   must_change_password: hesap geçici parolayla açılır ve o parola en az
   iki kişi tarafından bilinir (açan yönetici + kullanıcı). Bu bayrak
   inmeden panelde hiçbir uç çalışmaz (StaffPasswordChangeFilter).
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.SIMGE_STAFF_USER', 'U') IS NULL
CREATE TABLE dbo.SIMGE_STAFF_USER (
    id                   BIGINT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_SIMGE_STAFF_USER PRIMARY KEY,
    username             VARCHAR(50)   NOT NULL,   -- ASCII; küçük harf
    password_hash        NVARCHAR(100) NOT NULL,   -- BCrypt
    full_name            NVARCHAR(150) NULL,
    -- İsteğe bağlı. Personelin e-posta adresi olmak zorunda değil; varsa
    -- yalnızca "kim bu" sorusuna cevap için tutulur, giriş için kullanılmaz.
    email                NVARCHAR(190) NULL,
    status               NVARCHAR(20)  NOT NULL
        CONSTRAINT DF_STAFF_STATUS DEFAULT 'ACTIVE',   -- ACTIVE | DISABLED
    must_change_password BIT           NOT NULL
        CONSTRAINT DF_STAFF_MUSTCHG DEFAULT 1,
    failed_login_count   INT           NOT NULL
        CONSTRAINT DF_STAFF_FAILED DEFAULT 0,
    locked_until         DATETIMEOFFSET(6) NULL,
    last_login_at        DATETIMEOFFSET(6) NULL,
    created_by           BIGINT        NULL,       -- SIMGE_STAFF_USER.id (FK değil: ilk hesabı sistem açar)
    created_at           DATETIME2     NOT NULL
        CONSTRAINT DF_STAFF_CREATED DEFAULT SYSUTCDATETIME(),
    updated_at           DATETIME2     NOT NULL
        CONSTRAINT DF_STAFF_UPDATED DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_SIMGE_STAFF_USERNAME UNIQUE (username)
);
GO

/* ---------------------------------------------------------------------
   SIMGE_STAFF_ROLE — çoklu rol.

   Tek bir `role` kolonu yerine ayrı tablo: bir kişi hem depo hem satış
   olabiliyor ve panelde görünürlük role bağlı. Tek kolon olsaydı ilk
   çift rol ihtiyacında "DEPO_SATIS" gibi birleşik değerler türerdi.
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.SIMGE_STAFF_ROLE', 'U') IS NULL
CREATE TABLE dbo.SIMGE_STAFF_ROLE (
    id        BIGINT IDENTITY(1,1) NOT NULL
        CONSTRAINT PK_SIMGE_STAFF_ROLE PRIMARY KEY,
    staff_id  BIGINT       NOT NULL,
    -- ADMIN | SATIS | DEPO | MUHASEBE | ICERIK
    role      VARCHAR(20)  NOT NULL,
    CONSTRAINT FK_STAFF_ROLE_USER FOREIGN KEY (staff_id)
        REFERENCES dbo.SIMGE_STAFF_USER(id) ON DELETE CASCADE,
    CONSTRAINT UQ_STAFF_ROLE UNIQUE (staff_id, role)
);
GO

/* ---------------------------------------------------------------------
   Yönetim panelinin KENDİ oturum tabloları.

   Vitrinin SPRING_SESSION tablosu paylaşılmıyor: aynı tabloyu kullansalar
   iki uygulamanın oturum nesneleri (AppUserPrincipal / StaffPrincipal)
   aynı yere serileştirilir, biri diğerinin satırını okumaya çalışır ve
   `spring.session.jdbc.table-name` ile ayrılan tek şey zaten budur.
   Ayrıca vitrin oturumlarını silmek yönetim oturumlarını düşürmemeli.

   Şema kaynağı: spring-session-jdbc / schema-sqlserver.sql
   (JdbcIndexedSessionRepository öznitelik tablosunu <tablo>_ATTRIBUTES
    olarak türetir; isim birebir bu olmalı.)
   --------------------------------------------------------------------- */
IF OBJECT_ID('dbo.SIMGE_STAFF_SESSION', 'U') IS NULL
CREATE TABLE dbo.SIMGE_STAFF_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SIMGE_STAFF_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'SIMGE_STAFF_SESSION_IX1')
    CREATE UNIQUE NONCLUSTERED INDEX SIMGE_STAFF_SESSION_IX1
        ON dbo.SIMGE_STAFF_SESSION (SESSION_ID);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'SIMGE_STAFF_SESSION_IX2')
    CREATE NONCLUSTERED INDEX SIMGE_STAFF_SESSION_IX2
        ON dbo.SIMGE_STAFF_SESSION (EXPIRY_TIME);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'SIMGE_STAFF_SESSION_IX3')
    CREATE NONCLUSTERED INDEX SIMGE_STAFF_SESSION_IX3
        ON dbo.SIMGE_STAFF_SESSION (PRINCIPAL_NAME);
GO

IF OBJECT_ID('dbo.SIMGE_STAFF_SESSION_ATTRIBUTES', 'U') IS NULL
CREATE TABLE dbo.SIMGE_STAFF_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)       NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200)   NOT NULL,
    ATTRIBUTE_BYTES    VARBINARY(MAX) NOT NULL,
    CONSTRAINT SIMGE_STAFF_SESSION_ATTRIBUTES_PK
        PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SIMGE_STAFF_SESSION_ATTRIBUTES_FK
        FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES dbo.SIMGE_STAFF_SESSION(PRIMARY_ID) ON DELETE CASCADE
);
GO

PRINT 'V1 uygulandi: SIMGE_STAFF_USER, SIMGE_STAFF_ROLE, SIMGE_STAFF_SESSION(_ATTRIBUTES)';
GO
