package com.otech.bluetoothutils;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class BluetoothScanManager {

    private static final String TAG = "BluetoothScanManager";

    private Context context;
    private BluetoothAdapter mBtAdapter;
    private OnBluetoothScanEventListener mOnBluetoothScanEventListener;
    private List<String> macAddresses = new ArrayList<>();

    /**
     * The BroadcastReceiver that listens for discovered devices and changes the title when
     * discovery is finished
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "onNewDeviceFound()-> " + device);

                if (!macAddresses.contains(device.getAddress()) && mOnBluetoothScanEventListener != null) {
                    macAddresses.add(device.getAddress());
                    mOnBluetoothScanEventListener.onNewDeviceFound(device);
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "onScanFinished()");
                if (mOnBluetoothScanEventListener != null) {
                    mOnBluetoothScanEventListener.onScanFinished();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "onScanStarted()");
                if (mOnBluetoothScanEventListener != null) {
                    mOnBluetoothScanEventListener.onScanStarted();
                }
            }
        }
    };

    public BluetoothScanManager(Context context, OnBluetoothScanEventListener onBluetoothScanEventListener) {
        this.context = context;
        this.mOnBluetoothScanEventListener = onBluetoothScanEventListener;

        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(mReceiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        context.registerReceiver(mReceiver, filter);
    }

    /**
     * Start device discover with the BluetoothAdapter
     */
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN})
    public boolean startDiscovery() {
        Log.d(TAG, "doDiscovery()");

        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        return mBtAdapter.startDiscovery();
    }

    /**
     * Stop device discovery
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void stopDiscovery() {
        Log.d(TAG, "stopDiscovery()");

        // Make sure we're not doing discovery anymore
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // Unregister broadcast listeners
        context.unregisterReceiver(mReceiver);
    }

    /**
     * Get paired devices
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH)
    public Set<BluetoothDevice> getBondedDevices() {
        return mBtAdapter.getBondedDevices();
    }

    public interface OnBluetoothScanEventListener {

        void onNewDeviceFound(BluetoothDevice device);

        void onScanFinished();

        void onScanStarted();
    }
}
