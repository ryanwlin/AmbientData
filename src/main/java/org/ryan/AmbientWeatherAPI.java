package org.ryan;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

public class AmbientWeatherAPI {

    private final String applicationKey;
    private final String apiKey;
    private static final Logger logger = LogManager.getLogger(AmbientWeatherAPI.class);
    private final OkHttpClient httpClient;

    // Constructor
    public AmbientWeatherAPI(String appKey, String apiKey) {
        this.applicationKey = appKey;
        this.apiKey = apiKey;
        this.httpClient = buildHttpClient();
        logger.info("Http client built: " + httpClient);
    }

    // Method to get device data
    public String deviceData(String macAddress, int runs) {
        Request request = buildRequest(macAddress);
        return executeRequest(request, macAddress, runs);
    }

    // Build OkHttpClient
    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    // Build the HTTP request
    private Request buildRequest(String macAddress) {
        String url = "https://api.ambientweather.net/v1/devices/" + macAddress
                + "?apiKey=" + apiKey
                + "&applicationKey=" + applicationKey
                + "&limit=1&end_date=1723481785";
        return new Request.Builder().url(url).build();
    }

    // Execute the HTTP request and handle retries
    private String executeRequest(Request request, String macAddress, int runs) {
        try {
            logger.info("Executing new API call request...");
            Response response = httpClient.newCall(request).execute();

            if (response.isSuccessful()) {
                logger.info("Request successful");
                String responseJson = Objects.requireNonNull(response.body()).string();
                logger.info("Station data: " + responseJson);
                return responseJson;
            } else {
                logger.warn("Request not successful: " + response.code() + ", retrying...");
                try {
                    Thread.sleep(10000L * runs);
                    return retryRequest(macAddress, runs);
                } catch (InterruptedException ie) {
                    // Handle the interrupted exception, if necessary
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted. Exiting...");
                    return null;
                }
            }

        } catch (IOException e) {
            logger.warn("IOException occurred: " + e.getMessage());
            return retryRequest(macAddress, runs);
        }
    }

    // Handle retries for the request
    private String retryRequest(String macAddress, int runs) {
        if (runs + 1 >= 4) {
            logger.error("ERROR: Request failed after 3 attempts");
            return "";
        } else {
            return deviceData(macAddress, runs + 1);
        }
    }
}
