FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY finance-domain finance-domain
COPY finance-server finance-server
COPY finance-importer/pom.xml finance-importer/pom.xml
RUN mvn -B -pl finance-server -am package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --uid 10001 --create-home finance
COPY --from=build /build/finance-server/target/finance-server-0.4.0.jar /app/server.jar
USER finance
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/server.jar"]
