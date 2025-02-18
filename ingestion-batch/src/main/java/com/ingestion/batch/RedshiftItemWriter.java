package com.ingestion.batch;

import lombok.NonNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RedshiftItemWriter implements ItemWriter<StockData> {

    private final JdbcTemplate jdbcTemplate;
    @Value("${aws.s3.bucket}")
    private String s3Bucket;
    @Value("${aws.redshift.roleArn}")
    private String redshiftRoleArn;

    public RedshiftItemWriter(@Qualifier("redshiftJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void write(@NonNull Chunk<? extends StockData> chunk) throws Exception {
        if (chunk.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO stock_data (stock_symbol, trade_date, open_price, high_price, low_price, close_price, adjusted_close_price, volume, dividend_amount, split_coefficient) VALUES ";
        String values = chunk.getItems().stream()
                .map(data -> String.format(
                        "('%s', '%s', %.4f, %.4f, %.4f, %.4f, %.4f, %d, %.4f, %.4f)",
                        data.getStockSymbol(),
                        data.getTradeDate(),
                        data.getOpenPrice(),
                        data.getHighPrice(),
                        data.getLowPrice(),
                        data.getClosePrice(),
                        data.getAdjustedClosePrice(),
                        data.getVolume(),
                        data.getDividendAmount(),
                        data.getSplitCoefficient()
                ))
                .collect(Collectors.joining(", "));

        // 🔹 3. Execute the batched insert statement
        String finalSQL = sql + values + ";";
        jdbcTemplate.execute(finalSQL);
    }

    /*
    //Use COPY in case of large files.
    @Override
    public void write(@NonNull Chunk<? extends StockData> chunk) throws Exception {
       StockData data =  chunk.getItems().get(0);
       System.out.println("data"+ data);
        String copySQL = String.format("""
                    COPY stock_data
                    FROM 's3://%s/stock-data/'
                    IAM_ROLE '%s'
                    FORMAT AS CSV
                    IGNOREHEADER 1
                    COMPUPDATE OFF
                    STATUPDATE OFF
                    REGION 'us-east-2'
                    ACCEPTINVCHARS ' '
                    DATEFORMAT 'YYYY-MM-DD';
                """, s3Bucket, redshiftRoleArn);

        jdbcTemplate.execute(copySQL);
    }*/
}
