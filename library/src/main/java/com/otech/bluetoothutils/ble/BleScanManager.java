package com.otech.bluetoothutils.ble;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.support.annotation.RequiresApi;
import android.support.annotation.RequiresPermission;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanRecord;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;

/**
 * This is wrapper class that provides methods to perform advertisement data scan related operations
 * for Bluetooth LE devices ONLY that use {@link BleAdvertiseManager} to broadcast. An
 * application can scan for a particular type of Bluetooth LE devices using {@link ScanFilter}. It
 * can also request different types of callbacks for delivering the result.
 * <p>
 * Use {@link BluetoothLeScannerCompat#getScanner()} to get an instance of the scanner.
 * <p>
 * <b>Note:</b> Most of the scan methods here require
 * {@link Manifest.permission#BLUETOOTH_ADMIN} permission.
 *
 * @see ScanFilter
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleScanManager {

    private final ParcelUuid serviceDataUUID;
    private final List<ScanFilter> filters;
    private final ScanSettings settings;
    private final BluetoothLeScannerCompat scanner;
    private final ScanResultInterface scanResultInterface;
    private final Activity activity;
    private final byte[] hash = new byte[4];
    private byte[][] data;
    private int currentHashCode;
    private ScanCallback scanCallback;

    /**
     * Constructs a {@link BleScanManager}
     *
     * @param activity            Used to track activity life cycle.
     * @param serviceDataUUID     To use as a filter.
     * @param scanResultInterface Bluetooth LE scan callbacks. Scan results are reported using this callback.
     * @throws Exception Will throw exception if Bluetooth is turned off or if Bluetooth LE Scan is not supported on this device.
     */
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
    public BleScanManager(Activity activity, ParcelUuid serviceDataUUID, ScanResultInterface scanResultInterface) throws Exception {
        this.activity = activity;
        this.serviceDataUUID = serviceDataUUID;
        this.scanResultInterface = scanResultInterface;
        this.scanner = BluetoothLeScannerCompat.getScanner();
        if (scanner == null) {
            throw new Exception("Bluetooth not enabled or not supported. Try again after you enable the BT.");
        }

        this.scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                parseScanResult(result);
            }

            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                for (ScanResult r : results) {
                    parseScanResult(r);
                }
            }

            @Override
            @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                stopScan();
                if (BleScanManager.this.scanResultInterface != null) {
                    BleScanManager.this.scanResultInterface.onScanFailed(getError(errorCode));
                }
            }
        };
        this.filters = new ArrayList<>();
        this.filters.add(new ScanFilter.Builder().setServiceUuid(serviceDataUUID).build());
        this.settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setUseHardwareBatchingIfSupported(true).build();

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
            @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
            public void onActivityDestroyed(Activity activity) {
                if (BleScanManager.this.activity == activity) {
                    stopScan();
                }
            }
        });
    }

    /**
     * Start scanning for BLE advertisers. It will be stopped if the activity is destroyed.
     *
     * @return Error message if there is.
     */
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH})
    public String startScan() {
        try {
            scanner.startScan(filters, settings, scanCallback);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /**
     * Stops scanning for BLE advertisers.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void stopScan() {
        if (scanner != null) {
            scanner.stopScan(scanCallback);
        }
        currentHashCode = 0;
    }

    private String getError(int errorCode) {
        if (errorCode == 1) {
            return "Fails to start scan as BLE scan with the same settings is already started by the app.";
        } else if (errorCode == 2) {
            return "Fails to start scan as app cannot be registered.";
        } else if (errorCode == 3) {
            return "Fails to start scan due an internal error.";
        } else if (errorCode == 4) {
            return "Fails to start power optimized scan as this feature is not supported.";
        } else if (errorCode == 5) {
            return "Fails to start scan as it is out of hardware resources.";
        }
        return null;
    }

    private void parseScanResult(ScanResult result) {
        if (result == null || result.getDevice() == null)
            return;

        ScanRecord scanRecord = result.getScanRecord();
        if (scanRecord != null) {
            byte[] total = scanRecord.getServiceData(serviceDataUUID);
            if (total != null) {
                byte size = total[0];
                byte index = total[1];
                int hashCode = getHashCode(total);
                if (data == null || data.length != size || hashCode != currentHashCode) {
                    data = new byte[size][];
                }
                currentHashCode = hashCode;
                byte[] serviceData = Arrays.copyOfRange(total, 6, total.length);
                data[index] = serviceData;
            }
        }

        int received = packetsReceived();
        if (scanResultInterface != null) {
            scanResultInterface.scanProgress(received, data.length);
        }

        if (received == data.length) {
            if (scanResultInterface != null) {
                scanResultInterface.onScanComplete(getCompleteData(), currentHashCode);
            }
        }
    }

    private int getHashCode(byte[] total) {
        hash[0] = total[2];
        hash[1] = total[3];
        hash[2] = total[4];
        hash[3] = total[5];
        ByteBuffer bb = ByteBuffer.wrap(hash);
        return bb.getInt();
    }

    private String getCompleteData() {
        StringBuilder builder = new StringBuilder();
        for (byte[] adata : data) {
            builder.append(new String(adata, Charset.forName("UTF-8")));
        }
        return builder.toString();
    }

    private int packetsReceived() {
        int received = 0;
        for (byte[] aData : data) {
            if (aData != null) {
                received++;
            }
        }
        return received;
    }

    /**
     * Bluetooth LE scan callbacks. Scan results are reported using these callbacks.
     */
    public interface ScanResultInterface {
        void scanProgress(int progress, int outOf);

        void onScanComplete(String message, int hash);

        void onScanFailed(String message);
    }
}
