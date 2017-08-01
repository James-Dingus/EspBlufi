package com.espressif.libs.utils;

import java.util.ArrayList;
import java.util.List;

public class DataUtil {

    public static void printBytes(byte[] bytes) {
        printBytes(bytes, 20);
    }

    public static void printBytes(byte[] bytes, int colCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < colCount; i++) {
            if (i < 10) {
                sb.append(0);
            }
            sb.append(i).append('\t');
        }
        sb.append('\n');

        for (int i = 0; i < colCount; i++) {
            sb.append("-\t");
        }
        sb.append('\n');

        for (int i = 0; i < bytes.length; i++) {
            sb.append(Integer.toHexString(bytes[i] & 0xff)).append('\t');
            if (i % colCount == (colCount - 1)) {
                sb.append("| ").append(i / colCount).append('\n');
            }
        }

        System.out.println(sb.toString());
    }

    public static byte[] hexIntStringToBytes(String string) {
        if (string.length() < 2) {
            string = "0" + string;
        }
        byte[] result = new byte[string.length() / 2];
        for (int i = 0; i < string.length(); i += 2) {
            result[i / 2] = (byte) Integer.parseInt(string.substring(i, i + 2), 16);
        }
        return result;
    }

    public static String bytesToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int i = b & 0xff;
            String bstr = Integer.toHexString(i);
            if (bstr.length() < 2) {
                bstr = "0" + bstr;
            }

            sb.append(bstr);
        }
        return sb.toString();
    }

    public static byte[] byteListToArray(List<Byte> list) {
        byte[] result = new byte[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    public static List<Byte> byteArrayToList(byte[] bytes) {
        List<Byte> result = new ArrayList<>();
        for (byte b : bytes) {
            result.add(b);
        }
        return result;
    }

    public static boolean equleBytes(byte[] b1, byte[] b2) {
        if (b1 == null || b2 == null) {
            return false;
        }

        if (b1.length != b2.length) {
            return false;
        }

        for (int i = 0; i < b1.length; i++) {
            if (b1[i] != b2[i]) {
                return false;
            }
        }

        return true;
    }

    private boolean endsWith(byte[] data, byte[] suffix) {
        if (data.length < suffix.length) {
            return false;
        }
        for (int i = 0; i < suffix.length; i++) {
            if (suffix[i] != data[data.length - (suffix.length - i)]) {
                return false;
            }
        }

        return true;
    }

    public static boolean isEmpty(byte[] data) {
        return data == null || data.length == 0;
    }

    public static byte[] mergeBytes(byte[]... bytesArray) {
        int resultLen = 0;
        for (byte[] data : bytesArray) {
            resultLen += data.length;
        }

        byte[] result = new byte[resultLen];
        int offset = 0;
        for (byte[] data : bytesArray) {
            System.arraycopy(data, 0, result, offset, data.length);
            offset += data.length;
        }

        return  result;
    }
}
