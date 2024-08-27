FROM openjdk:17-jdk

WORKDIR /ambientracker

COPY target/ambient-weather-java-1.0.jar ambient-weather-java-1.0.jar
COPY /src/main/resources/headers.txt /app/config/headers.txt
RUN mkdir -p /app/tokens

CMD ["java", "-jar", "ambient-weather-java-1.0.jar"]
