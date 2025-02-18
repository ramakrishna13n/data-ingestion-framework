# Use a minimal JDK image for efficiency
FROM amazoncorretto:17-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/ingestion-batch-1.0-SNAPSHOT.jar ingestion-batch.jar

# Set environment variables for AWS (use in application.properties)
ENV AWS_REGION="AWS Region"
ENV REDSHIFT_JDBC_URL="Redshift URL"
ENV OPENSEARCH_ENDPOINT="OpenSearch URL"

# Command to run the Spring Batch job
ENTRYPOINT ["java", "-jar", "ingestion-batch.jar"]
