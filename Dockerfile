# === Stage 1: Build ===
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# === Stage 2: Run ===
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/bookmind-backend-1.0.0.jar app.jar
COPY application.yml application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
