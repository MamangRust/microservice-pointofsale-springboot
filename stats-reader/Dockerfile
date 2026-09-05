FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY common ./common
RUN mvn clean install -f common/pom.xml -DskipTests -B

COPY stats-reader/pom.xml ./stats-reader/
COPY stats-reader/src ./stats-reader/src

RUN mvn clean package -f stats-reader/pom.xml -DskipTests -B

FROM eclipse-temurin:21-jdk-alpine

LABEL maintainer="stats-reader"
LABEL version="1.0.0"
LABEL description="Stats Reader — ClickHouse query with Redis cache"

RUN addgroup -S app && adduser -S app -G app && \
    apk add --no-cache curl
WORKDIR /app

COPY --from=build /app/stats-reader/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8096

ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75 \
    -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom"

ENV OTEL_SERVICE_NAME=stats-reader
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
