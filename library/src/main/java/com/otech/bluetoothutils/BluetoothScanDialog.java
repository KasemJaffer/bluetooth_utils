package com.otech.bluetoothutils;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.Serializable;
import java.util.Set;

public class BluetoothScanDialog extends DialogFragment {


    private static final String TAG = "BluetoothScanManager";
    private static final int REQUEST_ENABLE_BT = 232;
    private static final int REQUEST_PERMISSION_BT = 233;
    private static final String ARGS_OPTIONS = "options";
    private static final String ARGS_MAKE_DISCOVERABLE = "discoverable";

    private BluetoothAdapter mBtAdapter;
    private BluetoothScanManager mBluetoothScanManager;
    private Activity activity;
    private ListView pairedDevices;
    private TextView newDeviceTitle;
    private TextView pairedDevicesTitle;
    private ListView newDevices;
    private Button scanButton;
    private ArrayAdapter<String> newDevicesArrayAdapter;
    private ArrayAdapter<String> pairedDevicesArrayAdapter;
    private TextView title;
    private ProgressBar progressBar;
    private UIOptions options;
    private boolean makeDiscoverable = false;
    private AdapterView.OnItemClickListener mDeviceClickListener;

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN})
    public BluetoothScanDialog() {
        this.mDeviceClickListener = new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
                // Cancel discovery because it's costly and we're about to connect
                mBtAdapter.cancelDiscovery();

                // Get the device MAC address, which is the last 17 chars in the View
                String info = ((TextView) v).getText().toString();
                String[] infos = info.split("\n");
                if (infos.length == 2) {
                    String name = infos[0];
                    String address = infos[1];
                    ((BluetoothDeviceDialogListener) activity).onDeviceSelected(name, address);
                    dismiss();
                }
            }
        };
    }


    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN})
    public static BluetoothScanDialog show(AppCompatActivity context, UIOptions options, boolean makeBluetoothDiscoverable) {
        if (!(context instanceof BluetoothDeviceDialogListener)) {
            throw new UnsupportedOperationException("Activity must implement OnBluetoothDeviceSelectedListener");
        }
        FragmentManager fm = context.getSupportFragmentManager();
        BluetoothScanDialog frag = new BluetoothScanDialog();
        if (options == null) {
            options = new UIOptions();
        }
        Bundle args = new Bundle();
        args.putSerializable(ARGS_OPTIONS, options);
        args.putBoolean(ARGS_MAKE_DISCOVERABLE, makeBluetoothDiscoverable);
        frag.setArguments(args);

        frag.show(fm, "scan_device_frag");
        return frag;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        this.options = (UIOptions) arguments.get(ARGS_OPTIONS);
        this.makeDiscoverable = arguments.getBoolean(ARGS_MAKE_DISCOVERABLE);
        this.mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        this.activity = getActivity();
        this.mBluetoothScanManager = new BluetoothScanManager(activity, new BluetoothScanManager.OnBluetoothScanEventListener() {
            @Override
            public void onNewDeviceFound(BluetoothDevice device) {
                // If it's already paired, skip it, because it's been listed already
//                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                ((BluetoothDeviceDialogListener) activity).onNewDeviceAvailable(device);
                newDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
//                }
            }

            @Override
            public void onScanFinished() {
                ((BluetoothDeviceDialogListener) activity).discoveryStopped();
                Dialog dialog = getDialog();
                if (dialog != null) {
                    updateUI(false);
                }
            }

            @Override
            public void onScanStarted() {
                ((BluetoothDeviceDialogListener) activity).discoveryStarted();
            }
        });

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LinearLayout parentLayout = buildView();


        builder.setView(parentLayout);

        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startDiscovery();
            }
        });

        // Initialize array adapters. One for already paired devices and
        // one for newly discovered devices
        pairedDevicesArrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setTextColor(options.itemTextColor);
                return textView;
            }
        };
        newDevicesArrayAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setTextColor(options.itemTextColor);
                return textView;
            }
        };

        // Set up the ListView for paired devices
        pairedDevices.setAdapter(pairedDevicesArrayAdapter);
        pairedDevices.setOnItemClickListener(mDeviceClickListener);

        // Set up the ListView for newly discovered devices
        newDevices.setAdapter(newDevicesArrayAdapter);
        newDevices.setOnItemClickListener(mDeviceClickListener);

        startDiscovery();
        return builder.create();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH)
    private void updateUI(boolean scanning) {
        if (scanning) {
            title.setText(options.titleWhenScanning);
            scanButton.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            newDevicesArrayAdapter.clear();
            pairedDevicesArrayAdapter.clear();

            Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
            // If there are paired devices, add each one to the ArrayAdapter
            if (pairedDevices != null) {
                for (BluetoothDevice device : pairedDevices) {
                    pairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }

            if (pairedDevicesArrayAdapter.getCount() == 0) {
                pairedDevicesArrayAdapter.add("No devices have been paired");
            }

        } else {
            title.setText(options.titleWhenIdle);
            scanButton.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);

            if (newDevicesArrayAdapter.getCount() == 0) {
                newDevicesArrayAdapter.add("No devices found");
            }
        }
    }

    @Override
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void onDismiss(DialogInterface dialog) {
        stopDiscovery();
        super.onDismiss(dialog);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    doDiscovery();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_BT:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startDiscovery();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }

    @NonNull
    private LinearLayout buildView() {

        LinearLayout parentLayout = new LinearLayout(activity);
        parentLayout.setBackgroundColor(options.backgroundColor);
        parentLayout.setOrientation(LinearLayout.VERTICAL);
        parentLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout linearLayout_94 = new LinearLayout(activity);
        linearLayout_94.setGravity(Gravity.CENTER);
        linearLayout_94.setOrientation(LinearLayout.HORIZONTAL);
        int padding = dpToPixels(activity, 8);
        linearLayout_94.setPadding(padding, padding, padding, padding);
        linearLayout_94.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        title = new TextView(activity, null, android.R.attr.windowTitleStyle);
        title.setText(options.titleWhenScanning);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        ));
        linearLayout_94.addView(title);

        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        progressBar.setVisibility(View.GONE);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        linearLayout_94.addView(progressBar);
        parentLayout.addView(linearLayout_94);

        pairedDevicesTitle = new TextView(activity);
        pairedDevicesTitle.setText(options.titleForPairedDevices);
        pairedDevicesTitle.setBackgroundColor(options.headersTitleBackgroundColor);
        pairedDevicesTitle.setPadding(dpToPixels(getContext(), 5), 0, 0, 0);
        pairedDevicesTitle.setTextColor(options.headersTitleColor);
        pairedDevicesTitle.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.addView(pairedDevicesTitle);
        pairedDevices = new ListView(activity);
        pairedDevices.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
        ));
        parentLayout.addView(pairedDevices);

        newDeviceTitle = new TextView(activity);
        newDeviceTitle.setText(options.titleForNewDevices);
        newDeviceTitle.setBackgroundColor(options.headersTitleBackgroundColor);
        newDeviceTitle.setPadding(dpToPixels(getContext(), 5), 0, 0, 0);
        newDeviceTitle.setTextColor(options.headersTitleColor);
        newDeviceTitle.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        parentLayout.addView(newDeviceTitle);

        newDevices = new ListView(activity);
        newDevices.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                2.0f
        ));
        parentLayout.addView(newDevices);

        //Setup scan button
        int[] attrs = new int[]{R.attr.selectableItemBackground};
        TypedArray typedArray = activity.obtainStyledAttributes(attrs);
        int backgroundResource = typedArray.getResourceId(0, 0);
        typedArray.recycle();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END;
        padding = dpToPixels(getContext(), 2);
        params.setMargins(padding, padding, padding, padding);

        scanButton = new Button(activity);
        scanButton.setText(options.titleForScanButton);
        scanButton.setBackgroundResource(backgroundResource);
        scanButton.setLayoutParams(params);
        scanButton.setTextColor(fetchAccentColor());
        parentLayout.addView(scanButton);
        return parentLayout;
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN})
    private boolean startDiscovery() {
        Log.d(TAG, "doDiscovery()");

        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_PERMISSION_BT);

            return false;
        }

        if (!mBtAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            return false;
        }

        if (makeDiscoverable && mBtAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }

        return doDiscovery();
    }

    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN})
    public boolean doDiscovery() {

        updateUI(true);

        // Request discover from BluetoothAdapter
        return mBluetoothScanManager.startDiscovery();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADMIN)
    public void stopDiscovery() {
        Log.d(TAG, "stopDiscovery()");

        // Unregister broadcast listeners
        mBluetoothScanManager.stopDiscovery();
    }

    private int dpToPixels(Context context, float dp) {
        // Get the screen's density scale
        final float scale = context.getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (int) (dp * scale + 0.5f);
    }

    private int fetchAccentColor() {
        TypedValue typedValue = new TypedValue();

        TypedArray a = getContext().obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorAccent});
        int color = a.getColor(0, 0);

        a.recycle();

        return color;
    }

    public interface BluetoothDeviceDialogListener {
        void onDeviceSelected(String name, String address);

        void onNewDeviceAvailable(BluetoothDevice device);

        void discoveryStarted();

        void discoveryStopped();
    }

    public static class UIOptions implements Serializable {
        private String titleWhenIdle = "Select a device to connect";
        private String titleWhenScanning = "Scanning for devices";
        private String titleForPairedDevices = "Paired Devices";
        private String titleForNewDevices = "Other Available Devices";
        private String titleForScanButton = "SCAN";
        private int headersTitleBackgroundColor = Color.parseColor("#FF666666");
        private int backgroundColor = Color.WHITE;
        private int headersTitleColor = Color.WHITE;
        private int itemTextColor = Color.BLACK;

        public UIOptions setTitleWhenIdle(String titleWhenIdle) {
            this.titleWhenIdle = titleWhenIdle;
            return this;
        }

        public UIOptions setTitleWhenScanning(String titleWhenScanning) {
            this.titleWhenScanning = titleWhenScanning;
            return this;
        }

        public UIOptions setTitleForPairedDevices(String titleForPairedDevices) {
            this.titleForPairedDevices = titleForPairedDevices;
            return this;
        }

        public UIOptions setTitleForNewDevices(String titleForNewDevices) {
            this.titleForNewDevices = titleForNewDevices;
            return this;
        }

        public UIOptions setTitleForScanButton(String titleForScanButton) {
            this.titleForScanButton = titleForScanButton;
            return this;
        }

        public UIOptions setHeadersTitleBackgroundColor(int headersTitleBackgroundColor) {
            this.headersTitleBackgroundColor = headersTitleBackgroundColor;
            return this;
        }

        public UIOptions setBackgroundColor(int backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public UIOptions setHeadersTitleColor(int headersTitleColor) {
            this.headersTitleColor = headersTitleColor;
            return this;
        }

        public void setItemTextColor(int itemTextColor) {
            this.itemTextColor = itemTextColor;
        }
    }

}
