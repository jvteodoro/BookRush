FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY platform platform
RUN mvn -q -f platform/pom.xml install -DskipTests
COPY services/book-ingestion-service/pom.xml pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY services/book-ingestion-service/src src
RUN mvn -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bookrush && adduser -S bookrush -G bookrush
RUN mkdir -p /var/lib/bookrush/ingestion && chown -R bookrush:bookrush /var/lib/bookrush
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER bookrush
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
