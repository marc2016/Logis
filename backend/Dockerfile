# Multi-arch: the build stage runs natively on the build machine (its output is platform-independent),
# only the runtime stage is built per target platform. All base images exist for amd64 and arm64.

FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
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
# 8080: application (internal only, via nginx), 8081: management/health (internal only)
EXPOSE 8080 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
