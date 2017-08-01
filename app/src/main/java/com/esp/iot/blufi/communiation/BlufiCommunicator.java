package com.esp.iot.blufi.communiation;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.text.TextUtils;

import com.esp.iot.blufi.communiation.response.BlufiSecurityResult;
import com.esp.iot.blufi.communiation.response.BlufiStatusResponse;
import com.esp.iot.blufi.communiation.response.BlufiVersionResponse;
import com.espressif.libs.ble.EspBleHelper;
import com.espressif.libs.log.EspLog;
import com.espressif.libs.security.EspAES;
import com.espressif.libs.security.EspCRC;
import com.espressif.libs.security.EspDH;
import com.espressif.libs.security.EspMD5;
import com.espressif.libs.utils.DataUtil;
import com.espressif.libs.utils.RandomUtil;

import java.math.BigInteger;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.crypto.interfaces.DHPublicKey;

public class BlufiCommunicator implements IBlufiCommunicator {
    private static final long TIMEOUT_READ = 5000L;

    private static final int DEFAULT_PACKAGE_LENGTH = 80;
    private static final int PACKAGE_HEADER_LENGTH = 4;
    private static final int MIN_PACKAGE_LENGTH = 6;

    private static final int DIRECTION_OUTPUT = 0;
    private static final int DIRECTION_INPUT = 1;

    private static final byte[] AES_BASE_IV = {
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
    };

    private final EspBleHelper mBleHelper;
    private final BluetoothGattCharacteristic mWriteChara;
    private final BluetoothGattCharacteristic mNotifyChara;

    private int mSendSequence = 0;
    private int mReadSequence = -1;

    private byte[] mSecretKeyMD5;

    private boolean mEncrypted = false;
    private boolean mChecksum = false;

    private boolean mRequireAck = false;

    private BlufiNotiData mNotiData;
    private LinkedBlockingQueue<BlufiNotiData> mNotiQueue = new LinkedBlockingQueue<>();

    private int mPackageLengthLimit;

    public BlufiCommunicator(EspBleHelper gatt, BluetoothGattCharacteristic write, BluetoothGattCharacteristic notify) {
        mBleHelper = gatt;
        mWriteChara = write;
        mNotifyChara = notify;
        mPackageLengthLimit = DEFAULT_PACKAGE_LENGTH;
    }

    private static int getTypeValue(int type, int subtype) {
        return (subtype << 2) | type;
    }

    private static int getPackageType(int typeValue) {
        return typeValue & 0x3;
    }

    private static int getSubType(int typeValue) {
        return ((typeValue & 0xfc) >> 2);
    }

    private static int getFrameCTRLValue(boolean encrypted, boolean checksum, int direction, boolean requireAck, boolean frag) {
        int frame = 0;
        if (encrypted) {
            frame = frame | (1 << FRAME_CTRL_POSITION_ENCRYPTED);
        }
        if (checksum) {
            frame = frame | (1 << FRAME_CTRL_POSITION_CHECKSUM);
        }
        if (direction == DIRECTION_INPUT) {
            frame = frame | (1 << FRAME_CTRL_POSITION_DATA_DIRECTION);
        }
        if (requireAck) {
            frame = frame | (1 << FRAME_CTRL_POSITION_REQUIRE_ACK);
        }
        if (frag) {
            frame = frame | (1 << FRAME_CTRL_POSITION_FRAG);
        }

        return frame;
    }

    private int toInt(byte b) {
        return b & 0xff;
    }

    private byte[] getSizeBytesLH(int size) {
        byte len1 = (byte) (size & 0xff);
        byte len2 = (byte) ((size >> 8) & 0xff);
        return new byte[]{len1, len2};
    }

    private byte[] getSizeBytesHL(int size) {
        byte len1 = (byte) ((size >> 8) & 0xff);
        byte len2 = (byte) (size & 0xff);
        return new byte[]{len1, len2};
    }

    private int generateSendSequence() {
        return mSendSequence++;
    }

    public int getSendSequence() {
        return mSendSequence;
    }

    public int getReadSequence() {
        return mReadSequence;
    }

    public void setPostPackageLengthLimit(int lengthLimit) {
        mPackageLengthLimit = lengthLimit;
        if (mPackageLengthLimit < MIN_PACKAGE_LENGTH) {
            mPackageLengthLimit = MIN_PACKAGE_LENGTH;
        }
    }

    public void setRequireAck(boolean requireAck) {
        mRequireAck = requireAck;
    }

    private byte[] generateAESIV(int sequence) {
        byte[] result = new byte[16];
        for (int i = 0; i < result.length; i++) {
            if (i == 0) {
                result[0] = (byte) sequence;
            } else {
                result[i] = AES_BASE_IV[i];
            }
        }

        return result;
    }

