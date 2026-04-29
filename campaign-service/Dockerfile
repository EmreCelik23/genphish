FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy only the pom first so dependencies can be cached.
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

# Copy source and build the executable Spring Boot jar.
COPY src ./src
RUN mvn -B -ntp -DskipTests -Dmaven.test.skip=true clean package

# Extract Spring Boot layers for better image caching.
RUN cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
