package com.esp.iot.blufi.communiation;

import java.io.Serializable;
import java.util.Locale;

public class BlufiConfigureParams implements Serializable {
    private int mOpMode;

    private String mStaBSSID;
    private String mStaSSID;
    private String mStaPassword;
    private boolean mMeshRoot = false;
    private int mWifiChannel = -1;
    private byte[] mMeshID;
    private int mConfigureSequence;

    private int mSoftAPSecurity;
    private String mSoftAPSSID;
    private String mSoftAPPassword;
    private int mSoftAPChannel;
    private int mSoftAPMaxConnection;

    public int getOpMode() {
        return mOpMode;
    }

    public void setOpMode(int mode) {
        mOpMode = mode;
    }

    public String getStaBSSID() {
        return mStaBSSID;
    }

    public void setStaBSSID(String bssid) {
        mStaBSSID = bssid;
    }

    public String getStaSSID() {
        return mStaSSID;
    }

    public void setStaSSID(String ssid) {
        mStaSSID = ssid;
    }

    public String getStaPassword() {
        return mStaPassword;
    }

    public void setStaPassword(String password) {
        mStaPassword = password;
    }

    public boolean isMeshRoot() {
        return mMeshRoot;
    }

    public void setMeshRoot(boolean isRoot) {
        mMeshRoot = isRoot;
    }

    public int getWifiChannel() {
        return mWifiChannel;
    }

    public void setWifiChannel(int channel) {
        mWifiChannel = channel;
    }

    public byte[] getMeshID() {
        return mMeshID;
    }

    public void setMeshID(byte[] id) {
        mMeshID = id;
    }

    public int getConfigureSequence() {
        return mConfigureSequence;
    }

    public void setConfigureSequence(int sequence) {
        mConfigureSequence = sequence;
    }

    public int getSoftAPSecurity() {
        return mSoftAPSecurity;
    }

    public void setSoftAPSecurity(int security) {
        mSoftAPSecurity = security;
    }

    public String getSoftAPSSID() {
        return mSoftAPSSID;
    }

    public void setSoftAPSSID(String ssid) {
        mSoftAPSSID = ssid;
    }

    public String getSoftAPPassword() {
        return mSoftAPPassword;
    }

    public void setSoftAPPAssword(String password) {
        mSoftAPPassword = password;
    }

    public int getSoftAPChannel() {
        return mSoftAPChannel;
    }

    public void setSoftAPChannel(int channel) {
        mSoftAPChannel = channel;
    }

    public int getSoftAPMaxConnection() {
        return mSoftAPMaxConnection;
    }

    public void setSoftAPMaxConnection(int connectionCount) {
        mSoftAPMaxConnection = connectionCount;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,
                "op mode = %d, sta bssid = %s, sta ssid = %s, sta password = %s, softap security = %d," +
                        " softap ssid = %s, softap password = %s, softap channel = %d, softap max connection = %d",
                mOpMode,
                mStaBSSID,
                mStaSSID,
                mStaPassword,
                mSoftAPSecurity,
                mSoftAPSSID,
                mSoftAPPassword,
                mSoftAPChannel,
                mSoftAPMaxConnection);
    }
}
