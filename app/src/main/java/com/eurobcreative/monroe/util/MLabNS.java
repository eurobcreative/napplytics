package com.eurobcreative.monroe.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.ArrayList;

public class MLabNS {

    /**
     * Query MLab-NS to get an FQDN/IP for the given tool and address family.
     *
     * @param field: fqdn or ip
     */
    static public ArrayList<String> Lookup(String tool, String address_family, String field) {
        // Set the timeout in milliseconds until a connection is established.
        final int timeoutConnection = 5000;

        InputStream inputStream = null;

        // Sanitize for possible returned field
        if (field != "fqdn" && field != "ip") {
            return null;
        }

        try {
            Logger.d("Creating request GET for mlab-ns");
            String url = "http://mlab-ns.appspot.com/" + tool + "?format=json";
            if (address_family == "ipv4" || address_family == "ipv6") {
                url += "&address_family=" + address_family;
            }
            URL urlObj = new URL(url);

            HttpURLConnection urlConnection = (HttpURLConnection) urlObj.openConnection();
            urlConnection.setConnectTimeout(timeoutConnection);
            urlConnection.setRequestProperty("User-Agent", Util.prepareUserAgent());
            urlConnection.setRequestMethod("GET");

            if (urlConnection.getResponseCode() != 200) {
                throw new InvalidParameterException("Received status " + urlConnection.getResponseCode() + " from mlab-ns");
            }
            Logger.d("STATUS OK");

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
            String body_str = response_body.toString();

            JSONObject json = new JSONObject(body_str);
            Logger.d("Field Type is " + json.get(field).getClass().getName());
            ArrayList<String> mlabNSResult = new ArrayList<>();
            if (json.get(field) instanceof JSONArray) {
                // Convert array value into ArrayList
                JSONArray jsonArray = (JSONArray) json.get(field);
                for (int i = 0; i < jsonArray.length(); i++) {
                    mlabNSResult.add(jsonArray.get(i).toString());
                }
            } else if (json.get(field) instanceof String) {
                // Append the string into ArrayList
                mlabNSResult.add(String.valueOf(json.getString(field)));
            } else {
                throw new InvalidParameterException("Unknown type " + json.get(field).getClass().toString() + " of value " + json.get(field));
            }
            return mlabNSResult;
        } catch (SocketTimeoutException e) {
            Logger.e("SocketTimeoutException trying to contact m-lab-ns");
            // e.getMessage() is null
            throw new InvalidParameterException("Connect to m-lab-ns timeout. " +
                    "Please try again.");
        } catch (IOException e) {
            Logger.e("IOException trying to contact m-lab-ns: " + e.getMessage());
            throw new InvalidParameterException(e.getMessage());
        } catch (JSONException e) {
            Logger.e("JSONException trying to contact m-lab-ns: " + e.getMessage());
            throw new InvalidParameterException(e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Logger.e("Failed to close the input stream from the HTTP response");
                }
            }
        }
    }
}