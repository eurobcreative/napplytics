package com.eurobcreative.monroe.util.video.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;

import com.google.android.exoplayer.ExoPlayerLibraryInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;

/**
 * Utility methods for the demo application.
 */
public class DemoUtil {

    public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    public static final String CONTENT_URL_EXTRA = "content_url";
    public static final String CONTENT_TYPE_EXTRA = "content_type";
    public static final String CONTENT_ID_EXTRA = "content_id";

    public static final int TYPE_DASH_VOD = 0;
    public static final int TYPE_BBA = 1;
    public static final int TYPE_PROGRESSIVE = 2;

    public static final boolean EXPOSE_EXPERIMENTAL_FEATURES = false;

    public static String getUserAgent(Context context) {
        String versionName;
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (NameNotFoundException e) {
            versionName = "?";
        }
        return "ExoPlayerDemo/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE + ") " + "ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION;
    }

    public static byte[] executePost(String url, byte[] data, Map<String, String> requestProperties) throws IOException {
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(data != null);
            urlConnection.setDoInput(true);
            if (requestProperties != null) {
                for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                    urlConnection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
                }
            }
            if (data != null) {
                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(data);
                out.close();
            }
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            return convertInputStreamToByteArray(in);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private static byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
        byte[] bytes;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte data[] = new byte[1024];
        int count;
        while ((count = inputStream.read(data)) != -1) {
            bos.write(data, 0, count);
        }
        bos.flush();
        bos.close();
        inputStream.close();
        bytes = bos.toByteArray();
        return bytes;
    }
}