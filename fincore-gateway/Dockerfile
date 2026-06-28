FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY account-service/pom.xml account-service/
COPY transaction-service/pom.xml transaction-service/
COPY notification-service/pom.xml notification-service/
COPY fincore-gateway/pom.xml fincore-gateway/
RUN mvn dependency:go-offline -B -pl fincore-gateway -am
COPY fincore-gateway/src fincore-gateway/src
RUN mvn clean package -DskipTests -pl fincore-gateway -am

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/fincore-gateway/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
