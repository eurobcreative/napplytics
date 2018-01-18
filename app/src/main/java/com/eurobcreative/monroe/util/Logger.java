package com.eurobcreative.monroe.util;

import android.util.Log;

import com.eurobcreative.monroe.Config;

/**
 * Wrapper for logging operations which can be disabled by setting LOGGING_ENABLED.
 */
public class Logger {
    private final static boolean LOGGING_ENABLED = true;
    private final static String TAG = "Napplytics-" + Config.version;

    public static void d(String msg) {
        if (LOGGING_ENABLED) {
            Log.d(TAG, msg);
        }
    }

    public static void d(String msg, Throwable t) {
        if (LOGGING_ENABLED) {
            Log.d(TAG, msg, t);
        }
    }

    public static void e(String msg) {
        if (LOGGING_ENABLED) {
            Log.e(TAG, msg);
        }
    }

    public static void e(String msg, Throwable t) {
        if (LOGGING_ENABLED) {
            Log.e(TAG, msg, t);
        }
    }

    public static void i(String msg) {
        if (LOGGING_ENABLED) {
            Log.i(TAG, msg);
        }
    }

    public static void i(String msg, Throwable t) {
        if (LOGGING_ENABLED) {
            Log.i(TAG, msg, t);
        }
    }

    public static void w(String msg) {
        if (LOGGING_ENABLED) {
            Log.w(TAG, msg);
        }
    }
}