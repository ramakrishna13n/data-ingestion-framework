package com.ingestion.batch;

import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
@EnableBatchProcessing(dataSourceRef = "batchDataSource")
public class BatchConfig {

    private final DataSource batchDataSource;
    private final DataSource redshiftDataSource;
    private final PlatformTransactionManager batchTransactionManager;
    private final PlatformTransactionManager redshiftTransactionManager;

    public BatchConfig(
            @Qualifier("batchDataSource") DataSource batchDataSource,
            @Qualifier("redshiftDataSource") DataSource redshiftDataSource,
            @Qualifier("transactionManager") PlatformTransactionManager batchTransactionManager,
            @Qualifier("redshiftTransactionManager") PlatformTransactionManager redshiftTransactionManager) {
        this.batchDataSource = batchDataSource;
        this.redshiftDataSource = redshiftDataSource;
        this.batchTransactionManager = batchTransactionManager;
        this.redshiftTransactionManager = redshiftTransactionManager;
    }

    @PostConstruct
    public void initializeBatchSchema() {
        try (Connection connection = batchDataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("org/springframework/batch/core/schema-h2.sql"));
            System.out.println("Spring Batch Metadata Tables Created!");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Spring Batch schema", e);
        }
    }

    @SneakyThrows
    @Bean
    public JobRepository jobRepository() {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        factory.setDataSource(batchDataSource);
        factory.setTransactionManager(batchTransactionManager);
        factory.setDatabaseType("H2");
        factory.afterPropertiesSet();
        return factory.getObject();
    }

    @Bean
    public Job dataIngestionJob(JobRepository jobRepository,
                                @Qualifier("createTableStep") Step createTableStep,
                                @Qualifier("redshiftIngestionStep") Step redshiftIngestionStep,
                                @Qualifier("openSearchIngestionStep") Step openSearchIngestionStep) {
        return new JobBuilder("dataIngestionJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(createTableStep)
                //.next(redshiftIngestionStep)
                .next(openSearchIngestionStep)
                .build();
    }

    @Bean
    public Step createTableStep(JobRepository jobRepository, CreateTableTasklet tasklet) {
        return new StepBuilder("createTableStep", jobRepository)
                .tasklet(tasklet, redshiftTransactionManager)
                .build();
    }

    @Bean
    public Step redshiftIngestionStep(JobRepository jobRepository,
                                      S3CsvItemReader s3CsvItemReader,
                                      StockDataProcessor processor,
                                      RedshiftItemWriter writer) {
        return new StepBuilder("redshiftIngestionStep", jobRepository)
                .<StockData, StockData>chunk(100, redshiftTransactionManager)
                .reader(s3CsvItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Step openSearchIngestionStep(JobRepository jobRepository,
                                        S3CsvItemReader s3CsvItemReader,
                                        StockDataProcessor processor,
                                        OpenSearchItemWriter writer) {
        return new StepBuilder("openSearchIngestionStep", jobRepository)
                .<StockData, StockData>chunk(100, batchTransactionManager)
                .reader(s3CsvItemReader)
                .processor(processor)
                .writer(writer)
                .build();
    }
}
