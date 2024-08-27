package org.ryan;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);
    private static final String API_KEY = System.getenv("API_KEY");
    private static final String APPLICATION_KEY = System.getenv("APP_KEY");
    private static final String DEVICE_MAC = System.getenv("MAC_ADD");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);


    public static void main(String[] args) {
        logger.info("Program started");

        Main ambient = new Main();
        AmbientWeatherAPI api = ambient.initializeAPI();
        GSheet sheet = new GSheet();
        sheet.httpBuilder(0);

        logger.info("Starting scheduled API calls...");
        ambient.scheduleAPICalls(api, sheet);
    }

    private AmbientWeatherAPI initializeAPI() {
        logger.info("Creating new API HTTP Access...");
        return new AmbientWeatherAPI(API_KEY, APPLICATION_KEY);
    }

    private void scheduleAPICalls(AmbientWeatherAPI api, GSheet sheet) {
        Runnable apiCall = () -> {
            try {
                logger.info("Calling deviceData method...");
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

        long initialDelay = calculateInitialDelay();

        scheduler.scheduleAtFixedRate(apiCall, (initialDelay), 300000, TimeUnit.MILLISECONDS);
    }

    private long calculateInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        int minutes = now.getMinute();

        int waitMinutes = calculateWaitMinutes(minutes);
        LocalDateTime nextApiCallTime = calculateNextApiCallTime(now, waitMinutes);

        logger.info("Next API call time is " + nextApiCallTime);
        return ChronoUnit.MILLIS.between(now, nextApiCallTime);
    }

    private int calculateWaitMinutes(int minutes) {
        int waitMinutes = (5 - (minutes % 5)) % 5;
        return (waitMinutes == 0) ? waitMinutes + 5 : waitMinutes;
    }

    private LocalDateTime calculateNextApiCallTime(LocalDateTime now, int waitMinutes) {
        return now.plus(waitMinutes, ChronoUnit.MINUTES).withSecond(0).withNano(0);
    }

}
