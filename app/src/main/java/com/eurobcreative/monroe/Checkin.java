package com.eurobcreative.monroe;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;

import com.eurobcreative.monroe.gcm.GCMManager;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.MeasurementJsonConvertor;
import com.eurobcreative.monroe.util.PhoneUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Handles checkins with the server.
 */
public class Checkin {
    private Context context;
    private volatile HttpCookie authCookie = null;
    private AccountSelector accountSelector = null;
    PhoneUtils phoneUtils;
    String gcm_registraion_id;

    public Checkin(Context context) {
        phoneUtils = PhoneUtils.getPhoneUtils();
        this.context = context;
        this.gcm_registraion_id = "";
    }

    /**
     * Shuts down the checkin thread
     */
    public void shutDown() {
        if (this.accountSelector != null) {
            this.accountSelector.shutDown();
        }
    }

    /**
     * Return a fake authentication cookie for a test server instance
     */
    private HttpCookie getFakeAuthCookie() {
        CookieManager cookie = new CookieManager();
        cookie.getCookieStore().add(null, new HttpCookie("dev_appserver_login", "test@nobody.com:False:185804764220139124118"));
        cookie.getCookieStore().add(null, new HttpCookie(".google.com", "/"));
        return cookie.getCookieStore().getCookies().get(0);
    }

    public List<MeasurementTask> checkin(ResourceCapManager resourceCapManager, GCMManager gcm) throws IOException {
        Logger.i("Checkin.checkin() called");
        boolean checkinSuccess = false;
        gcm_registraion_id = gcm.getRegistrationId();
        try {
            JSONObject status = new JSONObject();
            DeviceInfo info = phoneUtils.getDeviceInfo();
            // TODO(Wenjie): There is duplicated info here, such as device ID.
            status.put("id", info.deviceId);
            status.put("manufacturer", info.manufacturer);
            status.put("model", info.model);
            status.put("os", info.os);
            /**
             * TODO: checkin task don't belongs to any app. So we just fill
             * request_app field with server task key
             */

            DeviceProperty deviceProperty = phoneUtils.getDeviceProperty(Config.CHECKIN_KEY);
            deviceProperty.setRegistrationId(gcm.getRegistrationId());
            Logger.d("Checkin-> GCMManager: " + gcm.getRegistrationId());

            status.put("properties", MeasurementJsonConvertor.encodeToJson(deviceProperty));

            if (PhoneUtils.getPhoneUtils().getNetwork() != PhoneUtils.NETWORK_WIFI) {
                resourceCapManager.updateDataUsage(ResourceCapManager.PHONEUTILCOST);
            }

            Logger.d(status.toString());

            Logger.d("Checkin: " + status.toString());

            String result = serviceRequest("checkin", status.toString());
            Logger.d("Checkin result: " + result);
            if (PhoneUtils.getPhoneUtils().getNetwork() != PhoneUtils.NETWORK_WIFI) {
                resourceCapManager.updateDataUsage(result.length());
            }

            // Parse the result
            Vector<MeasurementTask> schedule = new Vector<>();
            JSONArray jsonArray = new JSONArray(result);


            for (int i = 0; i < jsonArray.length(); i++) {
                Logger.d("Parsing index " + i);
                JSONObject json = jsonArray.optJSONObject(i);
                Logger.d("Value is " + json);
                // checkin task must support
                if (json != null &&
                        MeasurementTask.getMeasurementTypes().contains(json.get("type"))) {
                    try {
                        MeasurementTask task = MeasurementJsonConvertor.makeMeasurementTaskFromJson(json);
                        Logger.i(MeasurementJsonConvertor.toJsonString(task.measurementDesc));

                        schedule.add(task);
                    } catch (IllegalArgumentException e) {
                        Logger.w("Could not create task from JSON: " + e);
                        // Just skip it, and try the next one
                    }
                }
            }
            Logger.i("Checkin complete, got " + schedule.size() + " new tasks");
            checkinSuccess = true;
            return schedule;
        } catch (JSONException e) {
            Logger.e("Got exception during checkin", e);
            throw new IOException("There is exception during checkin()");
        } catch (IOException e) {
            Logger.e("Got exception during checkin", e);
            throw e;
        } finally {
            if (!checkinSuccess) {
                // Failure probably due to authToken expiration. Will authenticate upon next checkin.
                this.accountSelector.setAuthImmediately(true);
                this.authCookie = null;
            }
        }
    }

    /**
     * Read in the results of tasks completed to date from a file, then clear the file.
     *
     * @return The results as a JSONArray, ready for sending to the server.
     */
    private synchronized JSONArray readResultsFromFile() {

        JSONArray results = new JSONArray();
        try {
            Logger.d("Loading results from disk: " + context.getFilesDir());

            FileInputStream inputstream = context.openFileInput("results");
            InputStreamReader streamreader = new InputStreamReader(inputstream);
            BufferedReader bufferedreader = new BufferedReader(streamreader);

            String line;
            int count = 0;
            while ((line = bufferedreader.readLine()) != null) {
                JSONObject jsonTask;
                try {
                    jsonTask = new JSONObject(line);
                    count++;
                    results.put(jsonTask);
                } catch (JSONException e) {
                    Logger.e("", e);
                }
            }
            Logger.i("Got " + count + " results from file");

            bufferedreader.close();
            streamreader.close();
            inputstream.close();

            // delete file once done, to avoid uploading results twice
            context.deleteFile("results");

        } catch (FileNotFoundException e) {
            Logger.e("", e);
        } catch (IOException e) {
            Logger.e("", e);
        }
        return results;
    }

