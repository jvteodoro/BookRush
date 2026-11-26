FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY platform platform
COPY infrastructure/keycloak/age-provider infrastructure/keycloak/age-provider
RUN mvn -B -q -f platform/pom.xml -pl bookrush-common-core -am install -DskipTests &&     mvn -B -q -f infrastructure/keycloak/age-provider/pom.xml package -DskipTests

FROM quay.io/keycloak/keycloak:25.0.6
COPY --from=build /workspace/platform/bookrush-common-core/target/bookrush-common-core-*.jar /opt/keycloak/providers/
COPY --from=build /workspace/infrastructure/keycloak/age-provider/target/bookrush-keycloak-age-*.jar /opt/keycloak/providers/
