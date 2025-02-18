package com.ingestion.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class TransactionManagerConfig {

    private final DataSource batchDataSource;
    private final DataSource redshiftDataSource;

    public TransactionManagerConfig(
            @Qualifier("batchDataSource") DataSource batchDataSource,
            @Qualifier("redshiftDataSource") DataSource redshiftDataSource) {
        this.batchDataSource = batchDataSource;
        this.redshiftDataSource = redshiftDataSource;
    }

    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager batchTransactionManager() {
        return new DataSourceTransactionManager(batchDataSource);
    }

    @Bean(name = "redshiftTransactionManager")
    public PlatformTransactionManager redshiftTransactionManager() {
        return new DataSourceTransactionManager(redshiftDataSource);
    }
}
