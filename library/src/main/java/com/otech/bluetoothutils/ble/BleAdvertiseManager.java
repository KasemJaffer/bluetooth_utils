package com.otech.bluetoothutils.ble;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * This is a wrapper class that provides a way to perform Bluetooth LE advertise operations, such as starting and
 * stopping advertising. This wrapper can broadcast unlimited bytes of advertisement data in packets 31 bytes at a time
 * <p>
 * To get an instance of {@link BluetoothLeAdvertiser}, call the
 * {@link BluetoothAdapter#getBluetoothLeAdvertiser()} method.
 * <p>
 * <b>Note:</b> Most of the methods here require {@link android.Manifest.permission#BLUETOOTH_ADMIN}
 * permission.
 *
 * @see AdvertiseData
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class BleAdvertiseManager {

    private final String TAG = "BleAdvertiseManager";
    private final ParcelUuid serviceDataUUID;
    private final BluetoothLeAdvertiser advertiser;
    private final AdvertiseSettings settings;
    private final AdvertiseResultInterface advertiseResultInterface;
    private final Activity activity;
    private Thread thread;
    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            stopAdvertising();
            if (advertiseResultInterface != null) {
                advertiseResultInterface.onAdvertiseStartFailure(getErrorMessage(errorCode));
            }
        }
    };

    /**
     * Constructs a {@link BleAdvertiseManager} object for Bluetooth LE Advertising operations.
     * Will return null if Bluetooth is turned off or if Bluetooth LE Advertising is not
     * supported on this device.
     *
     * @param activity                 Used to track activity life cycle.
     * @param serviceDataUUID          To use as a filter.
     * @param advertiseResultInterface Bluetooth LE advertise callbacks. Advertise results are reported using this callback.
     * @throws Exception Will throw exception if Bluetooth is turned off or if Bluetooth LE Advertising is not supported on this device.
     */
    public BleAdvertiseManager(Activity activity, ParcelUuid serviceDataUUID, AdvertiseResultInterface advertiseResultInterface) throws Exception {
        this.activity = activity;
        this.serviceDataUUID = serviceDataUUID;
        this.advertiseResultInterface = advertiseResultInterface;
        this.advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        if (advertiser == null) {
            throw new Exception("Bluetooth not enabled or not supported. Try again after you enable the BT.");
        }
        this.settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setTimeout(1000)
                .setConnectable(false)
                .build();

        this.activity.getApplication().registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (BleAdvertiseManager.this.activity == activity) {
                    stopAdvertising();
                }
            }
        });
    }

    /**
     * Starts advertising the given message
     *
     * @param message used ad advertiseData
     */
    public void startAdvertising(String message) {

        stopAdvertising();

        final String[] split = split(message, 8);
        final byte[] hash = ByteBuffer.allocate(4).putInt(message.hashCode()).array();

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        for (byte i = 0; i < split.length; i++) {

                            byte[] headerBytes = {(byte) split.length, i, hash[0], hash[1], hash[2], hash[3]};
                            byte[] dataBytes = split[i].getBytes(Charset.forName("UTF-8"));
                            byte[] total = new byte[headerBytes.length + dataBytes.length];
                            System.arraycopy(headerBytes, 0, total, 0, headerBytes.length);
                            System.arraycopy(dataBytes, 0, total, headerBytes.length, dataBytes.length);
                            final AdvertiseData data = new AdvertiseData.Builder()
                                    .addServiceUuid(serviceDataUUID)
                                    .addServiceData(serviceDataUUID, total)
                                    .build();
                            Log.d(TAG, "Advertising packet " + i + " of " + split.length);
                            advertiser.startAdvertising(settings, data, advertiseCallback);
                            Thread.sleep(500);
                            advertiser.stopAdvertising(advertiseCallback);
                        }
                    }
                } catch (InterruptedException e) {
                    advertiser.stopAdvertising(advertiseCallback);
                    Thread.currentThread().interrupt();
                }
            }
        });
        thread.start();
    }

    /**
     * Stops advertising
     */
    public void stopAdvertising() {
        if (thread != null)
            thread.interrupt();

        if (advertiser != null) {
            advertiser.stopAdvertising(advertiseCallback);
            try {
                Method method = advertiser.getClass().getMethod("cleanup");
                method.invoke(advertiser);
            } catch (Exception e) {
                Log.e(TAG, "Unable to invoke cleanup() method", e);
            }
        }
    }

    private String[] split(String src, int len) {
        String[] result = new String[(int) Math.ceil((double) src.length() / (double) len)];
        for (int i = 0; i < result.length; i++)
            result[i] = src.substring(i * len, Math.min(src.length(), (i + 1) * len));
        return result;
    }

    private String getErrorMessage(int errorCode) {
        if (errorCode == 1) {
            return "DATA TOO LARGE";
        } else if (errorCode == 2) {
            return "TOO MANY ADVERTISERS";
        } else if (errorCode == 3) {
            return "ALREADY STARTED";
        } else if (errorCode == 4) {
            return "INTERNAL ERROR";
        } else if (errorCode == 5) {
            return "FEATURE UNSUPPORTED";
        }
        return null;
    }

    /**
     * Bluetooth LE advertise callbacks. Advertise results are reported using these callbacks.
     */
    public interface AdvertiseResultInterface {
        void onAdvertiseStartFailure(String message);
    }


}
