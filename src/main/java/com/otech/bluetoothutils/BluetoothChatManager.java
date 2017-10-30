/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.otech.bluetoothutils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothChatManager {


    // Debugging
    private final String TAG = "BluetoothChatManager";

    // Unique UUID for this application
    private final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800212c9a66");
    private final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800212c9a66");

    // Member fields
    private final int headerLength = 4;
    private final BluetoothAdapter mAdapter;
    private final Context context;
    private final Handler mainThread;
    private final BluetoothChatServiceListener listener;

    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private BluetoothChatServiceState mState;

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothChatManager(Context context, BluetoothChatServiceListener handler) {
        this.context = context;
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.mState = BluetoothChatServiceState.STATE_NONE;
        this.listener = handler;
        this.mainThread = new Handler(context.getMainLooper());

    }

    /**
     * Return the current connection state.
     */
    public synchronized BluetoothChatServiceState getState() {
        return mState;
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(final BluetoothChatServiceState state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // Give the new state to the Handler so the UI Activity can update
        if (listener != null) {
            mainThread.post(new Runnable() {
                @Override
                public void run() {
                    listener.chatServiceStateChanged(state);
                }
            });
        }

    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.interrupt();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.interrupt();
            mConnectedThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.interrupt();
            mInsecureAcceptThread = null;
        }

        setState(BluetoothChatServiceState.STATE_LISTEN);

        // Start the thread to listen on a BluetoothServerSocket
        mInsecureAcceptThread = new AcceptThread(false);
        mInsecureAcceptThread.start();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == BluetoothChatServiceState.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.interrupt();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.interrupt();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        setState(BluetoothChatServiceState.STATE_CONNECTING);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.interrupt();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.interrupt();
            mConnectedThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.interrupt();
            mInsecureAcceptThread = null;
        }
        setState(BluetoothChatServiceState.STATE_NONE);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != BluetoothChatServiceState.STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * Makes this device discoverable.
     */
    public void ensureDiscoverable() {
        if (mAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            context.startActivity(discoverableIntent);
        }
    }

    /**
     * Establish connection with other divice
     *
     * @param address Device address.
     * @param secure  Socket Security type - Secure (true) , Insecure (false)
     */
    public void connectDevice(String address, boolean secure) {
        // Get the BluetoothDevice object
        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        connect(device, secure);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    private synchronized void connected(BluetoothSocket socket, final BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.interrupt();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.interrupt();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
//        if (mSecureAcceptThread != null) {
//            mSecureAcceptThread.interrupt();
//            mSecureAcceptThread = null;
//        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.interrupt();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        if (listener != null) {
            mainThread.post(new Runnable() {
                @Override
                public void run() {
                    listener.chatServiceConnectedTo(device);
                }
            });
        }


        setState(BluetoothChatServiceState.STATE_CONNECTED);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        if (listener != null) {
            mainThread.post(new Runnable() {
                @Override
                public void run() {
                    listener.chatServiceLog("Unable to connect device");
                }
            });
        }


        // Start the service over to restart listening mode
        BluetoothChatManager.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        if (listener != null) {
            mainThread.post(new Runnable() {
                @Override
                public void run() {
                    listener.chatServiceLog("Device connection was lost");
                }
            });
        }


        // Start the service over to restart listening mode
        BluetoothChatManager.this.start();
    }

    private byte[] readBytes(InputStream inStream, byte[] headerBuffer, byte[] bodyBuffer) throws IOException {
        int bytesAvailable;
        int bufferSize;
        int bytes = inStream.read(headerBuffer);

        if (bytes != -1) {
            int length = ByteBuffer.wrap(headerBuffer).getInt();
            Log.i(TAG, "Received: " + length + " bytes");

            byte[] bytesArrays = new byte[length];
            bytesAvailable = length;
            int byteRead = 0;
            try {
                while (bytes > 0) {
                    bufferSize = Math.min(bytesAvailable, bodyBuffer.length);
                    bytes = inStream.read(bodyBuffer, 0, bufferSize);
                    System.arraycopy(bodyBuffer, 0, bytesArrays, byteRead, bytes);
                    byteRead += bytes;
                    bytesAvailable -= bytes;
                }
                return bytesArrays;

            } catch (Exception e) {
                Log.e(TAG, "Exception while reading", e);
            }
        }
        return null;
    }

    /**
     * Constants that indicate the current connection state
     */
    public enum BluetoothChatServiceState {
        STATE_NONE, // we're doing nothing
        STATE_LISTEN,  // now listening for incoming connections
        STATE_CONNECTING, // now initiating an outgoing connection
        STATE_CONNECTED // now connected to a remote device
    }

    public interface BluetoothChatServiceListener {
        void chatServiceStateChanged(BluetoothChatServiceState state);

        void chatServiceMessageWritten(byte[] bytes);

        void chatServiceMessageRead(byte[] message);

        void chatServiceConnectedTo(BluetoothDevice device);

        void chatServiceLog(String log);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord("secure",
                            MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            "inSecure", MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {

            Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            // Listen to the server socket if we're not connected
            while (mState != BluetoothChatServiceState.STATE_CONNECTED && !Thread.currentThread().isInterrupted()) {
                BluetoothSocket socket;
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (Exception e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothChatManager.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice(),
                                        mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    //ignored
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);
            dispose();
        }

        private void dispose() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                //ignored
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        @Override
        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread.run()-> Unable to connect", e);

                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    //ignored
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothChatManager.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread()-> Unable to get the BluetoothSocket input and output streams", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        @Override
        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");

            byte[] headerBuffer = new byte[headerLength];
            byte[] bodyBuffer = new byte[1024];
            byte[] bytes;

            // Keep listening to the InputStream while connected
            while (!Thread.currentThread().isInterrupted()) {
                try {

                    // Read from the InputStream
                    bytes = readBytes(mmInStream, headerBuffer, bodyBuffer);

                    if (bytes != null) {
                        // Send the obtained bytes to the UI Activity
                        if (listener != null) {
                            final byte[] finalBytes = bytes;
                            mainThread.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.chatServiceMessageRead(finalBytes);
                                }
                            });
                        }
                    }

                    //Check if we can still get the input stream
                    mmSocket.getInputStream();

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothChatManager.this.start();
                    break;
                }
            }
            dispose();
        }

        void write(byte[] buffer) {
            try {
                byte[] headerBytes = ByteBuffer.allocate(headerLength).putInt(buffer.length).array();

                final byte[] destination = new byte[headerBytes.length + buffer.length];
                System.arraycopy(headerBytes, 0, destination, 0, headerBytes.length);
                System.arraycopy(buffer, 0, destination, headerBytes.length, buffer.length);

                mmOutStream.write(destination);

                // Share the sent message back to the UI Activity
                if (listener != null) {
                    mainThread.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.chatServiceMessageWritten(destination);
                        }
                    });
                }

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        private void dispose() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}
