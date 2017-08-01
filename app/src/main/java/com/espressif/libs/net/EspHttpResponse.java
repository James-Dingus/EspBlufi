package com.espressif.libs.net;

import org.json.JSONException;
import org.json.JSONObject;

public class EspHttpResponse {
    private int mCode;
    private String mMessage;
    private byte[] mContent;

    /**
     * Set http code
     *
     * @param code http status
     */
    public void setCode(int code) {
        mCode = code;
    }

    /**
     * Get http code
     *
     * @return http status
     */
    public int getCode() {
        return mCode;
    }


    /**
     * Set http message
     *
     * @param msg http message
     */
    public void setMessage(String msg) {
        mMessage = msg;
    }

    /**
     * Get http message
     *
     * @return http message
     */
    public String getMessage() {
        return mMessage;
    }

    /**
     * Set http content data
     *
     * @param content http content data
     */
    public void setContent(byte[] content) {
        mContent = content;
    }

    /**
     * Get http content data
     *
     * @return http content data
     */
    public byte[] getContent() {
        return mContent;
    }

    /**
     * Get http content string
     *
     * @return http content string
     */
    public String getContentString() {
        if (mContent == null) {
            return null;
        } else {
            return new String(mContent);
        }
    }

    /**
     * Get http content json
     *
     * @return http content json
     * @throws JSONException if content is not json format
     */
    public JSONObject getContentJSON() throws JSONException {
        if (mContent == null) {
            return null;
        } else {
            return new JSONObject(new String(mContent));
        }
    }
}
