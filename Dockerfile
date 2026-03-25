FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
# Ensure wrapper is executable inside container
RUN chmod +x mvnw
RUN ./mvnw -B clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]