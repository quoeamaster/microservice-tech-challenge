# --- Build stage: compile and run the tests with the Maven wrapper ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

# Copy the build files first so dependency downloads are cached between code changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B package

# --- Runtime stage: JRE only, runs as a non-root user ---
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home /app app \
    && mkdir -p /app/data && chown -R app:app /app
COPY --from=build /build/target/my-pet-photo-microservice-*.jar /app/app.jar

USER app
EXPOSE 9081
# The SQLite database lives in /app/data; mount a volume there to keep photos between runs.
VOLUME /app/data
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
