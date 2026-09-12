FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /usr/sbin/nologin appuser

COPY --from=build /app/target/admin-panel.jar /app/admin-panel.jar

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 9093

ENTRYPOINT ["java", "-jar", "/app/admin-panel.jar"]
