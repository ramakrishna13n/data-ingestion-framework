package com.ingestion.batch;

import lombok.NonNull;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenSearchItemWriter implements ItemWriter<StockData> {

    private static final Logger logger = LoggerFactory.getLogger(OpenSearchItemWriter.class);
    private static final int BATCH_SIZE = 100;
    private final OpenSearchClient openSearchClient;
    private final String openSearchIndex;

    public OpenSearchItemWriter(
            @Value("${aws.opensearch.index}") String openSearchIndex,
            @Value("${aws.opensearch.endpoint}") String openSearchEndpoint,
            @Value("${aws.region}") String awsRegion) {

        this.openSearchIndex = openSearchIndex;
        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        SdkHttpClient httpClient = UrlConnectionHttpClient.create();
        this.openSearchClient = new OpenSearchClient(
                new AwsSdk2Transport(
                        httpClient,
                        openSearchEndpoint,
                        "es",
                        Region.of(awsRegion),
                        AwsSdk2TransportOptions.builder()
                                .setCredentials(credentialsProvider)
                                .build()
                )
        );
    }

    @Override
    public void write(@NonNull Chunk<? extends StockData> chunk) {
        List<BulkOperation> bulkOperations = new ArrayList<>();

        for (StockData stock : chunk) {
            bulkOperations.add(new BulkOperation.Builder()
                    .index(index -> index
                            .index(openSearchIndex)
                            .document(stock)
                    ).build());
            if (bulkOperations.size() >= BATCH_SIZE) {
                executeBulkInsert(bulkOperations);
                bulkOperations.clear();
            }
        }
        if (!bulkOperations.isEmpty()) {
            executeBulkInsert(bulkOperations);
        }
    }

    private void executeBulkInsert(List<BulkOperation> bulkOperations) {
        try {
            BulkRequest bulkRequest = new BulkRequest.Builder()
                    .operations(bulkOperations)
                    .refresh(Refresh.True)
                    .build();
            BulkResponse response = openSearchClient.bulk(bulkRequest);
            if (response.errors()) {
                logger.error("Errors occurred while indexing documents in OpenSearch.");
            } else {
                logger.info("Successfully indexed {} documents in OpenSearch.", bulkOperations.size());
            }
        } catch (IOException e) {
            logger.error("Error while sending bulk request to OpenSearch", e);
            throw new RuntimeException("Failed to index documents in OpenSearch", e);
        }
    }
}
