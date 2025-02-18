package com.ingestion;

import com.ingestion.batch.BatchJobRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource("classpath:application.properties")
public class IngestionApplication implements CommandLineRunner {

    private final BatchJobRunner batchJobRunner;

    public IngestionApplication(BatchJobRunner batchJobRunner) {
        this.batchJobRunner = batchJobRunner;
    }

    public static void main(String[] args) {
        SpringApplication.run(IngestionApplication.class, args);
    }

    @Override
    public void run(String... args) {
        batchJobRunner.runJob();
    }
}
