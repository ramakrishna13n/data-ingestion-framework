package com.ingestion.batch;

import lombok.NonNull;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CreateTableTasklet implements Tasklet {

    private final JdbcTemplate redshiftJdbcTemplate;

    public CreateTableTasklet(@Qualifier("redshiftJdbcTemplate") JdbcTemplate redshiftJdbcTemplate) {
        this.redshiftJdbcTemplate = redshiftJdbcTemplate;
    }

    @Override
    public RepeatStatus execute(@NonNull StepContribution contribution, @NonNull ChunkContext chunkContext) {
        String sql = """
                    CREATE TABLE IF NOT EXISTS stock_data (
                        stock_symbol VARCHAR(10) NOT NULL,
                        trade_date DATE NOT NULL,
                        open_price DECIMAL(10,4),
                        high_price DECIMAL(10,4),
                        low_price DECIMAL(10,4),
                        close_price DECIMAL(10,4),
                        adjusted_close_price DECIMAL(10,4),
                        volume BIGINT,
                        dividend_amount DECIMAL(10,4),
                        split_coefficient DECIMAL(10,4),
                        PRIMARY KEY (stock_symbol, trade_date)
                    );
                """;
        redshiftJdbcTemplate.execute(sql);
        return RepeatStatus.FINISHED;
    }
}
