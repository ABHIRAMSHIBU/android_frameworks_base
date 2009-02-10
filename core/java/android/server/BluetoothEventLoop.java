/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.server;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothError;
import android.bluetooth.BluetoothIntent;
import android.bluetooth.IBluetoothDeviceCallback;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashMap;

/**
 * TODO: Move this to
 * java/services/com/android/server/BluetoothEventLoop.java
 * and make the contructor package private again.
 *
 * @hide
 */
class BluetoothEventLoop {
    private static final String TAG = "BluetoothEventLoop";
    private static final boolean DBG = false;

    private int mNativeData;
    private Thread mThread;
    private boolean mInterrupted;
    private HashMap<String, Integer> mPasskeyAgentRequestData;
    private HashMap<String, IBluetoothDeviceCallback> mGetRemoteServiceChannelCallbacks;
    private BluetoothDeviceService mBluetoothService;
    private Context mContext;

    private static final String BLUETOOTH_ADMIN_PERM = android.Manifest.permission.BLUETOOTH_ADMIN;
    private static final String BLUETOOTH_PERM = android.Manifest.permission.BLUETOOTH;

    static { classInitNative(); }
    private static native void classInitNative();

    /* pacakge */ BluetoothEventLoop(Context context, BluetoothDeviceService bluetoothService) {
        mBluetoothService = bluetoothService;
        mContext = context;
        mPasskeyAgentRequestData = new HashMap();
        mGetRemoteServiceChannelCallbacks = new HashMap();
        initializeNativeDataNative();
    }
    private native void initializeNativeDataNative();

    protected void finalize() throws Throwable {
        try {
            cleanupNativeDataNative();
        } finally {
            super.finalize();
        }
    }
    private native void cleanupNativeDataNative();

    /* pacakge */ HashMap<String, IBluetoothDeviceCallback> getRemoteServiceChannelCallbacks() {
        return mGetRemoteServiceChannelCallbacks;
    }

    /* pacakge */ HashMap<String, Integer> getPasskeyAgentRequestData() {
        return mPasskeyAgentRequestData;
    }

    private synchronized boolean waitForAndDispatchEvent(int timeout_ms) {
        return waitForAndDispatchEventNative(timeout_ms);
    }
    private native boolean waitForAndDispatchEventNative(int timeout_ms);

    /* package */ synchronized void start() {

        if (mThread != null) {
            // Already running.
            return;
        }
        mThread = new Thread("Bluetooth Event Loop") {
                @Override
                public void run() {
                    try {
                        if (setUpEventLoopNative()) {
                            while (!mInterrupted) {
                                waitForAndDispatchEvent(0);
                                sleep(500);
                            }
                            tearDownEventLoopNative();
                        }
                    } catch (InterruptedException e) { }
                    if (DBG) log("Event Loop thread finished");
                }
            };
        if (DBG) log("Starting Event Loop thread");
        mInterrupted = false;
        mThread.start();
    }
    private native boolean setUpEventLoopNative();
    private native void tearDownEventLoopNative();

    public synchronized void stop() {
        if (mThread != null) {
            mInterrupted = true;
            try {
                mThread.join();
                mThread = null;
            } catch (InterruptedException e) {
                Log.i(TAG, "Interrupted waiting for Event Loop thread to join");
            }
        }
    }

    public synchronized boolean isEventLoopRunning() {
        return mThread != null;
    }

    /*package*/ void onModeChanged(String bluezMode) {
        int mode = BluetoothDeviceService.bluezStringToScanMode(bluezMode);
        if (mode >= 0) {
            Intent intent = new Intent(BluetoothIntent.SCAN_MODE_CHANGED_ACTION);
            intent.putExtra(BluetoothIntent.SCAN_MODE, mode);
            mContext.sendBroadcast(intent, BLUETOOTH_PERM);
        }
    }

