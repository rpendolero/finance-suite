FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY finance-domain finance-domain
COPY finance-statements finance-statements
COPY finance-server finance-server
# The API build needs domain, statements and server; remove the importer from this build reactor.
RUN sed -i '/<module>finance-importer<\/module>/d' pom.xml \
    && mvn -B -pl finance-server -am package -DskipTests \
    && cp finance-server/target/finance-server-*.jar /build/server.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --uid 10001 --create-home finance
COPY --from=build /build/server.jar /app/server.jar
ENV SERVER_ADDRESS=0.0.0.0
USER finance
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/server.jar"]
