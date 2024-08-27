FROM openjdk:17-jdk

WORKDIR /ambientracker

COPY target/ambient-weather-java-1.0.jar ambient-weather-java-1.0.jar
COPY src/main/resources/credentials.json /app/credentials.json
COPY tokens/StoredCredential /app/tokens/StoredCredential
COPY /src/main/resources/headers.txt /app/config/headers.txt
RUN mkdir -p /app/tokens

ENV GOOGLE_APPLICATION_CREDENTIALS="/app/credentials.json"

CMD ["java", "-jar", "ambient-weather-java-1.0.jar"]
