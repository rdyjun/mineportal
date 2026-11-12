FROM eclipse-temurin:24.0.2_12-jdk AS builder
WORKDIR /app
COPY server/gradlew ./
COPY server/gradle gradle
COPY server/build.gradle server/settings.gradle ./
RUN chmod +x gradlew
COPY server/src src
COPY shared/src/main/java /shared/src/main/java
COPY client /client
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:24.0.2_12-jre
WORKDIR /app
COPY --from=builder /app/build/libs/app.jar ./app.jar

EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]
