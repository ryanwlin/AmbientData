package org.ryan;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.*;

public class GSheet {
    private final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private final Logger logger = LogManager.getLogger(GSheet.class);
    private final HashMap<String, SensorInfo> sensors = new HashMap<>();
    private final String spreadsheetId = "1XfM5AjJzs8rEJ9PDDi9N0DEPOqw-P1RYdM4ST8Ga4uM";

    private final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);


    // === Authentication and Authorization ===

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        logger.info("Getting GSheet Credentials.");

        // Use the provided environment variable for credentials
        InputStream in = new FileInputStream(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        logger.info("Loading tokens from data store.");
        String TOKENS_DIRECTORY_PATH = "/app/tokens";
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        // Check if the token is already stored and use it
        Credential credential = flow.loadCredential("user");
        if (credential != null && credential.refreshToken()) {
            return credential;
        }

        throw new IOException("Failed to load existing credentials, or no valid token found.");
    }

    public Sheets httpBuilder(int runs) {
        Sheets service = null;
        logger.info("Initializing Google Sheets API service with a new HTTP transport and credentials.");
        try {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            String APPLICATION_NAME = "AmbientTracker DataBase";
            service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
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

    public void writeData(JSONArray data) {
        Sheets service = httpBuilder(1);
        if (service == null) {
            logger.error("!ERROR!: Sheets service variable is null, returning back to main method.");
            return;
        }

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

        String year = Integer.toString(LocalDateTime.now().getYear());
        final String range = year + "!A:A";

        ValueRange response = getResponse(service, range, year, 1);
        if (response == null) {
            logger.error("!ERROR!: Unable to retrieve sheet due to response being null. Returning to main method.");
            return;
        }
        List<List<Object>> sheetData = response.getValues();

        logger.info("Parsing through Weather Data...");
        List<List<Object>> weatherVals = new ArrayList<>();
        List<Object> weatherRow = new ArrayList<>(Collections.nCopies(sensors.size(), null));

        JSONObject obj = data.getJSONObject(0);
        Iterator<String> key = obj.keys();
        while (key.hasNext()) {
            String k = key.next();
            SensorInfo currSensor = sensors.get(k);
            weatherRow.set(stringToNumber(currSensor.getId()), obj.get(k));
        }
        weatherVals.add(weatherRow);

        int emptyRow = sheetData.size() + 1;
        updateValues(service, year, weatherVals, "!" + "A" + emptyRow, 1);
    }

    private ValueRange getResponse(Sheets service, String range, String year, int runs) {
        logger.info("Getting info on sheet...");
        ValueRange response = null;
        try {
            response = service.spreadsheets().values().get(spreadsheetId, range).execute();
            logger.info("Info successfully retrieved");
        } catch (GoogleJsonResponseException e) {
            response = handleGoogleJsonResponseException(service, range, year, runs, e);
        } catch (IOException e) {
            handleError("IOException occurred while getting values of GSheet", e, runs, ()
                    -> getResponse(service, range, year, runs + 1));
        }
        return response;
    }

    private void createSheet(Sheets service, String sheetName) {
        logger.info("Creating new Sheet for current year.");
        AddSheetRequest addSheetRequest = new AddSheetRequest().setProperties(new SheetProperties()
                .setTitle(sheetName));

        logger.info("Batch Update Request to add new Sheet.");
        BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                .setRequests(Collections.singletonList(new Request().setAddSheet(addSheetRequest)));

        BatchUpdateSpreadsheetResponse response = updateBatch(service, batchUpdateRequest, 1);

        if (response == null) {
            logger.error("!ERROR!: Batch Update Response returned null, returning back to caller method");
            return;
        }

        logger.info("Getting Sheet ID...");
        Integer sheetId = response.getReplies().get(0).getAddSheet().getProperties().getSheetId();

        logger.info("Batch Update Request to freeze first row.");
        GridProperties gridProperties = new GridProperties().setFrozenRowCount(1);
        UpdateSheetPropertiesRequest updateSheetPropertiesRequest = new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId).setGridProperties(gridProperties))
                .setFields("gridProperties.frozenRowCount");

        updateBatch(service, new BatchUpdateSpreadsheetRequest().setRequests(Collections.singletonList(
                        new Request().setUpdateSheetProperties(updateSheetPropertiesRequest))), 0);

        logger.info("New spreadsheet created, Year: " + sheetName);
    }

    private BatchUpdateSpreadsheetResponse updateBatch(Sheets service, BatchUpdateSpreadsheetRequest request
            , int runs) {
        BatchUpdateSpreadsheetResponse response = null;
        logger.info("New Batch Update Requesting.");
        try {
            response = service.spreadsheets().batchUpdate(spreadsheetId, request).execute();
            logger.info("Batch Update Successful");
        } catch (IOException e) {
            handleError("IOException occurred while Updating with Batch Request", e, runs, ()
                    -> updateBatch(service, request, runs + 1));
        }
        return response;
    }

    private void updateValues(Sheets sheetsService, String sheetName, List<List<Object>> values, String range
            , int runs) {
        logger.info("Updating GSheet with new values at range " + range);
        ValueRange body = new ValueRange().setValues(values);
        String addRange = sheetName + range;

        try {
            sheetsService.spreadsheets().values().update(spreadsheetId, addRange, body)
                    .setValueInputOption("RAW").execute();
            logger.info("Successfully Updated GSheet");
        } catch (IOException e) {
            handleError("IOException occurred while updating GSheet with new values", e, runs, ()
                    -> updateValues(sheetsService, sheetName, values, range, runs + 1));
        }
    }

    // === Utility Methods ===

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

    private int stringToNumber(String letters) {
        int result = 0;
        for (int i = 0; i < letters.length(); i++) {
            result *= 26;
            result += letters.charAt(i) - 'A' + 1;
        }
        return result;
    }

    private ValueRange handleGoogleJsonResponseException(Sheets service, String range, String year,
                                                                int runs, GoogleJsonResponseException e) {
        if (e.getStatusCode() == 400 && e.getDetails().getErrors().get(0)
                .getMessage().contains("Unable to parse range")) {
            logger.info("Creating new sheet for current year: " + year);
            createSheet(service, year);
            List<List<Object>> headers = new ArrayList<>();
            List<Object> headerRow = new ArrayList<>(Collections.nCopies(sensors.size() + 1, null));
            for (Map.Entry<String, SensorInfo> headerVal : sensors.entrySet()) {
                SensorInfo sen = headerVal.getValue();
                headerRow.set(stringToNumber(sen.getId()), sen.getDescription());
            }
            headers.add(headerRow);
            updateValues(service, year, headers, "!A1", 1);
            return getResponse(service, range, year, runs);
        } else {
            if (runs + 1 == 4) {
                logger.error("!ERROR!: GoogleJsonResponseException occurred while getting values of GSheet"
                        + e + " returning back to caller method.");
                return null;
            } else {
                logger.warn("WARNING #" + (runs) + ": GoogleJsonResponseException occurred while getting " +
                        "values of GSheet " + e.getMessage() + " retrying.");
                try {
                    Thread.sleep(10000L * runs);
                    return getResponse(service, range, year, runs + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted. Exiting...");
                    return null;
                }
            }
        }
    }

    private void handleError(String message, Exception e, int runs, Runnable retry) {
        if (runs + 1 == 4) {
            logger.error("!ERROR!: " + message + ": " + e + " returning back to caller method.");
        } else {
            logger.warn("WARNING #" + (runs) + ": " + message + ": " + e.getMessage() + " retrying.");
            try {
                Thread.sleep(10000L * runs);
                retry.run();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Retry interrupted. Exiting...");
            }
        }
    }

    // === Inner Classes ===

    static class SensorInfo {
        private final String id;
        private final String description;

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
