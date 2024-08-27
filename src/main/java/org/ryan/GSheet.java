package org.ryan;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.http.HttpRequestInitializer;


import java.io.*;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The GSheet class provides functionality for interacting with Google Sheets API,
 * including reading and writing data, handling errors, and managing weather sensor information.
 * This class is responsible for:
 * - Authenticating and initializing the Google Sheets API service using OAuth2 credentials.
 * - Writing weather data or sensor data to a Google Sheet.
 * - Handling specific exceptions, such as GoogleJsonResponseException, and implementing retry logic.
 * - Managing sensor metadata through an inner class (SensorInfo) and using it to organize data in the spreadsheet.
 * The class uses Log4j for logging various stages of operation and errors, ensuring that
 * the program's activities and any issues encountered are recorded for troubleshooting purposes.
 * Example usage:
 * 1. Initialize the GSheet object and configure it with Google Sheets credentials.
 * 2. Use the writeData method to write sensor data to a specified Google Sheet.
 * 3. The class will handle sheet creation, updates, and error retries as needed.
 *
 * @author Ryan Lin
 * @version 08/27/2024
 */
public class GSheet {
    private final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private final Logger logger = LogManager.getLogger(GSheet.class);
    private final HashMap<String, SensorInfo> sensors = new HashMap<>();
    private final String spreadsheetId = System.getenv("SPREADSHEET_ID");

    // === Authentication and Authorization ===

    /**
     * Initializes the Google Sheets API service with proper credentials and HTTP transport.
     * Handles errors and calls methods for retry logic.
     *
     * @param runs the number of times this method has been attempted, used for retry logic
     * @return a Sheets service object for interacting with Google Sheets API
     */
    public Sheets httpBuilder(int runs) {
        Sheets service = null;
        logger.info("Initializing Google Sheets API service with a new HTTP transport and credentials.");

        // Retrieve the path to the Google application credentials from Kubernetes Secret
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (credentialsPath == null) {
            logger.error("ERROR!: Environment variable GOOGLE_APPLICATION_CREDENTIALS not set.");
        }

        try {
            // Initialize the HTTP transport and Google credentials
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            String APPLICATION_NAME = "AmbientTracker DataBase";
            assert credentialsPath != null;
            GoogleCredentials googleCredentials = GoogleCredentials.
                    fromStream(new FileInputStream(credentialsPath));
            HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(googleCredentials);

            // Build the Sheets service with the initialized HTTP transport and credentials
            service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            logger.info("HTTP transport and credentials successfully built");
        } catch (IOException | GeneralSecurityException e) {
            handleError("IOException or GeneralSecurityException occurred while building HTTP", e, runs, ()
                    -> httpBuilder(runs + 1));
        }
        return service;
    }

    // === Core Methods ===

    /**
     * Writes weather from Ambient Weather API data to the Google Sheet.
     *
     * @param data the weather data as a JSONArray to be written to the sheet
     */
    public void writeData(JSONArray data) {
        // Initialize Sheets service
        Sheets service = httpBuilder(1);
        if (service == null) {
            logger.error("!ERROR!: Sheets service variable is null, returning back to main method.");
            return;
        }

        // Add sensor data to a HashMap if the sensors HashMap is empty
        if (sensors.isEmpty()) {
            logger.info("Initializing sensor data.");
            try {
                sensorHash();
            } catch (IOException e) {
                logger.error("!ERROR!: Unable to read sensor file - headers.txt, HashMap with sensor info not " +
                        "created. Returning to main method.");
                return;
            }
        }

        // Set the range to retrieve data from the column 'A' of the sheet for the current year
        String year = Integer.toString(LocalDateTime.now().getYear());
        final String range = year + "!A:A";

        // Get the response from the sheet
        ValueRange response = getResponse(service, range, year, 1);
        if (response == null) {
            logger.error("!ERROR!: Unable to retrieve sheet due to response being null. Returning to main method.");
            return;
        }
        List<List<Object>> sheetData = response.getValues();

        // Prepare the data to be written to the sheet
        logger.info("Parsing through Weather Data...");
        List<List<Object>> weatherVals = new ArrayList<>();
        List<Object> weatherRow = new ArrayList<>(Collections.nCopies(sensors.size(), null));

        // Iterate over the JSON data and populate the weatherRow with sensor data
        JSONObject obj = data.getJSONObject(0);
        Iterator<String> key = obj.keys();
        while (key.hasNext()) {
            String k = key.next();
            SensorInfo currSensor = sensors.get(k);
            weatherRow.set(stringToNumber(currSensor.getId()), obj.get(k));
        }
        weatherVals.add(weatherRow);

        // Determine the next empty row in the sheet
        int emptyRow = sheetData.size() + 1;
        // Update the sheet with the new weather data
        updateValues(service, year, weatherVals, "!" + "A" + emptyRow, 1);
    }

