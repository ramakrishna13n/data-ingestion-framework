package com.ingestion.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.h2")
    public BaseDataSourceProperties batchDataSourceProperties() {
        return new BaseDataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.redshift")
    public BaseDataSourceProperties redshiftDataSourceProperties() {
        return new BaseDataSourceProperties();
    }

    @Bean(name = "batchDataSource")
    public DataSource batchDataSource(@Qualifier("batchDataSourceProperties") BaseDataSourceProperties batchProperties) {
        return createHikariDataSource(batchProperties);
    }

    @Bean(name = "redshiftDataSource")
    public DataSource redshiftDataSource(@Qualifier("redshiftDataSourceProperties") BaseDataSourceProperties redshiftProperties) {
        return createRedshiftDataSource(redshiftProperties);
    }

    @Bean(name = "batchJdbcTemplate")
    public JdbcTemplate batchJdbcTemplate(@Qualifier("batchDataSource") DataSource batchDataSource) {
        return new JdbcTemplate(batchDataSource);
    }

    @Bean(name = "redshiftJdbcTemplate")
    public JdbcTemplate redshiftJdbcTemplate(@Qualifier("redshiftDataSource") DataSource redshiftDataSource) {
        return new JdbcTemplate(redshiftDataSource);
    }

    private DataSource createHikariDataSource(BaseDataSourceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getUrl());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        return dataSource;
    }

    private DataSource createRedshiftDataSource(BaseDataSourceProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(properties.getUrl());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        return dataSource;
    }
}
