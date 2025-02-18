package com.search.sample;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.Map;

@Singleton
public class SearchLambdaHandler implements RequestHandler<Map<String, Object>, String> {

    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final RedshiftQueryService redshiftQueryService;
    private LambdaLogger logger;

    @Inject
    public SearchLambdaHandler(RedshiftQueryService redshiftQueryService) {
        this.redshiftQueryService = redshiftQueryService;
    }

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        this.logger = context.getLogger();
        logger.log("Received event: " + gson.toJson(event));

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) event.get("arguments");
            if (arguments == null) {
                return createErrorResponse("Missing arguments in event");
            }

            String sqlQuery = (String) arguments.get("sqlQuery");
            if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
                return createErrorResponse("SQL query cannot be empty");
            }

            if (!isSafeSQL(sqlQuery)) {
                return createErrorResponse("Unsafe SQL query detected!");
            }

            return gson.toJson(redshiftQueryService.executeQuery(sqlQuery));

        } catch (Exception e) {
            logger.log("Error processing request: " + e.getMessage());
            return createErrorResponse("Internal Server Error: " + e.getMessage());
        }
    }

    private boolean isSafeSQL(String sql) {
        String lowerSql = sql.toLowerCase();
        return !(lowerSql.contains("drop ") || lowerSql.contains("delete ") || lowerSql.contains("insert "));
    }

    private String createErrorResponse(String message) {
        Map<String, String> error = Collections.singletonMap("error", message);
        return gson.toJson(error);
    }
}
