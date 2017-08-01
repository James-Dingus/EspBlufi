package com.esp.iot.blufi.communiation;

public class BlufiUtils {
    public static final String VERSION = "v1.1";

    public static void sleep(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
