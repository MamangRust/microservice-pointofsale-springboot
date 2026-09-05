FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy and install the shared common module first
COPY common ./common
RUN mvn clean install -f common/pom.xml -DskipTests -B

# Copy the service pom.xml and src code
COPY file-storage-service/pom.xml ./file-storage-service/
COPY file-storage-service/src ./file-storage-service/src

# Package the file-storage-service
RUN mvn clean package -f file-storage-service/pom.xml -DskipTests -B

FROM eclipse-temurin:21-jdk-alpine

LABEL maintainer="file-storage-service"
LABEL version="1.0.0"
LABEL description="File-storage-service with OpenTelemetry and Common library"

RUN addgroup -S app && adduser -S app -G app && \
    apk add --no-cache curl
WORKDIR /app

COPY --from=build /app/file-storage-service/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8087

ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75 \
    -XX:+UseG1GC \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

ENV OTEL_SERVICE_NAME=file-storage-service
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
ENV OTEL_METRICS_EXPORTER=otlp
ENV OTEL_LOGS_EXPORTER=otlp

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
