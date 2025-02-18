package com.ingestion.batch;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;

@Component
public class S3CsvItemReader implements ItemReader<StockData> {

    private final S3Client s3Client;
    private final Queue<String> fileQueue = new LinkedList<>();
    private final String s3Bucket = "<Your s3 bucket name>";
    private final String s3Prefix = "stock-data/";
    private FlatFileItemReader<StockData> csvReader;

    public S3CsvItemReader(S3Client s3Client) {
        this.s3Client = s3Client;
        loadFileList();
    }

    private void loadFileList() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(s3Bucket)
                .prefix(s3Prefix)
                .build();

        ListObjectsV2Response result = s3Client.listObjectsV2(request);
        for (S3Object objectSummary : result.contents()) {
            if (objectSummary.key().endsWith(".csv")) {
                fileQueue.add(objectSummary.key());
            }
        }
    }

    @Override
    public StockData read() throws Exception {
        if (csvReader == null || csvReader.read() == null) {
            if (fileQueue.isEmpty()) {
                return null; // No more files left to process
            }
            String nextFile = fileQueue.poll();
            System.out.println("Processing file: " + nextFile);
            csvReader = createCsvReader(nextFile);
            csvReader.open(new ExecutionContext());
        }
        return csvReader.read();
    }

    private FlatFileItemReader<StockData> createCsvReader(String fileName) {
        byte[] s3Data = s3Client.getObjectAsBytes(b -> b.bucket(s3Bucket).key(fileName)).asByteArray();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(s3Data)));

        FlatFileItemReader<StockData> fileReader = new FlatFileItemReader<>();
        fileReader.setResource(new InputStreamResource(new ByteArrayInputStream(reader.lines().collect(java.util.stream.Collectors.joining("\n")).getBytes())));
        fileReader.setLinesToSkip(1); // Skip CSV header

        fileReader.setLineMapper(new DefaultLineMapper<>() {{
            setLineTokenizer(new DelimitedLineTokenizer() {{
                setNames("tradeDate", "openPrice", "highPrice", "lowPrice", "closePrice", "adjustedClosePrice", "volume", "dividendAmount", "splitCoefficient");
            }});
            setFieldSetMapper(fieldSet -> new StockData(
                    extractStockSymbol(fileName),
                    fieldSet.readDate("tradeDate", "yyyy-MM-dd"),
                    fieldSet.readBigDecimal("openPrice"),
                    fieldSet.readBigDecimal("highPrice"),
                    fieldSet.readBigDecimal("lowPrice"),
                    fieldSet.readBigDecimal("closePrice"),
                    fieldSet.readBigDecimal("adjustedClosePrice"),
                    fieldSet.readLong("volume"),
                    fieldSet.readBigDecimal("dividendAmount"),
                    fieldSet.readBigDecimal("splitCoefficient")
            ));
        }});

        return fileReader;
    }

    private String extractStockSymbol(String fileName) {
        return fileName.replace("stock-data/daily_adjusted_", "").replace(".csv", "").toUpperCase();
    }
}
