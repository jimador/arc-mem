# syntax=docker/dockerfile:1

# --- Stage 1: Build ---
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# Maven profiles activate on these env vars during build
ARG OPENAI_API_KEY=""
ARG ANTHROPIC_API_KEY=""
ENV OPENAI_API_KEY=${OPENAI_API_KEY}
ENV ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}

# Dependency cache layer — copy only what Maven needs to resolve deps
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw dependency:resolve -B -q

# Full source build with Vaadin production mode
COPY src/ src/
COPY frontend/ frontend/
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw clean package -DskipTests -Pproduction -B -q

# --- Stage 2: Layer extraction ---
FROM eclipse-temurin:25-jdk AS extractor

WORKDIR /extract
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# --- Stage 3: Runtime ---
FROM eclipse-temurin:25-jre

RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app

COPY --from=extractor /extract/dependencies/ ./
COPY --from=extractor /extract/spring-boot-loader/ ./
COPY --from=extractor /extract/snapshot-dependencies/ ./
COPY --from=extractor /extract/application/ ./

RUN chown -R appuser:appuser /app
USER appuser

EXPOSE 8089

ENTRYPOINT ["java", "--enable-preview", "org.springframework.boot.loader.launch.JarLauncher"]
