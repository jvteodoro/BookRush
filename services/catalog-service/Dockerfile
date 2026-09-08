FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S bookrush && adduser -S bookrush -G bookrush
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER bookrush
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
