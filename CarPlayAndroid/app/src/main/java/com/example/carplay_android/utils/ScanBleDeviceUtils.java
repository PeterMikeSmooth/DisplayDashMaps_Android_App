package com.example.carplay_android.utils;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.data.BleScanState;
import com.clj.fastble.scan.BleScanRuleConfig;

import java.util.ArrayList;
import java.util.List;

public class ScanBleDeviceUtils {
    private static final String TAG = "ScanBleDeviceUtils";
    private static final List<BleDevice> resultList = new ArrayList<>();

    public static void scanLeDevice(Context context) {
        Log.d(TAG, "scanLeDevice: starting scan process");

        if (!BleManager.getInstance().isBlueEnable()) {
            Log.e(TAG, "scanLeDevice: Bluetooth is not enabled.");
            Toast.makeText(context, "Veuillez activer le Bluetooth.", Toast.LENGTH_LONG).show();
            return;
        }
        Log.d(TAG, "scanLeDevice: Bluetooth is enabled.");

        if (BleManager.getInstance().getScanSate() == BleScanState.STATE_SCANNING) {
            Log.d(TAG, "scanLeDevice: Scan is already in progress, cancelling it.");
            BleManager.getInstance().cancelScan();
        }

        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
                .setScanTimeOut(10000)
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);

        Log.d(TAG, "scanLeDevice: Starting BLE scan.");
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                if (success) {
                    synchronized (resultList) {
                        resultList.clear();
                        BroadcastUtils.sendBleDevices(new ArrayList<>(resultList), "DeviceList", context);
                    }
                    Log.d(TAG, "onScanStarted: Scan started successfully.");
                    Toast.makeText(context, "Scan en cours...", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "onScanStarted: Failed to start scan.");
                    Toast.makeText(context, "Erreur au démarrage du scan", Toast.LENGTH_SHORT).show();
                }
            }

            private void addDevice(BleDevice bleDevice) {
                if (bleDevice == null || bleDevice.getMac() == null) {
                    return;
                }
                synchronized (resultList) {
                    boolean isNewDevice = true;
                    for (BleDevice device : resultList) {
                        if (device.getMac().equals(bleDevice.getMac())) {
                            isNewDevice = false;
                            break;
                        }
                    }
                    if (isNewDevice) {
                        if (bleDevice.getDevice() != null) {
                            Log.d(TAG, "addDevice: Found new device: " + bleDevice.getDevice().getName() + " - " + bleDevice.getMac());
                        }
                        resultList.add(bleDevice);
                        BroadcastUtils.sendBleDevices(new ArrayList<>(resultList), "DeviceList", context);
                    }
                }
            }

            @Override
            public void onLeScan(BleDevice bleDevice) {
                addDevice(bleDevice);
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                addDevice(bleDevice);
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                Log.d(TAG, "onScanFinished: Scan finished.");
                if (resultList.isEmpty()) {
                    Log.d(TAG, "onScanFinished: No devices found.");
                    Toast.makeText(context, "Aucun appareil trouvé.", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "onScanFinished: Found " + resultList.size() + " unique devices.");
                }
            }
        });
    }

    public static List<BleDevice> getResultList() {
        synchronized (resultList) {
            return new ArrayList<>(resultList);
        }
    }
}
