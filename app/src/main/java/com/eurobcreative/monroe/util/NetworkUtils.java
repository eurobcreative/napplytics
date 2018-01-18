package com.eurobcreative.monroe.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.JsonWriter;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class NetworkUtils {
    public static final String CODE_OK = "OK";
    public static final String CODE_ERR = "ERR";
    public static final String CODE_NOT_CONNECTION = "NOT_CONNECTION";
    public static final String JSON_RESPONSE_MESSAGE = "msg";

    private static final String SAVE_DATA_URL = "https://napplytics.eurobcreative.com/saveData";

    //Check if exist connection to network
    public static boolean isNetworkAvailable(Context context) {
        if (context != null) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnectedOrConnecting();
        } else {
            return false;
        }
    }

    public static String convertInputStreamToString(InputStream inputStream) throws IOException {
        StringWriter string_writer = new StringWriter();
        IOUtils.copy(inputStream, string_writer, "UTF-8");
        return string_writer.toString();
    }

    public static String saveData(String android_id, String request_type, String network_type,
                                  String better_option, long throughput, float si_throughput, int rssi,
                                  float si_rssi, int rsrp, float si_rsrp, long timestamp) {
        String result = null;

        URL url = null;
        try {
            url = new URL(SAVE_DATA_URL);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        if (url != null) {
            HttpsURLConnection HttpsURLConnection = null;
            try {
                //Connection
                HttpsURLConnection = (HttpsURLConnection) url.openConnection();
                HttpsURLConnection.setDoOutput(true);
                HttpsURLConnection.setRequestProperty("Content-Type", "application/json;charset=utf-8");
                OutputStream os = new BufferedOutputStream(HttpsURLConnection.getOutputStream());

                JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.setIndent(" ");

                writer.beginObject();
                writer.name("android_id").value(android_id);
                writer.name("request_type").value(request_type);
                writer.name("network_type").value(network_type);
                writer.name("result").value(better_option);
                writer.name("throughput").value(throughput);
                writer.name("si_throughput").value(si_throughput);
                writer.name("rssi").value(rssi);
                writer.name("si_rssi").value(si_rssi);
                writer.name("rsrp").value(rsrp);
                writer.name("si_rsrp").value(si_rsrp);
                writer.name("timestamp").value(timestamp);
                writer.endObject();
                writer.close();

                //Response
                int resCode = HttpsURLConnection.getResponseCode();
                if (resCode == HttpsURLConnection.HTTP_OK) {
                    InputStream inputStream = HttpsURLConnection.getInputStream();
                    JSONObject jsonObject = new JSONObject(convertInputStreamToString(inputStream));
                    result = jsonObject.optString(JSON_RESPONSE_MESSAGE, "");

                } else if (resCode == HttpsURLConnection.HTTP_UNAUTHORIZED){
                    result = CODE_ERR;
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();

            } finally {
                if (HttpsURLConnection != null) {
                    HttpsURLConnection.disconnect();
                }
            }
        }

        return result;
    }
}