    /**
     * Retrieves data from the Google Sheet based on the specified range. Calls handleGoogleJsonResponseException method
     * if no such sheet exists or invalid range is provided. Handles errors and calls methods for retry logic.
     *
     * @param service the Sheets service object
     * @param range the range of cells to retrieve
     * @param year the current year, used as the sheet name
     * @param runs the number of retry attempts
     * @return a ValueRange object containing the retrieved data
     */
    private ValueRange getResponse(Sheets service, String range, String year, int runs) {
        logger.info("Getting info on sheet...");
        ValueRange response = null;
        try {
            // Retrieve the data from the specified range
            response = service.spreadsheets().values().get(spreadsheetId, range).execute();
            logger.info("Info successfully retrieved");
        } catch (GoogleJsonResponseException e) {
            // Handle specific GoogleJsonResponseExceptions, such as an invalid range
            response = handleGoogleJsonResponseException(service, range, year, runs, e);
        } catch (IOException e) {
            // Handle IOException and implement retry logic
            handleError("IOException occurred while getting values of GSheet", e, runs, ()
                    -> getResponse(service, range, year, runs + 1));
        }
        return response;
    }

    /**
     * Creates a new sheet for the current year in the Google Spreadsheet.
     *
     * @param service the Sheets service object
     * @param sheetName the name of the new sheet (typically the current year)
     */
    private void createSheet(Sheets service, String sheetName) {
        logger.info("Creating new Sheet for current year.");
        // Create a new sheet with the specified name
        AddSheetRequest addSheetRequest = new AddSheetRequest().setProperties(new SheetProperties()
                .setTitle(sheetName));

        logger.info("Batch Update Request to add new Sheet.");
        // Create a batch update request to add the new sheet
        BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

        // Execute the batch update request
        BatchUpdateSpreadsheetResponse response = updateBatch(service, batchUpdateRequest, 1);

        if (response == null) {
            logger.error("!ERROR!: Batch Update Response returned null, returning back to caller method");
            return;
        }

        logger.info("Getting Sheet ID...");
        // Retrieve the ID of the newly created sheet
        Integer sheetId = response.getReplies().get(0).getAddSheet().getProperties().getSheetId();

        logger.info("Batch Update Request to freeze first row.");
        // Freeze the first row of the new sheet
        GridProperties gridProperties = new GridProperties().setFrozenRowCount(1);
        UpdateSheetPropertiesRequest updateSheetPropertiesRequest = new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId).setGridProperties(gridProperties))
                .setFields("gridProperties.frozenRowCount");

        // Execute the batch update request to apply the freeze
        updateBatch(service, new BatchUpdateSpreadsheetRequest().setRequests(Collections.singletonList(
                        new Request().setUpdateSheetProperties(updateSheetPropertiesRequest))), 0);

        logger.info("New spreadsheet created, Year: " + sheetName);
    }

    /**
     * Executes a batch update request to the Google Spreadsheet. Handles errors and calls methods for retry logic.
     *
     * @param service the Sheets service object
     * @param request the batch update request to be executed
     * @param runs the number of retry attempts
     * @return a BatchUpdateSpreadsheetResponse object containing the response data
     */
    private BatchUpdateSpreadsheetResponse updateBatch(Sheets service, BatchUpdateSpreadsheetRequest request
            , int runs) {
        BatchUpdateSpreadsheetResponse response = null;
        logger.info("New Batch Update Requesting.");
        try {
            // Execute the batch update request
            response = service.spreadsheets().batchUpdate(spreadsheetId, request).execute();
            logger.info("Batch Update Successful");
        } catch (IOException e) {
            // Handle IOException and implement retry logic
            handleError("IOException occurred while Updating with Batch Request", e, runs, ()
                    -> updateBatch(service, request, runs + 1));
        }
        return response;
    }

    /**
     * Updates values in the Google Sheet at the specified range. Handles errors and calls methods for retry logic.
     *
     * @param sheetsService the Sheets service object
     * @param sheetName the name of the sheet to be updated
     * @param values the values to be written to the sheet
     * @param range the range of cells to update
     * @param runs the number of retry attempts
     */
    private void updateValues(Sheets sheetsService, String sheetName, List<List<Object>> values, String range
            , int runs) {
        logger.info("Updating GSheet with new values at range " + range);
        ValueRange body = new ValueRange().setValues(values);
        String addRange = sheetName + range;

        try {
            // Update the sheet with the new values at the given range
            sheetsService.spreadsheets().values().update(spreadsheetId, addRange, body)
                    .setValueInputOption("RAW").execute();
            logger.info("Successfully Updated GSheet");
        } catch (IOException e) {
            // Handle IOException and implement retry logic
            handleError("IOException occurred while updating GSheet with new values", e, runs, ()
                    -> updateValues(sheetsService, sheetName, values, range, runs + 1));
        }
    }

    // === Utility Methods ===

    /**
     * Reads sensor information from the headers.txt file and populates the sensors HashMap.
     *
     * @throws IOException if an error occurs while reading the file
     */
    private void sensorHash() throws IOException {
        logger.info("Reading headers.txt file to create HashMap with sensor info");
        try (BufferedReader br = new BufferedReader(new FileReader
                ("/app/config/headers.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] sensorParts = line.split(",", 3);
                if (sensorParts.length == 3) {
                    sensors.put(sensorParts[0], new SensorInfo(sensorParts[1], sensorParts[2]));
                }
            }
        }
    }

    /**
     * Converts a string of letters into a number corresponding to the column position in the sheet.
     *
     * @param letters the string of letters (e.g., "A", "B", "AA")
     * @return the corresponding column number
     */
    private int stringToNumber(String letters) {
        int result = 0;
        for (int i = 0; i < letters.length(); i++) {
            result *= 26;
            result += letters.charAt(i) - 'A' + 1;
        }
        return result;
    }

    /**
     * Handles GoogleJsonResponseExceptions that occur while interacting with the Google Sheets API.
     * This method attempts to create a new sheet if the error is due to an invalid range.
     *
     * @param service the Sheets service object
     * @param range the range that caused the error
     * @param year the current year, used as the sheet name
     * @param runs the number of retry attempts
     * @param e the GoogleJsonResponseException that was thrown
     * @return a ValueRange object containing the retrieved data or null if the error persists
     */
    private ValueRange handleGoogleJsonResponseException(Sheets service, String range, String year,
                                                                int runs, GoogleJsonResponseException e) {
        // Check if the error is due to an invalid range (e.g., trying to access a non-existent sheet)
        if (e.getStatusCode() == 400 && e.getDetails().getErrors().get(0)
                .getMessage().contains("Unable to parse range")) {
            logger.info("Creating new sheet for current year: " + year);
            // Create a new sheet named after the current year
            createSheet(service, year);
            List<List<Object>> headers = new ArrayList<>();

            // Initialize the first row with sensor descriptions as headers
            List<Object> headerRow = new ArrayList<>(Collections.nCopies(sensors.size() + 1, null));
            for (Map.Entry<String, SensorInfo> headerVal : sensors.entrySet()) {
                SensorInfo sen = headerVal.getValue();
                // Set the sensor description in the appropriate column
                headerRow.set(stringToNumber(sen.getId()), sen.getDescription());
            }
            headers.add(headerRow);
            // Update the new sheet with the header row
            updateValues(service, year, headers, "!A1", 1);
            // Retry the original request to get the data from the sheet
            return getResponse(service, range, year, runs + 1);
        } else {
            // If the maximum number of retries has been reached, log an error and return null
            if (runs + 1 >= 4) {
                logger.error("!ERROR!: GoogleJsonResponseException occurred while getting values of GSheet"
                        + e + " returning back to caller method.");
                return null;
            } else {
                // Log a warning and retry the operation after a delay
                logger.warn("WARNING #" + (runs) + ": GoogleJsonResponseException occurred while getting " +
                        "values of GSheet " + e.getMessage() + " retrying.");
                try {
                    // Introduce a delay before retrying (exponential backoff based on the number of retries)
                    Thread.sleep(10000L * runs);
                    return getResponse(service, range, year, runs + 1);
                } catch (InterruptedException ie) {
                    // If the retry is interrupted, log a warning and exit the retry loop
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted. Exiting...");
                    return null;
                }
            }
        }
    }

    /**
     * Handles errors and implements retry logic for various operations and methods throughout the program. Attempts
     * to retry logic 3 times at varying delays before moving on.
     *
     * @param message the error message to be logged
     * @param e the exception that was thrown
     * @param runs the number of retry attempts
     * @param retry a Runnable representing the operation to retry
     */
    private void handleError(String message, Exception e, int runs, Runnable retry) {
        // If the maximum number of retries has been reached, log an error and stop retrying
        if (runs + 1 >= 4) {
            logger.error("!ERROR!: " + message + ": " + e + " returning back to caller method.");
        } else {
            // Log a warning and retry the operation after a delay
            logger.warn("WARNING #" + (runs) + ": " + message + ": " + e.getMessage() + " retrying.");
            try {
                // Introduce a delay before retrying (exponential backoff based on the number of retries)
                Thread.sleep(10000L * runs);
                retry.run();
            } catch (InterruptedException ie) {
                // If the retry is interrupted, log a warning and exit the retry loop
                Thread.currentThread().interrupt();
                logger.warn("Retry interrupted. Exiting...");
            }
        }
    }

    // === Inner Classes ===

    /**
     * Inner class representing sensor information, including the sensor ID and description.
     * This class is used to store metadata about each sensor, such as its identifier and a brief description.
     */
    static class SensorInfo {
        private final String id; // The unique identifier for the sensor
        private final String description; // Name and description of the sensor

        public SensorInfo(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }
}
