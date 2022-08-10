package com.compal.utils;

import android.util.Log;

public class LogUtils {
    private static final String APP_TAG = "LibWebRtc";
    private static final boolean DEBUG = false;

    public static void v(String tag, String message) {
        if (DEBUG) {
            Log.v(APP_TAG + " > " + tag, message);
        }
    }

    public static void i(String tag, String message) {
        if (DEBUG) {
            Log.i(APP_TAG + " > " + tag, message);
        }
    }

    public static void d(String tag, String message) {
        if (DEBUG) {
            Log.d(APP_TAG + " > " + tag, message);
        }
    }

    public static void w(String tag, String message) {
        Log.w(APP_TAG + " > " + tag, message);
    }

    public static void w(String tag, String message, Exception e) {
        Log.w(APP_TAG + " > " + tag, message, e);
    }

    public static void e(String tag, String message) {
        Log.e(APP_TAG + " > " + tag, message);
    }

    public static void e(String tag, String message, Exception e) {
        Log.e(APP_TAG + " > " + tag, message, e);
    }
}