    private void onDiscoveryStarted() {
        mBluetoothService.setIsDiscovering(true);
        Intent intent = new Intent(BluetoothIntent.DISCOVERY_STARTED_ACTION);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onDiscoveryCompleted() {
        mBluetoothService.setIsDiscovering(false);
        Intent intent = new Intent(BluetoothIntent.DISCOVERY_COMPLETED_ACTION);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void onPairingRequest() {
        Intent intent = new Intent(BluetoothIntent.PAIRING_REQUEST_ACTION);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }
    
    private void onPairingCancel() {
        Intent intent = new Intent(BluetoothIntent.PAIRING_CANCEL_ACTION);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    private void onRemoteDeviceFound(String address, int deviceClass, short rssi) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_DEVICE_FOUND_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        intent.putExtra(BluetoothIntent.CLASS, deviceClass);
        intent.putExtra(BluetoothIntent.RSSI, rssi);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteDeviceDisappeared(String address) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_DEVICE_DISAPPEARED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteClassUpdated(String address, int deviceClass) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_DEVICE_CLASS_UPDATED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        intent.putExtra(BluetoothIntent.CLASS, deviceClass);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteDeviceConnected(String address) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_DEVICE_CONNECTED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteDeviceDisconnectRequested(String address) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_DEVICE_DISCONNECT_REQUESTED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteDeviceDisconnected(String address) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_DEVICE_DISCONNECTED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteNameUpdated(String address, String name) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_NAME_UPDATED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        intent.putExtra(BluetoothIntent.NAME, name);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteNameFailed(String address) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_NAME_FAILED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }
    private void onRemoteNameChanged(String address, String name) {
        Intent intent = new Intent(BluetoothIntent.REMOTE_NAME_UPDATED_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        intent.putExtra(BluetoothIntent.NAME, name);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void onCreateBondingResult(String address, int result) {
        address = address.toUpperCase();
        if (result == BluetoothError.SUCCESS) {
            mBluetoothService.getBondState().setBondState(address, BluetoothDevice.BOND_BONDED);
        } else {
            mBluetoothService.getBondState().setBondState(address,
                                                          BluetoothDevice.BOND_NOT_BONDED, result);
        }
    }

    private void onBondingCreated(String address) {
        mBluetoothService.getBondState().setBondState(address.toUpperCase(),
                                                      BluetoothDevice.BOND_BONDED);
    }

    private void onBondingRemoved(String address) {
        mBluetoothService.getBondState().setBondState(address.toUpperCase(),
                BluetoothDevice.BOND_NOT_BONDED, BluetoothDevice.UNBOND_REASON_REMOVED);
    }

    private void onNameChanged(String name) {
        Intent intent = new Intent(BluetoothIntent.NAME_CHANGED_ACTION);
        intent.putExtra(BluetoothIntent.NAME, name);
        mContext.sendBroadcast(intent, BLUETOOTH_PERM);
    }

    private void onPasskeyAgentRequest(String address, int nativeData) {
        address = address.toUpperCase();
        mPasskeyAgentRequestData.put(address, new Integer(nativeData));

        if (mBluetoothService.getBondState().getBondState(address) ==
                BluetoothDevice.BOND_BONDING) {
            // we initiated the bonding
            int btClass = mBluetoothService.getRemoteClass(address);

            // try 0000 once if the device looks dumb
            switch (BluetoothClass.Device.getDevice(btClass)) {
            case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
            case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                if (mBluetoothService.getBondState().getAttempt(address) < 1) {
                    mBluetoothService.getBondState().attempt(address);
                    mBluetoothService.setPin(address, BluetoothDevice.convertPinToBytes("0000"));
                    return;
                }
            }
        }
        Intent intent = new Intent(BluetoothIntent.PAIRING_REQUEST_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
    }

    private void onPasskeyAgentCancel(String address) {
        address = address.toUpperCase();
        mPasskeyAgentRequestData.remove(address);
        Intent intent = new Intent(BluetoothIntent.PAIRING_CANCEL_ACTION);
        intent.putExtra(BluetoothIntent.ADDRESS, address);
        mContext.sendBroadcast(intent, BLUETOOTH_ADMIN_PERM);
        mBluetoothService.getBondState().setBondState(address, BluetoothDevice.BOND_NOT_BONDED,
                                                      BluetoothDevice.UNBOND_REASON_AUTH_CANCELED);
    }

    private boolean onAuthAgentAuthorize(String address, String service, String uuid) {
        boolean authorized = false;
        if (service.endsWith("service_audio")) {
            BluetoothA2dp a2dp = new BluetoothA2dp(mContext);
            authorized = a2dp.getSinkPriority(address) > BluetoothA2dp.PRIORITY_OFF;
            if (authorized) {
                Log.i(TAG, "Allowing incoming A2DP connection from " + address);
            } else {
                Log.i(TAG, "Rejecting incoming A2DP connection from " + address);
            }
        } else {
            Log.i(TAG, "Rejecting incoming " + service + " connection from " + address);
        }
        return authorized;
    }

    private void onAuthAgentCancel(String address, String service, String uuid) {
        // We immediately response to DBUS Authorize() so this should not
        // usually happen
        log("onAuthAgentCancel(" + address + ", " + service + ", " + uuid + ")");
    }

    private void onGetRemoteServiceChannelResult(String address, int channel) {
        IBluetoothDeviceCallback callback = mGetRemoteServiceChannelCallbacks.get(address);
        if (callback != null) {
            try {
                callback.onGetRemoteServiceChannelResult(address, channel);
            } catch (RemoteException e) {}
            mGetRemoteServiceChannelCallbacks.remove(address);
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