    private void notifyNotification(byte[] data) {
        if (mNotiData == null) {
            mNotiData = new BlufiNotiData();
        }
        boolean complete = parseNotification(data, mNotiData);
        if (complete) {
            mNotiQueue.add(mNotiData);
            mNotiData = null;
        }
    }

    private EspBleHelper.GattCallback mNotificationCallback = new EspBleHelper.GattCallback() {
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic == mNotifyChara) {
                notifyNotification(characteristic.getValue());
            }
        }
    };

    private void registerNotification() {
        mBleHelper.registerGattCallback(mNotificationCallback);
        mBleHelper.setCharacteristicNotification(mNotifyChara, true);
    }

    private void unregisterNotification() {
        mBleHelper.unregisterGattCallback(mNotificationCallback);
        mBleHelper.setCharacteristicNotification(mNotifyChara, false);
        mNotiQueue.clear();
    }

    public boolean cancelSecurity() {
        return false;
    }

    /**
     * Negotiate and set communication security
     *
     * @return Set security successfully or failed
     */
    public BlufiSecurityResult negotiateSecurity() {
        BlufiSecurityResult result = BlufiSecurityResult.SUCCESS;

        registerNotification();

        // Post P, G, Public Value
        EspDH dhm = postNegotiateSecurity();
        if (dhm == null) {
            EspLog.w("negotiateSecurity postNegotiateSecurity failed");
            result =  BlufiSecurityResult.POST_PGK_FAILED;
        }

        // Post P, G, PV success, receive device public value
        if (result == BlufiSecurityResult.SUCCESS) {
            boolean readNeg = receiveNegotiateSecurity(dhm);
            EspLog.d("negotiateSecurity readNeg " + readNeg);
            if (readNeg) {
                mEncrypted = true;
                mChecksum = true;
            } else {
                mEncrypted = false;
                mChecksum = false;
                result =  BlufiSecurityResult.RECV_PV_FAILED;
            }
        }

        // Receive device PV success, post security mode
        if (result == BlufiSecurityResult.SUCCESS) {
            if (!postSetSecurity(false, false, mEncrypted, mChecksum)) {
                EspLog.w("negotiateSecurity postSetSecurity failed");
                result = BlufiSecurityResult.POST_SET_MODE_FAILED;
            }
        }

        // Post security mode success, check security result
        if (result == BlufiSecurityResult.SUCCESS) {
            BlufiUtils.sleep(10L);
            if (!checkNegSec()) {
                EspLog.w("negotiateSecurity check failed");
                result = BlufiSecurityResult.CHECK_FAILED;
            }
        }

        unregisterNotification();

        return result;
    }

    /**
     * Generate p, g and public key, post to device
     *
     * @return null is generate data failed
     */
    private EspDH postNegotiateSecurity() {
        int type = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_NEG);

        final int radix = 16;
        final int dhLength = 1024;
        EspDH dhm;
        String p;
        String g;
        String k;
        do {
            dhm = new EspDH(dhLength);
            p = dhm.getP().toString(radix);
            g = dhm.getG().toString(radix);
            k = getPublicValue(dhm);
        } while (k == null);

        byte[] pBytes = DataUtil.hexIntStringToBytes(p);
        byte[] gBytes = DataUtil.hexIntStringToBytes(g);
        byte[] kBytes = DataUtil.hexIntStringToBytes(k);

        LinkedList<Byte> dataList = new LinkedList<>();

        int pgkLength = pBytes.length + gBytes.length + kBytes.length + 6;
        int pgkLen1 = (pgkLength >> 8) & 0xff;
        int pgkLen2 = pgkLength & 0xff;
        dataList.add(NEG_SET_SEC_TOTLE_LEN);
        dataList.add((byte) pgkLen1);
        dataList.add((byte) pgkLen2);
        if (!post(false, false, mRequireAck, type, dataList)) {
            return null;
        }

        BlufiUtils.sleep(10);

        dataList.clear();
        dataList.add(NEG_SET_SEC_ALL_DATA);

        int pLength = pBytes.length;
        int pLen1 = (pLength >> 8) & 0xff;
        int pLen2 = pLength & 0xff;
        dataList.add((byte) pLen1);
        dataList.add((byte) pLen2);
        for (byte b : pBytes) {
            dataList.add(b);
        }

        int gLength = gBytes.length;
        int gLen1 = (gLength >> 8) & 0xff;
        int gLen2 = gLength & 0xff;
        dataList.add((byte) gLen1);
        dataList.add((byte) gLen2);
        for (byte b : gBytes) {
            dataList.add(b);
        }

        int kLength = kBytes.length;
        int kLen1 = (kLength >> 8) & 0xff;
        int kLen2 = kLength & 0xff;
        dataList.add((byte) kLen1);
        dataList.add((byte) kLen2);
        for (byte b : kBytes) {
            dataList.add(b);
        }

        if (!post(false, false, mRequireAck, type, dataList)) {
            return null;
        }

        return dhm;
    }

    /**
     * Get public value
     *
     * @param dhm BlufiDHM
     * @return PublicKey value
     */
    private String getPublicValue(EspDH dhm) {
        DHPublicKey publicKey = dhm.getPublicKey();
        if (publicKey != null) {
            BigInteger y = publicKey.getY();
            String yStr = y.toString(16);
            while (yStr.length() < 256) {
                yStr = "0" + yStr;
            }
            return yStr;
        }

        return null;
    }

    /**
     * Receive negotiate response and parse secret key
     *
     * @param dhm BlufiDHM
     * @return generate secret key successfully or failed
     */
    private boolean receiveNegotiateSecurity(EspDH dhm) {
        BlufiNotiData receiveData = receive();
        if (receiveData != null) {
            String receiveStr = DataUtil.bytesToString(receiveData.getDataArray());
            BigInteger devicePublicValue = new BigInteger(receiveStr, 16);

            try {
                dhm.generateSecretKey(devicePublicValue);
                if (dhm.getSecretKey() == null) {
                    return false;
                }
                mSecretKeyMD5 = EspMD5.getMD5Byte(dhm.getSecretKey());

                return mSecretKeyMD5 != null;
            } catch (InvalidKeySpecException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Post device to set security
     *
     * @param ctrlEncrypted whether Ctrl part encryted
     * @param ctrlChecksum  whether Ctrl part require checksum
     * @param dataEncrypted whether Data part encryted
     * @param dataChecksum  whether Data part require checksum
     */
    private boolean postSetSecurity(boolean ctrlEncrypted, boolean ctrlChecksum, boolean dataEncrypted, boolean dataChecksum) {
        int type = getTypeValue(Type.Ctrl.PACKAGE_VALUE, Type.Ctrl.SUBTYPE_SET_SEC_MODE);
        int data = 0;
        if (dataChecksum) {
            data = data | 1;
        }
        if (dataEncrypted) {
            data = data | (1 << 1);
        }
        if (ctrlChecksum) {
            data = data | (1 << 4);
        }
        if (ctrlEncrypted) {
            data = data | (1 << 5);
        }

        byte[] postData = {(byte) data};

        return post(false, true, mRequireAck, type, postData);
    }

    private boolean checkNegSec() {
        boolean result;
        String checkString = RandomUtil.randomString(4);
        EspLog.i("negotiateSecurity check string = " + checkString);
        int checkValue = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_NEG);
        byte[] stringData = checkString.getBytes();
        byte[] checkData = new byte[stringData.length + 1];
        checkData[0] = NEG_CHECK_NEG_SEC;
        System.arraycopy(stringData, 0, checkData, 1, stringData.length);
        if (post(mEncrypted, mChecksum, false, checkValue, checkData)) {
            String checkResp = receiveCheckNegSecResponse();
            EspLog.i("negotiateSecurity check response = " + checkResp);
            result = checkString.equals(checkResp);
        } else {
            EspLog.w("negotiateSecurity post check neg sec failed");
            result = false;
        }

        return result;
    }

    private String receiveCheckNegSecResponse() {
        BlufiNotiData receiveData = receive();
        if (receiveData == null) {
            return null;
        }

        if (receiveData.getPkgType() != Type.Data.PACKAGE_VALUE) {
            return null;
        }

        if (receiveData.getSubType() != Type.Data.SUBTYPE_NEG) {
            return null;
        }

        byte[] data = receiveData.getDataArray();
        if (data.length == 0) {
            return null;
        }

        if (data[0] != NEG_CHECK_NEG_SEC) {
            return null;
        }

        byte[] strData = new byte[data.length - 1];
        System.arraycopy(data, 1, strData, 0, strData.length);
        return new String(strData);
    }


    public BlufiVersionResponse getVersion() {
        registerNotification();

        int type = getTypeValue(Type.Ctrl.PACKAGE_VALUE, Type.Ctrl.SUBTYPE_GET_VERSION);
        post(mEncrypted, mChecksum, false, type, (byte[]) null);

        BlufiVersionResponse result = receiveVersion();

        unregisterNotification();

        return result;
    }

    private BlufiVersionResponse receiveVersion() {
        BlufiVersionResponse result = new BlufiVersionResponse();

        BlufiNotiData response = receive();
        if (response == null) {
            result.setResultCode(BlufiVersionResponse.RESULT_GET_VERSION_FAILED);
            return result;
        }

        if (response.getPkgType() != Type.Data.PACKAGE_VALUE) {
            result.setResultCode(BlufiVersionResponse.RESULT_GET_VERSION_FAILED);
            return result;
        }

        if (response.getSubType() != Type.Data.SUBTYPE_VERSION) {
            result.setResultCode(BlufiVersionResponse.RESULT_GET_VERSION_FAILED);
            return result;
        }

        byte[] versionBytes = response.getDataArray();
        if (versionBytes.length != 2) {
            result.setResultCode(BlufiVersionResponse.RESULT_GET_VERSION_FAILED);
            return result;
        }

        int[] supportVersion = BlufiProtocol.SUPPORT_PROTOCOL_VERSION;
        int[] deviceVersion = {toInt(versionBytes[0]), toInt(versionBytes[1])};
        int supportValue = (supportVersion[0] << 8) & supportVersion[1];
        int deviceValue = (deviceVersion[0] << 8) & deviceVersion[1];
        if (supportValue >= deviceValue) {
            result.setResultCode(BlufiVersionResponse.RESULT_VALID);
            result.setVersionValues(deviceVersion[0], deviceVersion[1]);
        } else {
            result.setResultCode(BlufiVersionResponse.RESULT_APP_VERSION_INVALID);
        }
        return result;
    }

    /**
     * Get device current status
     *
     * @return result string
     */
    public BlufiStatusResponse getStatus() {
        registerNotification();

        int type = getTypeValue(Type.Ctrl.PACKAGE_VALUE, Type.Ctrl.SUBTYPE_GET_WIFI_STATUS);
        post(mEncrypted, mChecksum, false, type, (byte[]) null);
        BlufiUtils.sleep(10);

        BlufiStatusResponse result = new BlufiStatusResponse();
        receiveWifiState(result);

        unregisterNotification();

        return result;
    }

    /**
     * Receive wifi state information and set data in response
     *
     * @param response store the received data
     * @return receive wifi state successfully or not
     */
    private boolean receiveWifiState(BlufiStatusResponse response) {
        BlufiNotiData stateData = receive();
        if (stateData != null) {
            return parseWifiState(response, stateData);
        } else {
            response.setResultCode(BlufiStatusResponse.RESULT_TIMEOUT);
            return false;
        }
    }

    /**
     * Parse wifi notifaction data and set data in response
     *
     * @param response  store the parsed data
     * @param stateData notification data
     * @return parse wifi state successfully or not
     */
    private boolean parseWifiState(BlufiStatusResponse response, BlufiNotiData stateData) {
        int pkgType = stateData.getPkgType();
        if (pkgType != Type.Data.PACKAGE_VALUE) {
            response.setResultCode(BlufiStatusResponse.RESULT_PARSE_FAILED);
            return false;
        }

        int subType = stateData.getSubType();
        if (subType != Type.Data.SUBTYPE_WIFI_CONNECTION_STATE) {
            response.setResultCode(BlufiStatusResponse.RESULT_PARSE_FAILED);
            return false;
        }

        byte[] dataArray = stateData.getDataArray();
        if (dataArray.length < 3) {
            response.setResultCode(BlufiStatusResponse.RESULT_PARSE_FAILED);
            return false;
        }

        LinkedList<Byte> dataList = new LinkedList<>();
        for (byte b : dataArray) {
            dataList.add(b);
        }

        int opMode = toInt(dataList.poll());
        response.setOpMode(opMode);
        switch (opMode) {
            case OP_MODE_NULL:
                break;
            case OP_MODE_STA:
                break;
            case OP_MODE_SOFTAP:
                break;
            case OP_MODE_STASOFTAP:
                break;
        }

        int staConn = toInt(dataList.poll());
        response.setStaConnectionStatus(staConn);

        int softAPConn = toInt(dataList.poll());
        response.setSoftAPConnectionCount(softAPConn);

        while (!dataList.isEmpty()) {
            int subtype = toInt(dataList.poll());
            int len = toInt(dataList.poll());
            byte[] stateBytes = new byte[len];
            for (int i = 0; i < len; i++) {
                stateBytes[i] = dataList.poll();
            }

            parseWifiStateData(response, subtype, stateBytes);
        }

        response.setResultCode(BlufiStatusResponse.RESULT_SUCCESS);
        return true;
    }

    private String parseWifiStateData(BlufiStatusResponse response, int subType, byte[] data) {
        switch (subType) {
            case Type.Data.SUBTYPE_SOFTAP_AUTH_MODE:
                int authMode = toInt(data[0]);
                response.setSoftAPSecrity(authMode);
                return "SUBTYPE_SOFTAP_AUTH_MODE " + authMode;
            case Type.Data.SUBTYPE_SOFTAP_CHANNEL:
                int softAPChannel = toInt(data[0]);
                response.setSoftAPChannel(softAPChannel);
                return "SUBTYPE_SOFTAP_CHANNEL " + softAPChannel;
            case Type.Data.SUBTYPE_SOFTAP_MAX_CONNECTION_COUNT:
                int softAPMaxConnCount = toInt(data[0]);
                response.setSoftAPMaxConnectionCount(softAPMaxConnCount);
                return "SUBTYPE_SOFTAP_MAX_CONNECTION_COUNT " + softAPMaxConnCount;
            case Type.Data.SUBTYPE_SOFTAP_WIFI_PASSWORD:
                String softapPassword = new String(data);
                response.setSoftAPPassword(softapPassword);
                return "SUBTYPE_SOFTAP_WIFI_PASSWORD " + softapPassword;
            case Type.Data.SUBTYPE_SOFTAP_WIFI_SSID:
                String softapSSID = new String(data);
                response.setSoftAPSSID(softapSSID);
                return "SUBTYPE_SOFTAP_WIFI_SSID " + softapSSID;
            case Type.Data.SUBTYPE_STA_WIFI_BSSID:
                String staBssid = DataUtil.bytesToString(data);
                response.setStaBSSID(staBssid);
                return "SUBTYPE_STA_WIFI_BSSID " + staBssid;
            case Type.Data.SUBTYPE_STA_WIFI_SSID:
                String staSsid = new String(data);
                response.setStaSSID(staSsid);
                return "SUBTYPE_STA_WIFI_SSID " + staSsid;
            case Type.Data.SUBTYPE_STA_WIFI_PASSWORD:
                String staPassword = new String(data);
                response.setStaPassword(staPassword);
                return "SUBTYPE_STA_WIFI_SSID " + staPassword;
        }

        return String.format(Locale.ENGLISH, "Wrong subtype = %d", subType);
    }

    /**
     * Configure the device
     *
     * @param params config information
     * @return configure result
     */
    public BlufiStatusResponse configure(final BlufiConfigureParams params, boolean requireResponse) {
        registerNotification();

        BlufiStatusResponse result = new BlufiStatusResponse();

        int opMode = params.getOpMode();
        boolean postSuc = postDeviceMode(opMode);
        if (!postSuc) {
            result.setResultCode(BlufiStatusResponse.RESULT_POST_FAILED);
        } else {
            BlufiUtils.sleep(10);

            switch (opMode) {
                case OP_MODE_NULL:
                    if (requireResponse) {
                        receiveWifiState(result);
                    }
                    break;
                case OP_MODE_STA:
                    postSuc = postStaWifiInfo(params);
                    if (!postSuc) {
                        result.setResultCode(BlufiStatusResponse.RESULT_POST_FAILED);
                        break;
                    }
                    BlufiUtils.sleep(10);

                    if (requireResponse) {
                        receiveWifiState(result);
                    } else {
                        result.setResultCode(BlufiStatusResponse.RESULT_SUCCESS);
                    }
                    break;
                case OP_MODE_SOFTAP:
                    receiveWifiState(result);
                    postSuc = postSoftAPInfo(params, result);
                    if (!postSuc) {
                        result.setResultCode(BlufiStatusResponse.RESULT_POST_FAILED);
                        break;
                    }
                    BlufiUtils.sleep(10);

                    break;
                case OP_MODE_STASOFTAP:
                    receiveWifiState(result);
                    postSuc = postSoftAPInfo(params, result);
                    if (!postSuc) {
                        result.setResultCode(BlufiStatusResponse.RESULT_POST_FAILED);
                        break;
                    }
                    BlufiUtils.sleep(10);

                    postSuc = postStaWifiInfo(params);
                    if (!postSuc) {
                        result.setResultCode(BlufiStatusResponse.RESULT_POST_FAILED);
                        break;
                    }
                    BlufiUtils.sleep(10);

                    if (requireResponse) {
                        receiveWifiState(result);
                    }
                    break;
                default:
                    break;
            }
        }

        unregisterNotification();

        return result;
    }

    private boolean postDeviceMode(int deviceMode) {
        int type = getTypeValue(Type.Ctrl.PACKAGE_VALUE, Type.Ctrl.SUBTYPE_SET_OP_MODE);
        byte[] data = {(byte) deviceMode};

        return post(mEncrypted, mChecksum, mRequireAck, type, data);
    }

    /**
     * Post SoftAP mode information
     *
     * @param params   config information
     * @param response config response
     * @return post state and receive response successfully or not
     */
    private boolean postSoftAPInfo(BlufiConfigureParams params, BlufiStatusResponse response) {
        boolean postSuc;

        String ssid = params.getSoftAPSSID();
        if (!TextUtils.isEmpty(ssid)) {
            int ssidType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_SOFTAP_WIFI_SSID);
            postSuc = post(mEncrypted, mChecksum, mRequireAck, ssidType, params.getSoftAPSSID().getBytes());
            BlufiUtils.sleep(10);

            if (!postSuc || !receiveWifiState(response)) {
                return false;
            }
        }

        String password = params.getSoftAPPassword();
        if (!TextUtils.isEmpty(password)) {
            int pwdType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_SOFTAP_WIFI_PASSWORD);
            postSuc = post(mEncrypted, mChecksum, mRequireAck, pwdType, password.getBytes());
            BlufiUtils.sleep(10);

            if (!postSuc || !receiveWifiState(response)) {
                return false;
            }
        }

        int channel = params.getSoftAPChannel();
        if (channel > 0) {
            int channelType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_SOFTAP_CHANNEL);
            postSuc = post(mEncrypted, mChecksum, mRequireAck, channelType, new byte[]{(byte) channel});
            BlufiUtils.sleep(10);

            if (!postSuc || !receiveWifiState(response)) {
                return false;
            }
        }

        int maxConn = params.getSoftAPMaxConnection();
        if (maxConn > 0) {
            int maxConnType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_SOFTAP_MAX_CONNECTION_COUNT);
            postSuc = post(mEncrypted, mChecksum, mRequireAck, maxConnType, new byte[]{(byte) maxConn});
            BlufiUtils.sleep(10);

            if (!postSuc || !receiveWifiState(response)) {
                return false;
            }
        }

        int securityType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_SOFTAP_AUTH_MODE);
        byte[] securityBytes = {(byte) params.getSoftAPSecurity()};
        postSuc = post(mEncrypted, mChecksum, mRequireAck, securityType, securityBytes);
        return postSuc && receiveWifiState(response);
    }

    private boolean postStaWifiInfo(BlufiConfigureParams params) {
        boolean result;

        int configureSeqType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_NEG);
        result = post(mEncrypted, mChecksum, mRequireAck, configureSeqType,
                new byte[]{NEG_SET_CONFIGURE_SEQUENCE, (byte) params.getConfigureSequence()});
        if (!result) {
            return false;
        }
        BlufiUtils.sleep(10);

        if (params.getMeshID() != null) {
            int tokenType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_NEG);
            byte[] tokenBytes = params.getMeshID();
            byte[] postTokenBytes = new byte[tokenBytes.length + 1];
            postTokenBytes[0] = NEG_SET_MESH_ID;
            System.arraycopy(tokenBytes, 0, postTokenBytes, 1, tokenBytes.length);
            result = post(mEncrypted, mChecksum, mRequireAck, tokenType, postTokenBytes);
            if (!result) {
                return false;
            }
            BlufiUtils.sleep(10);
        }

        if (params.getWifiChannel() > 0) {
            int channelType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_NEG);
            byte channelByte = (byte) params.getWifiChannel();
            result = post(mEncrypted, mChecksum, mRequireAck, channelType, new byte[]{NEG_SET_WIFI_CHANNEL, channelByte});
            if (!result) {
                return false;
            }
            BlufiUtils.sleep(10);
        }

        int ssidType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_STA_WIFI_SSID);
        result = post(mEncrypted, mChecksum, mRequireAck, ssidType, params.getStaSSID().getBytes());
        if (!result) {
            return false;
        }
        BlufiUtils.sleep(10);

        int pwdType = getTypeValue(Type.Data.PACKAGE_VALUE, Type.Data.SUBTYPE_STA_WIFI_PASSWORD);
        result = post(mEncrypted, mChecksum, mRequireAck, pwdType, params.getStaPassword().getBytes());
        if (!result) {
            return false;
        }
        BlufiUtils.sleep(10);

        int comfirmType = getTypeValue(Type.Ctrl.PACKAGE_VALUE, Type.Ctrl.SUBTYPE_CONNECT_WIFI);
        return post(false, false, mRequireAck, comfirmType, (byte[]) null);
    }

    /**
     * Parse the response data and add the parsed data in BlufiNotiData
     *
     * @param response     date received
     * @param notification store the data
     * @return parse notification successfully or failed
     */
    private boolean parseNotification(byte[] response, BlufiNotiData notification) {
        if (response == null) {
            return true;
        }

        if (response.length < 4) {
            return true;
        }

        int sequence = toInt(response[2]);
        if (sequence == mReadSequence + 1) {
            mReadSequence = sequence;
        } else {
            return true;
        }

        int type = toInt(response[0]);
        int pkgType = getPackageType(type);
        int subType = getSubType(type);
        notification.setType(type);
        notification.setPkgType(pkgType);
        notification.setSubType(subType);

        int frameCtrl = toInt(response[1]);
        notification.setFrameCtrl(frameCtrl);
        FrameCtrlData frameCtrlData = new FrameCtrlData(frameCtrl);

        int dataLen = toInt(response[3]);
        byte[] dataBytes = new byte[dataLen];
        int dataOffset = 4;
        try {
            System.arraycopy(response, dataOffset, dataBytes, 0, dataLen);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        if (frameCtrlData.isEncrypted()) {
            EspAES espAES = new EspAES(mSecretKeyMD5, generateAESIV(sequence));
            dataBytes = espAES.decrypt(dataBytes);
        }

        if (frameCtrlData.isChecksum()) {
            int respChecksum1 = toInt(response[response.length - 1]);
            int respChecksum2 = toInt(response[response.length - 2]);

            List<Byte> checkByteList = new LinkedList<>();
            checkByteList.add((byte) sequence);
            checkByteList.add((byte) dataLen);
            for (byte b : dataBytes) {
                checkByteList.add(b);
            }
            int checksum = EspCRC.caluCRC(0, DataUtil.byteListToArray(checkByteList));

            int calcChecksum1 = (checksum >> 8) & 0xff;
            int calcChecksum2 = checksum & 0xff;
            if (respChecksum1 != calcChecksum1 || respChecksum2 != calcChecksum2) {
                return false;
            }
        }

        if (frameCtrlData.hasFrag()) {
            int totleLen = dataBytes[0] | (dataBytes[1] << 8);
            dataOffset = 2;
        } else {
            dataOffset = 0;
        }
        for (int i = dataOffset; i < dataBytes.length; i++) {
            notification.addData(dataBytes[i]);
        }

        return !frameCtrlData.hasFrag();
    }

    public void deauthenticate(String macAddress) {
        List<String> list = new ArrayList<>();
        list.add(macAddress);
        deauthenticate(list);
    }

    public void deauthenticate(List<String> macAddressList) {
        // TODO
        if (macAddressList.isEmpty()) {
            return;
        }

        registerNotification();

        int type = getTypeValue(Type.Ctrl.PACKAGE_VALUE, Type.Ctrl.SUBTYPE_DEAUTHENTICATE);
        List<Byte> dataList = new LinkedList<>();
        for (String mac : macAddressList) {
            byte[] bytes = DataUtil.hexIntStringToBytes(mac);
            for (byte b : bytes) {
                dataList.add(b);
            }
        }

        post(mEncrypted, mChecksum, false, type, dataList);

        unregisterNotification();
    }


    /**
     * Receive the data from the cache queue
     *
     * @return BlufiNotiData
     */
    private BlufiNotiData receive() {
        try {
            BlufiNotiData notiData = mNotiQueue.poll(TIMEOUT_READ, TimeUnit.MILLISECONDS);
            if (notiData == null) {
                return null;
            }

            BlufiNotiData result = new BlufiNotiData();
            result.setType(notiData.getType());
            result.setPkgType(notiData.getPkgType());
            result.setSubType(notiData.getSubType());
            result.setFrameCtrl(notiData.getFrameCtrl());
            result.addData(notiData.getDataArray());

            notiData.clear();

            return result;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    private boolean receiveAck(int sequence) {
        BlufiNotiData receiveData = receive();
        if (receiveData == null) {
            return false;
        }
        List<Byte> dataList = receiveData.getDataList();
        if (dataList.size() == 0) {
            return false;
        }
        int ack = dataList.get(0);

        return ack == sequence;
    }

    /**
     * Generate the byte array to post
     *
     * @param type       type value
     * @param frameCtrl  fc value
     * @param sequence   sequence
     * @param dataLength length of data
     * @param data       data Byte List
     * @return byte array
     */
    private byte[] getPostBytes(int type, int frameCtrl, int sequence, int dataLength, List<Byte> data) {
        LinkedList<Byte> byteList = new LinkedList<>();
        byteList.add((byte) type);
        byteList.add((byte) frameCtrl);
        byteList.add((byte) sequence);
        byteList.add((byte) dataLength);

        FrameCtrlData frameCtrlData = new FrameCtrlData(frameCtrl);
        byte[] checksumBytes = null;
        if (frameCtrlData.isChecksum()) {
            List<Byte> checkByteList = new LinkedList<>();
            checkByteList.add((byte) sequence);
            checkByteList.add((byte) dataLength);
            if (data != null) {
                checkByteList.addAll(data);
            }
            byte[] checkBytes = new byte[checkByteList.size()];
            for (int i = 0; i < checkBytes.length; i++) {
                checkBytes[i] = checkByteList.get(i);
            }
            int checksum = EspCRC.caluCRC(0, checkBytes);
            byte checksumByte1 = (byte) (checksum & 0xff);
            byte checksumByte2 = (byte) ((checksum >> 8) & 0xff);
            checksumBytes = new byte[]{checksumByte1, checksumByte2};
        }

        if (frameCtrlData.isEncrypted() && data != null) {
            byte[] unEncrytedData = new byte[data.size()];
            for (int i = 0; i < unEncrytedData.length; i++) {
                unEncrytedData[i] = data.get(i);
            }

            EspAES espAES = new EspAES(mSecretKeyMD5, generateAESIV(sequence));
            byte[] encrytedData = espAES.encrypt(unEncrytedData);
            List<Byte> encrytedDataList = new LinkedList<>();
            for (byte b : encrytedData) {
                encrytedDataList.add(b);
            }
            data = encrytedDataList;
        }
        if (data != null) {
            byteList.addAll(data);
        }

        if (checksumBytes != null) {
            byteList.add(checksumBytes[0]);
            byteList.add(checksumBytes[1]);
        }

        byte[] result = new byte[byteList.size()];
        for (int i = 0; i < byteList.size(); i++) {
            result[i] = byteList.get(i);
        }

        return result;
    }

    private boolean postNonData(boolean encrypt, boolean checksum, boolean requireAck, int type) {
        int frameCtrl = getFrameCTRLValue(encrypt, checksum, DIRECTION_OUTPUT, requireAck, false);
        int sequence = generateSendSequence();
        int dataLen = 0;

        byte[] postBytes = getPostBytes(type, frameCtrl, sequence, dataLen, null);
        boolean writeSuc = mBleHelper.write(mWriteChara, postBytes);
        if (!writeSuc) {
            return false;
        }
        if (requireAck) {
            return receiveAck(sequence);
        } else {
            return true;
        }
    }

    private boolean postContainData(boolean encrypt, boolean checksum, boolean requireAck, int type, List<Byte> dataList) {
        LinkedList<Byte> allDataList = new LinkedList<>();
        allDataList.addAll(dataList);

        LinkedList<Byte> postDataList = new LinkedList<>();

        while (!allDataList.isEmpty()) {
            Byte b = allDataList.poll();
            postDataList.add(b);
            int postDataLengthLimit = mPackageLengthLimit - PACKAGE_HEADER_LENGTH;
            if (checksum) {
                postDataLengthLimit -= 1;
            }
            if (postDataList.size() >= postDataLengthLimit) {
                boolean frag = !allDataList.isEmpty();
                if (frag) {
                    int frameCtrl = getFrameCTRLValue(encrypt, checksum, DIRECTION_OUTPUT, requireAck, true);
                    int sequence = generateSendSequence();
                    int totleLen = postDataList.size() + allDataList.size();
                    byte totleLen1 = (byte) (totleLen & 0xff);
                    byte totleLen2 = (byte) ((totleLen >> 8) & 0xff);
                    postDataList.add(0, totleLen2);
                    postDataList.add(0, totleLen1);
                    int posDatatLen = postDataList.size();

                    byte[] postBytes = getPostBytes(type, frameCtrl, sequence, posDatatLen, postDataList);
                    boolean writeSuc = mBleHelper.write(mWriteChara, postBytes);
                    postDataList.clear();
                    if (!writeSuc) {
                        return false;
                    }
                    if (requireAck && !receiveAck(sequence)) {
                        return false;
                    }

                    BlufiUtils.sleep(10L);
                }
            }
        }

        if (!postDataList.isEmpty()) {
            int frameCtrl = getFrameCTRLValue(encrypt, checksum, DIRECTION_OUTPUT, requireAck, false);
            int sequence = generateSendSequence();
            int postDataLen = postDataList.size();

            byte[] postBytes = getPostBytes(type, frameCtrl, sequence, postDataLen, postDataList);
            boolean writeSuc = mBleHelper.write(mWriteChara, postBytes);
            postDataList.clear();
            if (!writeSuc) {
                return false;
            }
            if (requireAck && !receiveAck(sequence)) {
                return false;
            }
        }

        return true;
    }

    private boolean post(boolean encrypt, boolean checksum, boolean requireAck, int type, List<Byte> dataList) {
        if (dataList != null) {
            return postContainData(encrypt, checksum, requireAck, type, dataList);
        } else {
            return postNonData(encrypt, checksum, requireAck, type);
        }
    }

    private boolean post(boolean encrypt, boolean checksum, boolean requireAck, int type, byte[] data) {
        LinkedList<Byte> allDataList;
        if (data == null) {
            allDataList = null;
        } else {
            allDataList = new LinkedList<>();
            for (byte b : data) {
                allDataList.add(b);
            }
        }

        return post(encrypt, checksum, requireAck, type, allDataList);
    }
}
