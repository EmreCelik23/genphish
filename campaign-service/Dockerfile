FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build

WORKDIR /workspace

# Copy only the pom first so dependencies can be cached.
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

# Copy source and build the executable Spring Boot jar.
COPY src ./src
COPY docker ./docker
RUN mvn -B -ntp -DskipTests -Dmaven.test.skip=true clean package

# Extract Spring Boot layers for better image caching.
RUN cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Compile a tiny JVM-based healthcheck helper used by Docker HEALTHCHECK.
RUN javac -d /workspace/healthcheck-classes /workspace/docker/Healthcheck.java \
    && jar --create --file /workspace/healthcheck.jar -C /workspace/healthcheck-classes .

FROM gcr.io/distroless/java21-debian12:nonroot@sha256:7e37784d94dccbf5ccb195c73b295f5ad00cd266512dfbac12eb9c3c28f8077d

WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
COPY --from=build /workspace/healthcheck.jar /app/healthcheck.jar

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD ["java", "-cp", "/app/healthcheck.jar", "Healthcheck", "http://127.0.0.1:8080/actuator/health/readiness"]

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
