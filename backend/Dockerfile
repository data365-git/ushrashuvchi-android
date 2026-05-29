FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean shadowJar --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
