FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/transferencia.jar app.jar
CMD ["java", "-cp", "app.jar", "transferencia.GeradorArquivos", "/arquivos"]
