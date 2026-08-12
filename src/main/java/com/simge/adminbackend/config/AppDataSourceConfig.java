package com.simge.adminbackend.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * SIMGE_OS_APP — vitrinle <b>paylaşılan</b> kendi veritabanımız (okuma/yazma).
 *
 * <p>
 * Paylaşım bilinçli: kullanıcılar, davetler, kayıt başvuruları ve vitrin
 * ayarları tek yerde dursun; iki servis arasına senkronizasyon API'si yazmak
 * gerekmesin. Bedeli şema sahipliğinin bulanıklaşmasıdır, bunun karşılığı da
 * <b>tablo bazlı sahiplik</b>:
 * </p>
 * <ul>
 *   <li>{@code SIMGE_OS_BE} sahibi: SIMGE_USER, SIMGE_REGISTRATION_REQUEST,
 *       SIMGE_COMPANY_INVITATION, SIMGE_HOME_SECTION*, SPRING_SESSION*
 *       — şemaları {@code db/app} altında.</li>
 *   <li>Bu servis sahibi: SIMGE_STAFF_USER, SIMGE_STAFF_ROLE,
 *       SIMGE_STAFF_SESSION* — şemaları {@code db/admin} altında.</li>
 * </ul>
 *
 * <p>
 * Hibernate burada da DDL üretmez ({@code ddl-auto=validate}); şema değişikliği
 * her zaman versiyonlu SQL script'i ile yapılır.
 * </p>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.simge.adminbackend.appdb.repository",
        entityManagerFactoryRef = "appEntityManagerFactory",
        transactionManagerRef = "appTransactionManager")
public class AppDataSourceConfig {

    @Bean
    @ConfigurationProperties("simge.app-datasource")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Oturumlar da bu veritabanına yazılır.
     *
     * <p>
     * {@code @SpringSessionDataSource} şart: Spring Session JDBC varsayılan
     * olarak <b>birincil</b> datasource'u kullanır, o da Mikro ERP'dir. Bu
     * niteleyici olmadan uygulama ERP'ye oturum satırı yazmaya çalışırdı.
     * </p>
     */
    @Bean
    @SpringSessionDataSource
    public DataSource appDataSource(
            @Qualifier("appDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean appEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("appDataSource") DataSource dataSource) {

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");

        return builder
                .dataSource(dataSource)
                .packages("com.simge.adminbackend.appdb.model")
                .persistenceUnit("app")
                .properties(props)
                .build();
    }

    @Bean
    public PlatformTransactionManager appTransactionManager(
            @Qualifier("appEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }
}
