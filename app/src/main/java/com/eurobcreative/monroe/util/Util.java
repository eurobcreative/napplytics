package com.eurobcreative.monroe.util;

import android.content.Context;
import android.net.TrafficStats;

import com.eurobcreative.monroe.Config;

import java.util.Date;

/**
 * Utility class for Speedometer that does not require runtime information
 */
public class Util {
    public static final String HTTP_DESCRIPTOR = "HTTP";

    /**
     * Prepare the internal User-Agent string for use. This requires a
     * {@link Context} to pull the package name and version number for this
     * application.
     */
    public static String prepareUserAgent() {
        return Config.USER_AGENT;
    }

    public static String getTimeStringFromMicrosecond(long microsecond) {
        Date timestamp = new Date(microsecond / 1000);
        return timestamp.toString();
    }

    public static long getCurrentRxTxBytes() {
        int uid = android.os.Process.myUid();
        return TrafficStats.getUidRxBytes(uid) + TrafficStats.getUidTxBytes(uid);
    }
}