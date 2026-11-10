# ---- Builder Stage ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
ARG MAVEN_TLS_INSECURE=false
WORKDIR /app
COPY pom.xml .
RUN mvn -Dmaven.resolver.transport=wagon \
    -Dmaven.wagon.http.ssl.insecure=${MAVEN_TLS_INSECURE} \
    -Dmaven.wagon.http.ssl.allowall=${MAVEN_TLS_INSECURE} \
    dependency:go-offline -B -q
COPY src/ src/
RUN mvn -Dmaven.resolver.transport=wagon \
    -Dmaven.wagon.http.ssl.insecure=${MAVEN_TLS_INSECURE} \
    -Dmaven.wagon.http.ssl.allowall=${MAVEN_TLS_INSECURE} \
    package -DskipTests -B -q

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
