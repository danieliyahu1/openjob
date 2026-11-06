FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 -S app && adduser -u 10001 -S -G app app
WORKDIR /app
COPY --from=build /app/target/openjob-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "app.jar"]
