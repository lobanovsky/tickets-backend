FROM gradle:8.13-jdk21 AS builder
WORKDIR /app

COPY . .

RUN gradle clean build -x test

FROM mcr.microsoft.com/playwright/java:v1.52.0-noble
WORKDIR /app
COPY --from=builder /app/build/libs/tickets-backend-all.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]