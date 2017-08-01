package com.espressif.libs.net;

import com.espressif.libs.log.EspLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class EspHttpUtils {
    public static final String HEADER_RESPONSE = "Require-Response";
    public static final String HEADER_TIMEOUT_CONN = "Timeout-Conn";
    public static final String HEADER_TIMEOUT_SO = "Timeout-SO";
    public static final String HEADER_CONNECTION = "Connection";

    public static final EspHttpHeader H_KEEP_ALIVE = new EspHttpHeader(HEADER_CONNECTION, "Keep-Alive");
    public static final EspHttpHeader H_NON_RESPONSE = new EspHttpHeader(HEADER_RESPONSE, "false");
    public static final EspHttpHeader H_MESH_NON_RESP = new EspHttpHeader(HEADER_RESPONSE, "false");

    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String METHOD_PUT = "PUT";
    private static final String METHOD_DELETE = "DELETE";

    private static final int PORT_HTTP = 80;
    private static final int PORT_HTTPS = 443;

    private static final int TIMEOUT_CONNECT = 5000;
    private static final int TIMEOUT_SO_GET = 5000;
    private static final int TIMEOUT_SO_POST = 5000;

    static {
        H_MESH_NON_RESP.setMesh(true);
    }

    /**
     * Execute Http Get request.
     *
     * @param url     target url
     * @param headers http headers
     * @return response. null is failed.
     */
    public static EspHttpResponse Get(String url, EspHttpHeader... headers) {
        return execureRequest(createURLConnection(url, METHOD_GET, headers), null);
    }

    /**
     * Execute Http Post request.
     *
     * @param url     target url
     * @param content content bytes
     * @param headers http headers
     * @return response. null is failed.
     */
    public static EspHttpResponse Post(String url, byte[] content, EspHttpHeader... headers) {
        return execureRequest(createURLConnection(url, METHOD_POST, headers), content);
    }

    /**
     * Execute Http Put request.
     *
     * @param url     target url
     * @param content content bytes
     * @param headers http headers
     * @return response. null is failed.
     */
    public static EspHttpResponse Put(String url, byte[] content, EspHttpHeader... headers) {
        return execureRequest(createURLConnection(url, METHOD_PUT, headers), content);
    }

    /**
     * Execute Http Delete request.
     *
     * @param url     target url
     * @param content content bytes
     * @param headers http headers
     * @return response. null is failed.
     */
    public static EspHttpResponse Delete(String url, byte[] content, EspHttpHeader... headers) {
        return execureRequest(createURLConnection(url, METHOD_DELETE, headers), content);
    }

    private static HttpURLConnection createURLConnection(String url, String method, EspHttpHeader... headers) {
        try {
            URL originURL = new URL(url);
            String protocol = originURL.getProtocol();
            String host = originURL.getHost();
            int port = originURL.getPort();
            if (port < 0) {
                port = protocol.toLowerCase(Locale.ENGLISH).equals("https") ?
                        PORT_HTTPS : PORT_HTTP;
            }
            String file = originURL.getFile();

            URL targetURL = new URL(protocol, host, port, file);
            HttpURLConnection connection = (HttpURLConnection) targetURL.openConnection();
            connection.setRequestMethod(method);
            int timeoutConn = -1;
            int timeoutSO = -1;
            for (EspHttpHeader head : headers) {
                if (head == null) {
                    continue;
                }
                if (head.isMesh()) {
                    continue;
                }

                try {
                    if (head.getName().equals(HEADER_TIMEOUT_CONN)) {
                        timeoutConn = Integer.parseInt(head.getValue());
                        continue;
                    } else if (head.getName().equals(HEADER_TIMEOUT_SO)) {
                        timeoutSO = Integer.parseInt(head.getValue());
                        continue;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }

                connection.addRequestProperty(head.getName(), head.getValue());
            }
            String connValue = connection.getRequestProperty(HEADER_CONNECTION);
            if (connValue == null) {
                connection.addRequestProperty(HEADER_CONNECTION, "close");
            }

            if (timeoutConn < 0) {
                timeoutConn = TIMEOUT_CONNECT;
            }
            connection.setConnectTimeout(timeoutConn);
            if (timeoutSO < 0) {
                timeoutSO = method.equals(METHOD_GET) ? TIMEOUT_SO_GET : TIMEOUT_SO_POST;
            }
            connection.setReadTimeout(timeoutSO);

            return connection;
        } catch (IOException e) {
            e.printStackTrace();

            return null;
        }
    }

    private static EspHttpResponse execureRequest(HttpURLConnection connection, byte[] content) {
        if (connection == null) {
            return null;
        }

        String hResp = connection.getRequestProperty(HEADER_RESPONSE);
        boolean requireResponse = hResp == null || Boolean.parseBoolean(hResp);
        if (!requireResponse) {
            connection.setReadTimeout(1);
        }

        try {
            if (!isEmpty(content)) {
                connection.setDoOutput(true);
            }

            if (!isEmpty(content)) {
                EspLog.i("EspHttpUtils post " + new String(content));
                connection.getOutputStream().write(content);
            } else {
                connection.connect();
            }

            EspHttpResponse response = new EspHttpResponse();
            if (requireResponse) {
                int code = connection.getResponseCode();
                String msg = connection.getResponseMessage();
                response.setCode(code);
                response.setMessage(msg);

                int respConcentLen = connection.getContentLength();
                byte[] contentData = new byte[respConcentLen];
                readResponseContent(connection.getInputStream(), contentData);
                response.setContent(contentData);
                EspLog.i("EspHttpUtils response = " + response.getContentString());
            }
            try {
                connection.getInputStream().close();
            } catch (IOException ioe) {
                EspLog.w("EspHttpUtils close InputStream: " + ioe.getMessage());
            }

            return response;
        } catch (IOException e) {
            EspLog.w("EspHttpUtils over io exception: " + e.getMessage());
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private static void readResponseContent(InputStream is, byte[] data) throws IOException {
        int i;
        int offset = 0;
        while ((i = is.read()) != -1) {
            data[offset++] = (byte) i;
        }
    }

    private static boolean isEmpty(byte[] data) {
        return data == null || data.length == 0;
    }

    private static String unescapeHtml(String str) {
        return str.replace("&quot;", "\\\"");
    }
}
