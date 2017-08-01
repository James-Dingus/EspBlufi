package com.espressif.libs.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import com.espressif.libs.log.EspLog;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EspBleHelper {
    private final Object mConnectLock = new Object();
    private final List<GattCallback> mUserCallbacks;

    private Context mContext;
    private BluetoothManager mBluetoothManager;

    private BluetoothGatt mGatt;
    private Callback mCallback;

    private int mConnectState;

    public EspBleHelper(Context context) {
        mContext = context.getApplicationContext();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mUserCallbacks = new LinkedList<>();

        mConnectState = BluetoothProfile.STATE_DISCONNECTED;
    }

    public int getConnectState() {
        return mConnectState;
    }

    public void registerGattCallback(GattCallback callback) {
        synchronized (mUserCallbacks) {
            if (!mUserCallbacks.contains(callback)) {
                mUserCallbacks.add(callback);
            }
        }
    }

    public void unregisterGattCallback(GattCallback callback) {
        synchronized (mUserCallbacks) {
            if (mUserCallbacks.contains(callback)) {
                mUserCallbacks.remove(callback);
            }
        }
    }

    public boolean connectGatt(BluetoothDevice device) {
        synchronized (mConnectLock) {
            EspLog.d(String.format("EspBleHelper %s connectGatt", device.getName()));
            if (mGatt != null) {
                throw new IllegalStateException("the gatt has connected a device already");
            }

            final int tryCount = 2;
            boolean result = false;
            for (int i = 0; i < tryCount; i++) {
                EspLog.d(String.format(Locale.ENGLISH, "EspBleHelper %s connect %d", device.getName(), i));
                mCallback = new Callback();
                mGatt = connect(device, mCallback);
                result = mCallback.waitConnect(4000L);

                if (!result) {
                    EspLog.d(String.format("EspBleHelper %s retry connect", device.getName()));
                    mGatt.connect();
                    result = mCallback.waitConnect(4000L);
                }

                if (result) {
                    EspLog.d(String.format("EspBleHelper %s discoverServices", device.getName()));
                    mCallback.clear();
                    mGatt.discoverServices();
                    result = mCallback.waitService(8000L);
                }

                if (!result) {
                    EspLog.d(String.format("EspBleHelper %s connectGatt close", device.getName()));
                    mGatt.close();
                    mGatt = null;
                    mCallback.clear();
                    mConnectState = BluetoothProfile.STATE_DISCONNECTED;
                }

                if (result) {
                    break;
                }
            }

            EspLog.d(String.format("EspBleHelper %s connectGatt result %b", device.getName(), result));
            return result;
        }
    }

    private BluetoothGatt connect(BluetoothDevice device, Callback callback) {
        BluetoothGatt gatt;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(mContext, false, callback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(mContext, false, callback);
        }

        return gatt;
    }

    public void close() {
        synchronized (mConnectLock) {
            EspLog.d("EspBleHelper close");
            if (mGatt != null) {
                EspLog.d("EspBleHelper close1");
                mGatt.disconnect();
                mGatt.close();
                mConnectState = BluetoothProfile.STATE_DISCONNECTED;

                mUserCallbacks.clear();
                mCallback.clear();
                mCallback = null;
                mGatt = null;
            }
            EspLog.d("EspBleHelper close2");
        }
    }

    public BluetoothGattService discoverService(UUID uuid) {
        EspLog.d("EspBleHelper discoverService");
        if (mGatt == null || mConnectState != BluetoothGatt.STATE_CONNECTED) {
            return null;
        }

        return mGatt.getService(uuid);
    }

    public boolean requestMtu(int mtu) {
        EspLog.d("EspBleHelper requestMtu");
        if (mGatt == null || mConnectState != BluetoothGatt.STATE_CONNECTED) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mGatt.requestMtu(mtu)) {
                return mCallback.waitMtu();
            }
        }

        return false;
    }

    public boolean write(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (mGatt == null || mConnectState != BluetoothGatt.STATE_CONNECTED) {
            return false;
        }

        characteristic.setValue(data);
        mGatt.writeCharacteristic(characteristic);
        return mCallback.waitWrite(3000L);
    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (mGatt == null) {
            return false;
        }

        return mGatt.setCharacteristicNotification(characteristic, enable);
    }

    public static class GattCallback {
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        }

        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        }
    }

    private class Callback extends BluetoothGattCallback {
        private LinkedBlockingQueue<Boolean> mConnectQueue;
        private LinkedBlockingQueue<Boolean> mServiceQueue;
        private LinkedBlockingQueue<Boolean> mMtuQueue;
        private LinkedBlockingQueue<Boolean> mWriteQueue;

        Callback() {
            mConnectQueue = new LinkedBlockingQueue<>();
            mServiceQueue = new LinkedBlockingQueue<>();
            mMtuQueue = new LinkedBlockingQueue<>();
            mWriteQueue = new LinkedBlockingQueue<>();
        }

        void notifyDisconnected() {
            EspLog.w("notifyDisconnected");
            mServiceQueue.add(false);
            mMtuQueue.add(false);
            mWriteQueue.add(false);
        }

        void clear() {
            mConnectQueue.clear();
            mServiceQueue.clear();
            mMtuQueue.clear();
            mWriteQueue.clear();
        }

        boolean waitConnect() {
            try {
                return mConnectQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }

        boolean waitConnect(long timeout) {
            try {
                Boolean result = mConnectQueue.poll(timeout, TimeUnit.MILLISECONDS);
                if (result == null) {
                    return false;
                } else {
                    return result;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return false;
        }

        boolean waitService() {
            try {
                return mServiceQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }

        boolean waitService(long timeout) {
            try {
                Boolean result = mServiceQueue.poll(timeout, TimeUnit.MILLISECONDS);
                if (result == null) {
                    return false;
                } else {
                    return result;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }

        boolean waitMtu() {
            try {
                return mMtuQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }

        boolean waitWrite() {
            try {
                return mWriteQueue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }

        boolean waitWrite(long timeout) {
            try {
                Boolean result = mWriteQueue.poll(timeout, TimeUnit.MILLISECONDS);
                if (result == null) {
                    return false;
                } else {
                    return result;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            EspLog.i(String.format(Locale.ENGLISH, "EspBleHelper %s onConnectionStateChange status=%d, state=%d",
                    gatt.getDevice().getName(), status, newState));
            mConnectState = newState;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        mConnectQueue.add(true);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        mConnectQueue.add(false);
                        notifyDisconnected();
                        break;
                }
            } else {
                mConnectQueue.add(false);
                notifyDisconnected();
            }

            for (GattCallback callback : mUserCallbacks) {
                callback.onConnectionStateChange(gatt, status, newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            EspLog.i(String.format(Locale.ENGLISH, "EspBleHelper %s onServicesDiscovered status=%d",
                    gatt.getDevice().getName(), status));
            mServiceQueue.add(status == BluetoothGatt.GATT_SUCCESS);

            for (GattCallback callback : mUserCallbacks) {
                callback.onServicesDiscovered(gatt, status);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            EspLog.i(String.format(Locale.ENGLISH, "EspBleHelper %s onMtuChanged status=%d, mtu=%d",
                    gatt.getDevice().getName(), status, mtu));
            mMtuQueue.add(status == BluetoothGatt.GATT_SUCCESS);

            for (GattCallback callback : mUserCallbacks) {
                callback.onMtuChanged(gatt, mtu, status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            EspLog.i(String.format(Locale.ENGLISH, "EspBleHelper %s onCharacteristicWrite status=%d",
                    gatt.getDevice().getName(), status));
            mWriteQueue.add(status == BluetoothGatt.GATT_SUCCESS);
            for (GattCallback callback : mUserCallbacks) {
                callback.onCharacteristicWrite(gatt, characteristic, status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            for (GattCallback callback : mUserCallbacks) {
                callback.onCharacteristicChanged(gatt, characteristic);
            }
        }
    }
}
