FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copia el pom y las fuentes
COPY pom.xml .
COPY src ./src

# Compila el JAR
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

# Copia solo el JAR desde la etapa anterior
COPY --from=build /app/target/gastos-app.jar /app/gastos-app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/gastos-app.jar"]
