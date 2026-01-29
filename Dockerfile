FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew clean bootJar -x test

FROM eclipse-temurin:21-jre
ARG JAR_FILE=/workspace/build/libs/*.jar
COPY --from=build ${JAR_FILE} /app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
