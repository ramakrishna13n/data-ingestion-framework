package com.ingestion.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Component;

@Component
public class BatchJobRunner {
    private static final Logger logger = LoggerFactory.getLogger(BatchJobRunner.class);
    private final JobLauncher jobLauncher;
    private final Job dataIngestionJob;

    public BatchJobRunner(JobLauncher jobLauncher, Job dataIngestionJob) {
        this.jobLauncher = jobLauncher;
        this.dataIngestionJob = dataIngestionJob;
    }

    public void runJob() {
        try {
            JobExecution execution = jobLauncher.run(dataIngestionJob, new JobParameters());
            System.out.println("Job Status: " + execution.getStatus());
        } catch (Exception e) {
            logger.error("Error during job execution", e);
        }
    }
}
