package com.example.carplay_android.services;

import static com.example.carplay_android.javabeans.JavaBeanFilters.*;

import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.exception.TimeoutException;
import com.example.carplay_android.MainActivity;
import com.example.carplay_android.utils.BroadcastUtils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class BleService extends Service {

    private static final String TAG = "BleService";
    private Timer timerBTState;
    private BleDevice bleDeviceConnectTo;
    private final Queue<Runnable> writeQueue = new LinkedList<>();
    private final AtomicBoolean isWriting = new AtomicBoolean(false);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new BleBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate called");
        setBTCheckTimer();
        BroadcastUtils.sendStatus(true, getFILTER_BLE_STATUS(), getApplicationContext());
    }

    public void setBTCheckTimer() { // Check the BT state every second
        if (timerBTState == null) {
            timerBTState = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    BleManager.getInstance().init(getApplication());
                    boolean status = false;
                    if (BleManager.getInstance().isSupportBle()) {
                        if (!BleManager.getInstance().isBlueEnable() && MainActivity.isForeground) {
                            BleManager.getInstance().enableBluetooth();
                            status = !BleManager.getInstance().isBlueEnable();
                        } else {
                            status = true;
                        }
                    }
                    BroadcastUtils.sendStatus(status, getFILTER_BT_STATUS(), getApplicationContext());
                }
            };
            timerBTState.schedule(timerTask, 10, 1000);
        }
    }

    private void sendConnectionStatus(boolean isConnected) {
        Intent intent = new Intent(getFILTER_DEVICE_STATUS());
        intent.putExtra("status", isConnected);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    private void processWriteQueue() {
        Log.d(TAG, "processWriteQueue called. isWriting: " + isWriting.get() + ", queue size: " + writeQueue.size());
        if (isWriting.get()) {
            Log.d(TAG, "Another write is in progress. Will not process queue now.");
            return;
        }
        if (writeQueue.isEmpty()) {
            Log.d(TAG, "Queue is empty.");
            return;
        }

        // Atomically set isWriting to true and check if it was false before
        if (isWriting.compareAndSet(false, true)) {
            Runnable writeOperation = writeQueue.poll();
            if (writeOperation != null) {
                Log.d(TAG, "Dequeuing and running write op. New queue size: " + writeQueue.size());
                writeOperation.run();
            } else {
                // This should not happen if the queue was not empty, but as a safeguard
                isWriting.set(false);
            }
        }
    }

    public class BleBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }

        public boolean isConnected() {
            return bleDeviceConnectTo != null && BleManager.getInstance().isConnected(bleDeviceConnectTo);
        }

        private void setMtu(BleDevice bleDevice) {
            BleManager.getInstance().setMtu(bleDevice, 200, new BleMtuChangedCallback() {
                @Override
                public void onSetMTUFailure(BleException exception) {
                    Log.e(TAG, "setMtu failed: " + exception.getDescription());
                }

                @Override
                public void onMtuChanged(int mtu) {
                    Log.d(TAG, "MTU changed to: " + mtu);
                }
            });
        }

        public void connectLeDevice(final BleDevice bleDevice) {
            connect(bleDevice);
        }

        public void connectLeDevice(final String mac) {
            BleManager.getInstance().connect(mac, new BleGattCallback() {
                @Override
                public void onStartConnect() {
                    Log.d(TAG, "Starting BLE connection with MAC...");
                }

                @Override
                public void onConnectFail(BleDevice bleDevice, BleException exception) {
                    Log.e(TAG, "Connect with MAC failed: " + getFailureMessage(exception));
                    sendConnectionStatus(false);
                }

                @Override
                public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                    Log.d(TAG, "Connect success for device: " + bleDevice.getName());
                    bleDeviceConnectTo = bleDevice;
                    setMtu(bleDevice);
                    NotificationService.cleanLastTimeSent();
                    sendConnectionStatus(true);
                    
                    // Broadcast the connected device
                    Intent intent = new Intent(getFILTER_DEVICE_USED());
                    intent.putExtra(getFILTER_DEVICE_USED(), bleDevice);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                }

                @Override
                public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                    Log.d(TAG, "Disconnected. isActive: " + isActiveDisConnected);
                    sendConnectionStatus(false);
                    // Clear queue on disconnect to prevent sending stale data on reconnect
                    writeQueue.clear();
                    isWriting.set(false);
                }
            });
        }

        private void connect(BleDevice bleDevice) {
            BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
                @Override
                public void onStartConnect() {
                    Log.d(TAG, "Starting BLE connection...");
                }

                @Override
                public void onConnectFail(BleDevice bleDevice, BleException exception) {
                    Log.e(TAG, "Connect failed: " + getFailureMessage(exception));
                    sendConnectionStatus(false);
                }

                @Override
                public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                    Log.d(TAG, "Connect success for device: " + bleDevice.getName());
                    bleDeviceConnectTo = bleDevice;
                    setMtu(bleDevice);
                    NotificationService.cleanLastTimeSent();
                    sendConnectionStatus(true);

                    // Broadcast the connected device
                    Intent intent = new Intent(getFILTER_DEVICE_USED());
                    intent.putExtra(getFILTER_DEVICE_USED(), bleDevice);
                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
                }

                @Override
                public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                    Log.d(TAG, "Disconnected. isActive: " + isActiveDisConnected);
                    sendConnectionStatus(false);
                    // Clear queue on disconnect to prevent sending stale data on reconnect
                    writeQueue.clear();
                    isWriting.set(false);
                }
            });
        }

        private String getFailureMessage(BleException exception) {
            if (exception instanceof TimeoutException) {
                return "Connection timed out.";
            } else if (exception != null) {
                return exception.getDescription();
            } else {
                return "Unknown error.";
            }
        }

        public void sendToDevice(String informationMessage, String uuid) {
            Log.d(TAG, "Queueing write op for UUID " + uuid + ". Queue size: " + writeQueue.size());
            writeQueue.add(() -> {
                if (!isConnected()) {
                    Log.e(TAG, "Device not connected, skipping write for UUID " + uuid);
                    isWriting.set(false);
                    // Don't process queue here, just stop this failed operation.
                    return;
                }
                Log.d(TAG, "Executing write for UUID: " + uuid + " message: " + informationMessage);
                String uuid_service = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
                byte[] data = informationMessage.getBytes();

                BleManager.getInstance().write(
                        bleDeviceConnectTo,
                        uuid_service,
                        uuid,
                        data,
                        true, // Use split writer
                        new BleWriteCallback() {
                            @Override
                            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                                Log.d(TAG, "onWriteSuccess for UUID: " + uuid + ". Progress: " + current + "/" + total);
                                // For split writes, we only unblock the queue when the last packet is sent.
                                if (current == total) {
                                    Log.d(TAG, "Final write success for UUID: " + uuid + ". Unblocking queue.");
                                    isWriting.set(false);
                                    processWriteQueue();
                                }
                            }

                            @Override
                            public void onWriteFailure(BleException exception) {
                                Log.e(TAG, "onWriteFailure for UUID: " + uuid + " - " + getFailureMessage(exception));
                                Log.d(TAG, "Unblocking queue after failure.");
                                isWriting.set(false);
                                processWriteQueue();
                            }
                        });
            });
            processWriteQueue();
        }

        public void sendNextStreet(String information) {
            String DESTINATION_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
            sendToDevice(information, DESTINATION_UUID);
        }

        public void sendEta(String information) {
            String ETA_UUID = "ca83fac2-2438-4d14-a8ae-a01831c0cf0d";
            sendToDevice(information, ETA_UUID);
        }

        public void sendDistanceToNextDir(String information) {
            String DIRECTION_UUID = "0343ff39-994e-481b-9136-036dabc02a0b";
            sendToDevice(information, DIRECTION_UUID);
        }

        public void sendEtaInMinutes(String information) {
            String ETA_DISTANCE_UUID = "563c187d-ff17-4a6a-8061-ca9b7b70b2b0";
            sendToDevice(information, ETA_DISTANCE_UUID);
        }

        public void sendDistance(String information) {
            String ETA_DISTANCE_UUID = "8bf31540-eb0d-476c-b233-f514678d2afb";
            sendToDevice(information, ETA_DISTANCE_UUID);
        }

        public void sendArrow(String information) {
            String DIRECTION_PRECISE_UUID = "a602346d-c2bb-4782-8ea7-196a11f85113";
            sendToDevice(information, DIRECTION_PRECISE_UUID);
        }

        public void requestStatusUpdate() {
            BroadcastUtils.sendStatus(BleManager.getInstance().isBlueEnable(), getFILTER_BT_STATUS(), getApplicationContext());
            BroadcastUtils.sendStatus(true, getFILTER_BLE_STATUS(), getApplicationContext()); // if service is running, it is ON
            sendConnectionStatus(isConnected());
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved called");
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
        if (timerBTState != null) {
            timerBTState.cancel();
            timerBTState = null;
        }
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service onDestroy called");
        BleManager.getInstance().disconnectAllDevice();
        if (timerBTState != null) {
            timerBTState.cancel();
            timerBTState = null;
        }
        BleManager.getInstance().destroy();
    }
}
