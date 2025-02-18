package com.search.sample;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.redshiftdata.RedshiftDataClient;
import software.amazon.awssdk.services.redshiftdata.model.*;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RedshiftQueryService {

    private static final String REDSHIFT_JDBC_URL = System.getenv("REDSHIFT_JDBC_URL");

    private final RedshiftDataClient redshiftClient;
    private LambdaLogger logger; // Logger instance

    @Inject
    public RedshiftQueryService() {
        this.redshiftClient = RedshiftDataClient.builder()
                .region(Region.of(System.getenv("AWS_REGION")))
                .credentialsProvider(DefaultCredentialsProvider.create()) // Uses IAM Role
                .build();
    }

    public void setLogger(LambdaLogger logger) {
        this.logger = logger;
    }

    /**
     * Executes a SQL query using IAM authentication (No username/password needed).
     */
    public List<Map<String, Object>> executeQuery(String sqlQuery) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            long startTime = System.currentTimeMillis();
            logger.log("[INFO] Executing SQL query: " + sqlQuery);

            ExecuteStatementRequest request = ExecuteStatementRequest.builder()
                    .workgroupName(getWorkgroupFromJdbcUrl())
                    .database(getDatabaseFromJdbcUrl())
                    .sql(sqlQuery)
                    .build();

            ExecuteStatementResponse response = redshiftClient.executeStatement(request);
            String statementId = response.id();
            logger.log("[INFO] Query execution started (Statement ID: " + statementId + ")");

            // Wait for query completion
            DescribeStatementResponse result;
            do {
                Thread.sleep(2000); // Poll every 2 seconds
                result = redshiftClient.describeStatement(DescribeStatementRequest.builder()
                        .id(statementId)
                        .build());
                logger.log("[DEBUG] Query Status: " + result.status());
            } while (!result.status().equals(StatusString.FINISHED));

            // Fetch query results
            GetStatementResultResponse queryResult = redshiftClient.getStatementResult(
                    GetStatementResultRequest.builder().id(statementId).build());

            long executionTime = System.currentTimeMillis() - startTime;
            logger.log("[INFO] Query execution completed in " + executionTime + "ms (Statement ID: " + statementId + ")");

            // Process rows
            queryResult.records().forEach(record -> {
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < queryResult.columnMetadata().size(); i++) {
                    row.put(queryResult.columnMetadata().get(i).name(), record.get(i).stringValue());
                }
                results.add(row);
            });

        } catch (InterruptedException e) {
            logger.log("[ERROR] Query execution interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (RedshiftDataException e) {
            logger.log("[ERROR] Redshift Data API error: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            logger.log("[ERROR] Unexpected error executing query: " + e.getMessage());
        }

        return results;
    }

    private String getWorkgroupFromJdbcUrl() {
        return RedshiftQueryService.REDSHIFT_JDBC_URL.split("//")[1].split("\\.")[0];
    }

    private String getDatabaseFromJdbcUrl() {
        return RedshiftQueryService.REDSHIFT_JDBC_URL.split(":5439/")[1];
    }
}
