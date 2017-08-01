package com.espressif.libs.net;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class NetUtil {
    /**
     * @param context Context
     * @return current connected ssid, null is disconnected
     */
    public static String getCurrentConnectSSID(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo connection = wm.getConnectionInfo();
        boolean isWifiConnected = connection != null && connection.getNetworkId() != -1;
        if (isWifiConnected) {
            String ssid = connection.getSSID();
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }

            return ssid;
        } else {
            return null;
        }
    }

    /**
     * @param context Context
     * @return current connected bssid, null is disconnected
     */
    public static String getCurrentConnectBSSID(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo connection = wm.getConnectionInfo();
        boolean isWifiConnected = connection != null && connection.getNetworkId() != -1;
        if (isWifiConnected) {
            return connection.getBSSID();
        } else {
            return null;
        }
    }

    /**
     * @param context Context
     * @return current connected ip address, null is disconnected
     */
    public static String getCurrentConnectIP(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo connection = wm.getConnectionInfo();
        boolean isWifiConnected = connection != null && connection.getNetworkId() != -1;
        if (isWifiConnected) {
            return getIpString(connection.getIpAddress());
        } else {
            return null;
        }
    }

    private static String getIpString(int ip) {
        StringBuilder ipSB = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            ipSB.append((ip >> (i * 8)) & 0xff);
            if (i < 3) {
                ipSB.append('.');
            }
        }

        return ipSB.toString();
    }

    /**
     * @param context Context
     * @return position 0 is ssid, position 1 is bssid, position 2 is ip address, null is disconnected
     */
    public static String[] getCurrentConnectionInfo(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo connection = wm.getConnectionInfo();
        boolean isWifiConnected = connection != null && connection.getNetworkId() != -1;
        if (isWifiConnected) {
            String[] result = new String[3];

            String ssid = connection.getSSID();
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length() - 1);
            }

            result[0] = ssid;
            result[1] = connection.getBSSID();
            result[2] = getIpString(connection.getIpAddress());
            return result;
        } else {
            return null;
        }
    }

    public static boolean isWifiConnected(Context context) {
        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo connection = wm.getConnectionInfo();
        return connection != null && connection.getNetworkId() != -1;
    }

    public static int getWifiChannel(int frequency) {
        int channel = -1;
        switch (frequency) {
            case 2412:
                channel = 1;
                break;
            case 2417:
                channel = 2;
                break;
            case 2422:
                channel = 3;
                break;
            case 2427:
                channel = 4;
                break;
            case 2432:
                channel = 5;
                break;
            case 2437:
                channel = 6;
                break;
            case 2442:
                channel = 7;
                break;
            case 2447:
                channel = 8;
                break;
            case 2452:
                channel = 9;
                break;
            case 2457:
                channel = 10;
                break;
            case 2462:
                channel = 11;
                break;
            case 2467:
                channel = 12;
                break;
            case 2472:
                channel = 13;
                break;
            case 2484:
                channel = 14;
                break;
            case 5745:
                channel = 149;
                break;
            case 5765:
                channel = 153;
                break;
            case 5785:
                channel = 157;
                break;
            case 5805:
                channel = 161;
                break;
            case 5825:
                channel = 165;
                break;
        }
        return channel;
    }
}
