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
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Mikro ERP veritabanı (MikroDB_V15_2021).
 *
 * <p>
 * <b>Bugün salt okunur.</b> Bu servis ileride ERP'ye sınırlı yazma yapacak
 * (D-122) ama o yetenek <i>henüz eklenmedi</i>: repository'ler hâlâ
 * {@link com.simge.adminbackend.erp.ReadOnlyRepository} üzerinden türüyor, yani
 * yazmak burada da derleme hatası. Yazma geldiğinde tek tek, hangi tabloya
 * hangi alanın yazılacağı ayrı ayrı kararlaştırılarak açılacak — "artık admin
 * backend'iz, her yere yazabiliriz" diye toptan açılmayacak.
 * </p>
 *
 * <p>
 * {@code ddl-auto=none}: şema ERP'ye ait (D-100), Hibernate'in ona dokunması
 * söz konusu değil.
 * </p>
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.simge.adminbackend.erp.repository",
        entityManagerFactoryRef = "mikroEntityManagerFactory",
        transactionManagerRef = "mikroTransactionManager")
public class MikroDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mikroDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource mikroDataSource(
            @Qualifier("mikroDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean mikroEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mikroDataSource") DataSource dataSource) {

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect");

        return builder
                .dataSource(dataSource)
                .packages("com.simge.adminbackend.erp.model")
                .persistenceUnit("mikro")
                .properties(props)
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager mikroTransactionManager(
            @Qualifier("mikroEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }
}
