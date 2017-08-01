package com.espressif.libs.log;

import android.content.Context;
import android.util.Log;

public class EspLog {
    private static String mTag = null;
    private static Level mLevel = Level.NIL;

    public static void init(Context context, Level level) {
        mTag = context.getPackageName();
        mLevel = level;
    }

    public static void setLevel(Level level) {
        mLevel = level;
    }

    public static void v(String msg) {
        if (mLevel.ordinal() <= Level.V.ordinal()) {
            Log.v(mTag, msg);
        }
    }

    public static void d(String msg) {
        if (mLevel.ordinal() <= Level.D.ordinal()) {
            Log.d(mTag, msg);
        }
    }

    public static void i(String msg) {
        if (mLevel.ordinal() <= Level.I.ordinal()) {
            Log.i(mTag, msg);
        }
    }

    public static void w(String msg) {
        if (mLevel.ordinal() <= Level.W.ordinal()) {
            Log.w(mTag, msg);
        }
    }

    public static void e(String msg) {
        if (mLevel.ordinal() <= Level.E.ordinal()) {
            Log.e(mTag, msg);
        }
    }

    public enum Level {
        V, D, I, W, E, NIL
    }
}
