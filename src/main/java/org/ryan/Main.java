package org.ryan;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main class that initializes and schedules periodic API calls to the AmbientWeather API.
 * The retrieved data is written to a Google Sheet at regular intervals.
 * This program is designed to run continuously, calling the AmbientWeather API every 5 minutes,
 * and writing the data to Google Sheets at the top of each hour. The program uses the Log4j
 * logging framework to log important information and errors during execution.
 * The API and Application keys, as well as the device MAC address, are retrieved from the
 * environment variables set in the Kubernetes Secrets.
 *
 * @author Ryan Lin
 * @version 08/27/2024
 */
public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String APPLICATION_KEY = System.getenv("APP_KEY");
    private static final String DEVICE_MAC = System.getenv("MAC_ADD");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public static void main(String[] args) {
        logger.info("Program started");

        // Initialize the main instance, API, and Google Sheet
        Main ambient = new Main();
        AmbientWeatherAPI api = ambient.initializeAPI();
        GSheet sheet = new GSheet();
        sheet.httpBuilder(1); // Initialize the Google Sheet HTTP builder

        logger.info("Starting scheduled API calls...");
        // Schedule the API calls at fixed intervals
        ambient.scheduleAPICalls(api, sheet);
    }

    /**
     * Creates a new AmbientWeatherAPI object using the API and Application keys provided by Kubernetes Secrets.
     * This method is used to initialize the API object that will be used to interact with the AmbientWeather API in
     * the program.
     *
     * @return AmbientWeatherAPI Object initialized with the provided API and Application keys
     */
    private AmbientWeatherAPI initializeAPI() {
        logger.info("Creating new API HTTP Access...");
        return new AmbientWeatherAPI(API_KEY, APPLICATION_KEY);
    }

    /**
     * Schedules periodic API calls to the AmbientWeatherAPI and writes data to a Google Sheet.
     *
     * @param api the AmbientWeatherAPI instance used to retrieve data
     * @param sheet the GSheet instance used to write data to Google Sheets
     */
    private void scheduleAPICalls(AmbientWeatherAPI api, GSheet sheet) {
        Runnable apiCall = () -> {
            try {
                logger.info("Calling deviceData method...");

                // Retrieve data from the API as a JSON array
                JSONArray data = new JSONArray(api.deviceData(DEVICE_MAC, 1));
                if (LocalDateTime.now().getMinute() == 0) {
                    sheet.writeData(data);
                }
            } catch (Exception e) {
                logger.error("An error occurred while calling deviceData or writing data to " +
                        "Google Sheets: ", e);
            }
            logger.info("Next API call time is " + calculateNextApiCallTime(LocalDateTime.now(), 5));
        };

        // Calculate the initial delay until the first API call
        long initialDelay = calculateInitialDelay();

        // Schedule the API call task to run at a fixed rate, starting after the initial delay,
        // and repeating every 300,000 milliseconds (5 minutes)
        scheduler.scheduleAtFixedRate(apiCall, (initialDelay), 300000, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculates the initial delay until the first API call.
     *
     * @return the delay in milliseconds until the next API call
     */
    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        int minutes = now.getMinute();

        // Calculate how many minutes to wait until the next 5-minute interval
        int waitMinutes = calculateWaitMinutes(minutes);
        LocalDateTime nextApiCallTime = calculateNextApiCallTime(now, waitMinutes);

        logger.info("Next API call time is " + nextApiCallTime);
        return ChronoUnit.MILLIS.between(now, nextApiCallTime);
    }

    /**
     * Calculates the number of minutes to wait until the next interval of 5 minutes.
     *
     * @param minutes the current time in minutes
     * @return the wait time in minutes until the next interval of 5 minutes
     */
    private int calculateWaitMinutes(int minutes) {
        int waitMinutes = (5 - (minutes % 5)) % 5;
        //If remainder
        return (waitMinutes == 0) ? waitMinutes + 5 : waitMinutes;
    }

    /**
     * Calculates the exact time for the next API call.
     *
     * @param now the current time
     * @param waitMinutes the number of minutes to wait until the next API call
     * @return the LocalDateTime representing the exact time for the next API call,
     *         with seconds and nanoseconds set to zero
     */
    private LocalDateTime calculateNextApiCallTime(LocalDateTime now, int waitMinutes) {
        return now.plus(waitMinutes, ChronoUnit.MINUTES).withSecond(0).withNano(0);
    }

}
