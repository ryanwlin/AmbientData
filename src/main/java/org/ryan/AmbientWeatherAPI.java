package org.ryan;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * The AmbientWeatherAPI class provides a way to interact with the Ambient Weather API
 * by making HTTP requests to retrieve data from Ambient Weather stations. This class
 * handles the construction and execution of said API requests, manages retries in case of
 * failures, and logs the process for monitoring and debugging purposes.
 * This class is responsible for:
 * - Authentication: The class uses an application key and API key for authenticating
 *   requests to the Ambient Weather API.
 * - HTTP Requests: It constructs and sends HTTP GET requests to the API endpoint to
 *   fetch data from specific weather devices identified by their MAC addresses.
 * - Retry Logic: If a request fails (e.g., due to network issues or API errors), the
 *   class includes logic to automatically retry the request up to three times before giving up.
 * - Logging: All major actions, including the success and failure of requests, are logged
 *   using Log4j, providing detailed information for troubleshooting and performance monitoring.
 * Example usage:
 * - Instantiate the AmbientWeatherAPI class with the appropriate keys.
 * - Call the `deviceData` method with a device's MAC address to retrieve its data.
 *
 * @author Ryan Lin
 * @version 08/27/2024
 */
public class AmbientWeatherAPI {

    private final String applicationKey;
    private final String apiKey;
    private static final Logger logger = LogManager.getLogger(AmbientWeatherAPI.class);
    private final OkHttpClient httpClient;

    /**
     * Constructs an AmbientWeatherAPI object and initializes the HTTP client.
     *
     * @param appKey the application key for the Ambient Weather API
     * @param apiKey the API key for the Ambient Weather API
     */
    public AmbientWeatherAPI(String appKey, String apiKey) {
        this.applicationKey = appKey;
        this.apiKey = apiKey;
        this.httpClient = buildHttpClient();
        logger.info("Http client built: " + httpClient);
    }

    /**
     * Retrieves data from a specific Ambient Weather Station by using its MAC address.
     *
     * @param macAddress the MAC address of the device
     * @param runs the number of retry attempts made so far
     * @return a JSON string containing the device data
     */
    public String deviceData(String macAddress, int runs) {
        Request request = buildRequest(macAddress);
        return executeRequest(request, macAddress, runs);
    }

    /**
     * Builds and configures the OkHttpClient used for making API calls.
     *
     * @return a configured OkHttpClient instance
     */
    private OkHttpClient buildHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * Constructs an HTTP request for retrieving data from the Ambient Weather API, with the given MAC Address of a
     * Ambient Weather Station.
     *
     * @param macAddress the MAC address of the device
     * @return a Request object representing the HTTP request
     */
    private Request buildRequest(String macAddress) {
        String url = "https://api.ambientweather.net/v1/devices/" + macAddress
                + "?apiKey=" + apiKey
                + "&applicationKey=" + applicationKey
                + "&limit=1&end_date=1723481785";
        return new Request.Builder().url(url).build();
    }

    /**
     * Executes the HTTP request to the Ambient Weather API to get the weather data of a specific Ambient Weather
     * Station and handles retry logic in case of failure. Returns the data as a String that can be turned into
     * a JSONArray.
     *
     * @param request the HTTP request to be executed
     * @param macAddress the MAC address of the device
     * @param runs the number of retry attempts made so far
     * @return a JSON string containing the device data, or null if the request fails
     */
    private String executeRequest(Request request, String macAddress, int runs) {
        try {
            logger.info("Executing new API call request...");
            // Execute the request and get the response
            Response response = httpClient.newCall(request).execute();

            // Check if the response is successful
            if (response.isSuccessful()) {
                logger.info("Request successful");

                // Extract the response body as a JSON string
                String responseJson = Objects.requireNonNull(response.body()).string();
                logger.info("Station data: " + responseJson);
                return responseJson;
            } else {
                // Log a warning and attempt to retry the request if it was not successful
                logger.warn("Request not successful: " + response.code() + ", retrying...");
                try {
                    // Introduce a delay before retrying (exponential backoff based on the number of retries)
                    Thread.sleep(10000L * runs);
                    return retryRequest(macAddress, runs);
                } catch (InterruptedException ie) {
                    // If the retry is interrupted, log a warning and exit the retry loop
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted. Exiting...");
                    return null;
                }
            }

        } catch (IOException e) {
            // Log an IOException and attempt to retry the request
            logger.warn("IOException occurred: " + e.getMessage());
            return retryRequest(macAddress, runs);
        }
    }

    /**
     * Handles the retry logic for the HTTP request in case of failure.
     *
     * @param macAddress the MAC address of the device
     * @param runs the number of retry attempts made so far
     * @return a JSON string containing the device data, or an empty string if all retries fail
     */
    private String retryRequest(String macAddress, int runs) {
        // Check if the maximum number of retries has been reached
        if (runs + 1 >= 4) {
            logger.error("ERROR: Request failed after 3 attempts");
            return "";
        } else {
            // Retry the request by calling deviceData again with an incremented run count
            return deviceData(macAddress, runs + 1);
        }
    }
}
