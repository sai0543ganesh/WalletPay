FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=UTC
COPY --from=build /app/target/wallet-pay-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Duser.timezone=UTC","-jar","/app/app.jar"]
