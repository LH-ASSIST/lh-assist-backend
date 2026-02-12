FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew clean bootJar -x test

FROM node:20-bookworm-slim AS node_deps
WORKDIR /opt/report-charts
COPY package*.json ./
RUN if [ -f package-lock.json ]; then npm ci --omit=dev || npm install --omit=dev; else npm install --omit=dev; fi

FROM eclipse-temurin:21-jre
WORKDIR /opt/report-charts

RUN apt-get update \
    && apt-get install -y --no-install-recommends nodejs npm \
    && rm -rf /var/lib/apt/lists/*

ARG JAR_FILE=/workspace/build/libs/*.jar
COPY --from=build ${JAR_FILE} /opt/report-charts/app.jar
COPY --from=node_deps /opt/report-charts/node_modules /opt/report-charts/node_modules
COPY scripts /opt/report-charts/scripts

EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/report-charts/app.jar"]
