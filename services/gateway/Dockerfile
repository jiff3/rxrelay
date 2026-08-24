# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY services/gateway/pom.xml services/gateway/pom.xml
COPY services/core-service/pom.xml services/core-service/pom.xml
COPY services/gateway/src services/gateway/src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
  mvn --batch-mode -pl services/gateway -am \
    -DskipTestDependencies -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S rxrelay && adduser -S rxrelay -G rxrelay
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=20 -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
COPY --from=build --chown=rxrelay:rxrelay /workspace/services/gateway/target/gateway-*.jar /app.jar
USER rxrelay
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=35s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app.jar"]
