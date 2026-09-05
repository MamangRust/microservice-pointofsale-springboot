FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY common ./common
RUN mvn clean install -f common/pom.xml -DskipTests -B
COPY order-item-service/pom.xml ./order-item-service/
COPY order-item-service/src ./order-item-service/src
RUN mvn clean package -f order-item-service/pom.xml -DskipTests -B
FROM eclipse-temurin:21-jdk-alpine
LABEL maintainer="order-item-service" version="1.0.0"
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/order-item-service/target/*.jar app.jar
RUN chown app:app app.jar
USER app
EXPOSE 8092
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENV OTEL_SERVICE_NAME=order-item-service OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]