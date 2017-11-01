Bluetooth Scan and Communication Made Easy!
=======

This library provides easy to use bluetooth scanner and picker as well as connection manager.

![Release](https://jitpack.io/v/KasemJaffer/bluetooth_utils.svg)
https://jitpack.io/#KasemJaffer/bluetooth_utils

How to use
------------------------

```groovy
repositories {
    maven {
        url "https://jitpack.io"
    }
}

dependencies {
    compile 'com.github.KasemJaffer:bluetooth_utils:1.0.6'
}
```

To scan and pick bluetooth device
------------------------

```java
public class MainActivity extends AppCompatActivity implements
        BluetoothDeviceDialogListener,
        BluetoothChatServiceListener {
        
   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
       
       //To scan for bluetooth devices
       BluetoothScanDialog.show(this, null, true);
       
   }
                
   @Override
   public void onDeviceSelected(String name, String address) {
       Toast.makeText(this, address, Toast.LENGTH_SHORT).show();
   }
   
   @Override
   public void onNewDeviceAvailable(BluetoothDevice device) {
       Log.d(TAG, "deviceName: " + device.getName() + ", address: " + device.getAddress());
   }
   
   @Override
   public void discoveryStarted() {
       Log.d(TAG, "discoveryStarted()");
   }
   
   @Override
   public void discoveryStopped() {
       Log.d(TAG, "discoveryStopped()");
   }
        
}
```

To establish a connection to a BT device
------------------------

```java
public class MainActivity extends AppCompatActivity implements BluetoothChatServiceListener {
        
   private BluetoothChatManager bService;
   // Unique UUID for this application
   private final UUID chatUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800212c9a66");
        
   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
       
   
       bService = new BluetoothChatManager(this, this);
       bService.startListening(chatUUID);
       
       
       // And if you have the mac address you connect to using the following
       boolean secure = true;
       bService.connectDevice("FF:23:CE:34:F3:67", chatUUID, secure);
   }
                
   @Override
    public void chatServiceStateChanged(BluetoothChatServiceState state) {
        if (state == BluetoothChatServiceState.STATE_CONNECTED) {
    

        } else if (state == BluetoothChatServiceState.STATE_CONNECTING) {
          
            
        } else if (state == BluetoothChatServiceState.STATE_LISTEN
                || state == BluetoothChatServiceState.STATE_NONE) {
           
        }
    }

    @Override
    public void chatServiceMessageWritten(byte[] bytes) {

    }

    @Override
    public void chatServiceMessageRead(byte[] bytes) {

    }

    @Override
    public void chatServiceConnectedTo(BluetoothDevice device) {

    }

    @Override
    public void chatServiceLog(String log) {
        Toast.makeText(MainActivity.this, log, Toast.LENGTH_SHORT).show();
    }
        
}
```

To broadcast unlimited data using BLE advertising (Requires API level 21)
------------------------

```java
public class MainActivity extends AppCompatActivity  implements BleAdvertiseManager.AdvertiseResultInterface {

private final ParcelUuid serviceDataUUID = new ParcelUuid(UUID.fromString("00001111-0000-1000-8000-00805f9b34fb"));
private AdvertiseManager advertiser;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
       
       advertiser = new BleAdvertiseManager(this, serviceDataUUID, this);
       
   }
                
   public void onAdvertiseButtonClicked(View view) {
        advertiser.startAdvertising("Hello world");
   }
   
   public void onStopButtonClicked(View view) {
        advertiser.stopAdvertising();
   }
   
   @Override
   public void onAdvertiseStartFailure(String message) {
       Toast.makeText(this, "onAdvertiseStartFailure: " + message, Toast.LENGTH_SHORT).show();
   }
        
}
```

To scan and receive advertised data (Requires API level 18)
------------------------

```java
public class MainActivity extends AppCompatActivity implements BleScanManager.ScanResultInterface {

private final ParcelUuid serviceDataUUID = new ParcelUuid(UUID.fromString("00001111-0000-1000-8000-00805f9b34fb"));
private ScanManager scanner;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
       binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
       
       scanner = new BleScanManager(this, serviceDataUUID, this);
       
   }
                
   public void onScanButtonClicked(View view) {
        scanner.startScan();
   }
   
   public void onStopButtonClicked(View view) {
        scanner.stopScan();
   }

   @Override
   public void scanProgress(int progress, int outOf) {
        //Can be used to set progress indicator as follows
        //progressBar.setMax(outOf);
        //progressBar.setProgress(progress);
   }
   
   @Override
   public void onScanComplete(String message, int hash) {
       Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
   }
   
   @Override
   public void onScanFailed(String message) {
       Toast.makeText(this, "onScanFailed: " + message, Toast.LENGTH_SHORT).show();
   }
        
}
```