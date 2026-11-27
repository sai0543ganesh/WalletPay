FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline
COPY backend/src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S -g 10001 walletpay \
    && adduser -S -u 10001 -G walletpay walletpay
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/walletpay-api-1.0.0.jar /app/app.jar
COPY --chmod=0555 infra/docker/backend-entrypoint.sh /app/entrypoint.sh
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -Duser.timezone=UTC"
ENTRYPOINT ["/app/entrypoint.sh"]
