FROM ubuntu:latest
LABEL authors="pablo"
# Usa una imagen oficial de Java 17
FROM eclipse-temurin:17-jdk

# Establece el directorio de trabajo
WORKDIR /app

# Copia y compila el código
COPY . /app
RUN ./mvnw clean package -DskipTests

# Expone el puerto que tu app usa
EXPOSE 8080

# Ejecuta la app
CMD ["java", "-jar", "target/gastos-app.jar"]

ENTRYPOINT ["top", "-b"]