    public void uploadMeasurementResult(Vector<MeasurementResult> finishedTasks, ResourceCapManager resourceCapManager) throws IOException {
        JSONArray resultArray = readResultsFromFile();
        for (MeasurementResult result : finishedTasks) {
            try {
                resultArray.put(MeasurementJsonConvertor.encodeToJson(result));
            } catch (JSONException e1) {
                Logger.e("Error when adding " + result);
            }
        }

        JSONArray chunckedArray = new JSONArray();
        int i = 0;
        for (; i < resultArray.length(); i++) {
            try {
                chunckedArray.put(resultArray.getJSONObject(i));
            } catch (JSONException e) {
                Logger.e("Error when adding index " + i + " to array");
            }

            if ((i + 1) % 100 == 0) {
                Logger.d("uploading " + chunckedArray.length() + " measurements");
                uploadChunkedArray(chunckedArray, resourceCapManager);
                chunckedArray = new JSONArray();
            }
        }
        if (i % 100 != 0) {
            Logger.d("uploading " + chunckedArray.length() + " measurements");
            uploadChunkedArray(chunckedArray, resourceCapManager);
        }
        Logger.i("TaskSchedule.uploadMeasurementResult() complete");
    }

    private void uploadChunkedArray(JSONArray resultArray, ResourceCapManager resourceCapManager) throws IOException {
        Logger.i("uploadChunkedArray uploading: " + resultArray.toString());
        if (PhoneUtils.getPhoneUtils().getNetwork() != PhoneUtils.NETWORK_WIFI) {
            resourceCapManager.updateDataUsage(resultArray.toString().length());
        }
        String response = serviceRequest("postmeasurement", resultArray.toString());
        try {
            JSONObject responseJson = new JSONObject(response);
            if (!responseJson.getBoolean("success")) {
                throw new IOException("Failure posting measurement result");
            }
        } catch (JSONException e) {
            throw new IOException(e.getMessage());
        }
    }

    public String serviceRequest(String url, String jsonString) throws IOException {

        if (this.accountSelector == null) {
            accountSelector = new AccountSelector(context);
        }
        if (!accountSelector.isAnonymous()) {
            synchronized (this) {
                if (authCookie == null) {
                    if (!checkGetCookie()) {
                        throw new IOException("No authCookie yet");
                    }
                }
            }
        }

        String fullurl = (accountSelector.isAnonymous() ? phoneUtils.getAnonymousServerUrl() : phoneUtils.getServerUrl()) + "/" + url;
        Logger.i("Checking in to " + fullurl);
        URL urlObj = new URL(fullurl);

        HttpURLConnection urlConnection = (HttpURLConnection) urlObj.openConnection();
        urlConnection.setRequestMethod("POST");
        urlConnection.setDoOutput(true);
        urlConnection.setRequestProperty("Accept", "application/json");
        urlConnection.setRequestProperty("Content-type", "application/json");
        if (!accountSelector.isAnonymous()) {
            // TODO(mdw): This should not be needed
            urlConnection.setRequestProperty("Cookie", authCookie.getName() + "=" + authCookie.getValue());
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(urlConnection.getOutputStream()));
        writer.write(jsonString);
        writer.flush();
        writer.close();

        StringBuilder response_body = new StringBuilder();

        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                response_body.append(line);
            }
        } finally {
            urlConnection.disconnect();
        }
        Logger.i("Sending request: " + fullurl);
        return response_body.toString();
    }

    /**
     * Initiates the process to get the authentication cookie for the user account.
     * Returns immediately.
     */
    public synchronized void getCookie() {
        if (phoneUtils.isTestingServer(phoneUtils.getServerUrl())) {
            Logger.i("Setting fakeAuthCookie");
            authCookie = getFakeAuthCookie();
            return;
        }
        if (this.accountSelector == null) {
            accountSelector = new AccountSelector(context);
        }

        try {
            // Authenticates if there are no ongoing ones
            if (accountSelector.getCheckinFuture() == null) {
                accountSelector.authenticate();
            }
        } catch (OperationCanceledException e) {
            Logger.e("Unable to get auth cookie", e);
        } catch (AuthenticatorException e) {
            Logger.e("Unable to get auth cookie", e);
        } catch (IOException e) {
            Logger.e("Unable to get auth cookie", e);
        }
    }

    /**
     * Resets the checkin variables in AccountSelector
     */
    public void initializeAccountSelector() {
        accountSelector.resetCheckinFuture();
        accountSelector.setAuthImmediately(false);
    }

    private synchronized boolean checkGetCookie() {
        if (phoneUtils.isTestingServer(phoneUtils.getServerUrl())) {
            authCookie = getFakeAuthCookie();
            return true;
        }
        Future<HttpCookie> getCookieFuture = accountSelector.getCheckinFuture();
        if (getCookieFuture == null) {
            Logger.i("checkGetCookie called too early");
            return false;
        }
        if (getCookieFuture.isDone()) {
            try {
                authCookie = getCookieFuture.get();
                Logger.i("Got authCookie: " + authCookie);
                return true;
            } catch (InterruptedException e) {
                Logger.e("Unable to get auth cookie", e);
                return false;
            } catch (ExecutionException e) {
                Logger.e("Unable to get auth cookie", e);
                return false;
            }
        } else {
            Logger.i("getCookieFuture is not yet finished");
            return false;
        }
    }
}