FROM eclipse-temurin:17.0.21_9-jdk AS build
WORKDIR /src
COPY demo-server/gradlew demo-server/gradlew
COPY demo-server/gradle demo-server/gradle
COPY demo-server/build.gradle demo-server/settings.gradle demo-server/
COPY demo-server/src/main demo-server/src/main
WORKDIR /src/demo-server
RUN chmod +x gradlew && ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:17.0.21_9-jre
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Xms32m -Xmx128m -XX:MaxMetaspaceSize=64m -XX:MaxDirectMemorySize=64m -Xss256k"
WORKDIR /app
COPY --from=build /src/demo-server/build/libs/demo-server.jar demo-server.jar
RUN groupadd --gid 10001 demo \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin demo
USER demo
ENTRYPOINT ["java", "-jar", "demo-server.jar"]
