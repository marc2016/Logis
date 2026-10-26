# Multi-Arch: alle Basis-Images gibt es fuer amd64 und arm64, daher keine platform-Angabe.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests \
    && cp target/logis-backend-*.jar /app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 logis \
    && useradd --system --uid 10001 --gid logis --no-create-home --shell /usr/sbin/nologin logis
WORKDIR /app
COPY --from=build /app.jar app.jar
USER logis
# 8080: Anwendung (nur intern ueber nginx), 8081: Management/Health (nur intern)
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
