# The stylesheet is already built and committed, so this needs no Node.
FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /app
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh ./mvnw -q -B dependency:go-offline
COPY src src
RUN sh ./mvnw -q -B -DskipTests package && cp target/*.jar /app.jar

FROM eclipse-temurin:17-jre-noble
RUN useradd --system --uid 1001 --no-create-home steelsight
COPY --from=build /app.jar /app/app.jar
USER steelsight
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=prod
# Sized for Render's free service: 512 MB and very little CPU.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
