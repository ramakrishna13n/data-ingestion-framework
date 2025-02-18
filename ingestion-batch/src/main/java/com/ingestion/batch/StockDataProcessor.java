package com.ingestion.batch;

import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Getter
@Component
public class StockDataProcessor implements ItemProcessor<StockData, StockData> {
    private static final Logger logger = LoggerFactory.getLogger(StockDataProcessor.class);
    private int failedRecords = 0;

    @Override
    public StockData process(@NonNull StockData stock) {
        try {
            if (stock.getStockSymbol() == null || stock.getStockSymbol().isEmpty()) {
                throw new IllegalArgumentException("Invalid Stock Symbol");
            }
            if (stock.getTradeDate() == null) {
                throw new IllegalArgumentException("Missing Trade Date");
            }
            if (stock.getVolume() < 0) {
                throw new IllegalArgumentException("Invalid Volume");
            }
            return stock;
        } catch (Exception e) {
            failedRecords++;
            logger.error("Data Validation Failed: {} - {}", stock, e.getMessage());
            return null;
        }
    }
}
