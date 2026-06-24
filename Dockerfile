# Etapa 1: Compilación
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests \
        -D"SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/placeholder" \
        -D"SPRING_DATASOURCE_USERNAME=placeholder" \
        -D"SPRING_DATASOURCE_PASSWORD=placeholder"

# Etapa 2: Ejecución
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]