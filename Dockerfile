# Etapa 1: Compilación
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Render (o docker-compose) → Spring las lee desde el environment.
ENTRYPOINT ["java", "-jar", "app.jar"]