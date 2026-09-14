FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY platform platform
RUN mvn -q -f platform/pom.xml install -DskipTests
COPY services/catalog-service/pom.xml pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY services/catalog-service/src src
RUN mvn -q package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bookrush && adduser -S bookrush -G bookrush
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER bookrush
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
