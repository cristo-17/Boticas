# Java 25 / Maven 3.9 verificados en Docker Hub antes de escribir esto
# (regla "verificar antes de adivinar", CLAUDE.md) — no hay variante
# Alpine publicada para Temurin 25, se usa la base Ubuntu ("noble").

# Etapa 1: compilar el jar con Maven + JDK 25 completo.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B clean package -DskipTests

# Etapa 2: runtime solo con JRE (sin Maven ni JDK de compilación).
FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
