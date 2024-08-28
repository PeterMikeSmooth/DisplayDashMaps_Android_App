package com.example.carplay_android.services;

import static com.example.carplay_android.javabeans.JavaBeanFilters.*;

import android.app.ActivityManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.exception.TimeoutException;
import com.example.carplay_android.MainActivity;
import com.example.carplay_android.utils.BroadcastUtils;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class BleService extends Service {

    private Timer timerBTState;
    private BleDevice bleDeviceConnectTo;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new BleBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("BleService", "Service onCreate called");
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
                            // Check again to see if BT is enabled
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

    private boolean isAppRunning(String packageName) {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(packageName)) {
                return true; // The app is running
            }
        }
        return false; // The app is not running
    }

    private boolean isAppInForeground(String packageName) {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> appList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60 * 60, time);
        if (appList != null && !appList.isEmpty()) {
            UsageStats recentStats = null;
            for (UsageStats usageStats : appList) {
                if (usageStats.getPackageName().equals(packageName)) {
                    if (recentStats == null || usageStats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                        recentStats = usageStats;
                    }
                }
            }
            return recentStats != null && recentStats.getLastTimeUsed() > time - 1000;
        }
        return false;
    }

    public class BleBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }

        public void setMtu(BleDevice bleDevice) {
            BleManager.getInstance().setMtu(bleDevice, 200, new BleMtuChangedCallback() {
                @Override
                public void onSetMTUFailure(BleException exception) {
                    Log.d("BleService", "MTUFailed: " + exception.getDescription());
                }

                @Override
                public void onMtuChanged(int mtu) {
                    Log.d("BleService", "MTU changed to: " + mtu);
                }
            });
        }

        public void connectLeDevice(BleDevice bleDevice) {
            BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
                @Override
                public void onStartConnect() {
                    Log.d("BleService", "Starting BLE connection...");
                }

                @Override
                public void onConnectFail(BleDevice bleDevice, BleException exception) {
                    Log.d("BleService", "Connect failed: " + getFailureMessage(exception));
                    BroadcastUtils.sendStatus(false, getFILTER_DEVICE_STATUS(), getApplicationContext());
                }

                @Override
                public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                    Log.d("BleService", "Connect success");
                    BroadcastUtils.sendStatus(true, getFILTER_DEVICE_STATUS(), getApplicationContext());
                    bleDeviceConnectTo = bleDevice;
                    NotificationService.cleanLastTimeSent();
                }

                @Override
                public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                    Log.d("BleService", "Disconnected with status: " + status);
                    BroadcastUtils.sendStatus(false, getFILTER_DEVICE_STATUS(), getApplicationContext());
                    connectLeDevice(bleDeviceConnectTo); // Attempt to reconnect
                }
            });
        }

        private String getFailureMessage(BleException exception) {
            if (exception instanceof TimeoutException) {
                return "Connection timed out. Please ensure the device is powered on and in range.";
            } else if (exception != null) {
                return "Bluetooth error occurred: " + exception.getDescription();
            } else {
                return "An unknown error occurred. Please try again.";
            }
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

        private void sendToDevice(String informationMessage, String uuid) {
            String uuid_service = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
            byte[] data = informationMessage.getBytes();

            BleManager.getInstance().write(
                    bleDeviceConnectTo,
                    uuid_service,
                    uuid,
                    data,
                    false,
                    new BleWriteCallback() {
                        @Override
                        public void onWriteSuccess(int current, int total, byte[] justWrite) {
                            Log.d("BleService", "Success to send");
                        }

                        @Override
                        public void onWriteFailure(BleException exception) {
                            Log.d("BleService", "Failed to send: " + getFailureMessage(exception));
                            new Handler(Looper.getMainLooper()).postDelayed(() -> sendToDevice(informationMessage, uuid), 100);
                        }
                    });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("BleService", "Service onDestroy called");
        if (timerBTState != null) {
            timerBTState.cancel();
            timerBTState = null;
        }
        BroadcastUtils.sendStatus(false, getFILTER_BLE_STATUS(), getApplicationContext());
    }
}
