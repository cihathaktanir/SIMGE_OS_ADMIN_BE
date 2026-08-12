package com.simge.adminbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simge Yönetim API'si — <b>intranet</b> (ADR D-122).
 *
 * <p>
 * Vitrin backend'inden ({@code SIMGE_OS_BE}) ayrı bir süreçtir. Ayrımın sebebi
 * yalnızca düzen değil, güven bölgesi:
 * </p>
 * <ul>
 *   <li>Vitrin internete açık ve Mikro'ya <b>yazamaz</b> — yazma yolu kodda
 *       yok, olmadığı için derleme hatası (D-104).</li>
 *   <li>Bu servis ise ileride Mikro'ya sınırlı yazma yapacak. O yeteneği
 *       internete açık sürecin içine koymak, oradaki her açığı ERP'ye
 *       taşıyan bir köprü kurardı.</li>
 *   <li>Ayrı süreç = ayrı bağlantı havuzu = ayrı MSSQL kullanıcısı. Vitrinin
 *       Mikro girişi SELECT-only kalabilir; yazma yetkisi yalnızca burada.</li>
 * </ul>
 *
 * <p>
 * İki servis {@code SIMGE_OS_APP} veritabanını <b>paylaşır</b> (kullanıcılar,
 * davetler, vitrin ayarları tek yerde dursun diye) ama tablo sahipliği ayrıdır:
 * bu servisin tabloları {@code db/admin} altındaki script'lerle yönetilir.
 * </p>
 */
@SpringBootApplication
public class AdminBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminBackendApplication.class, args);
    }
}
