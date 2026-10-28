# Etapa 1: compilar el .jar con el Maven Wrapper del proyecto
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

# Etapa 2: imagen liviana solo con Java para ejecutar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/tratoseguro-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Memoria limitada para el plan gratuito del servidor (512 MB)
ENTRYPOINT ["java", "-Xmx300m", "-jar", "app.jar"]
