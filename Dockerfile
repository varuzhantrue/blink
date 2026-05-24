# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S blink && adduser -S blink -G blink

COPY --from=builder /app/build/libs/*.jar app.jar

USER blink

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
