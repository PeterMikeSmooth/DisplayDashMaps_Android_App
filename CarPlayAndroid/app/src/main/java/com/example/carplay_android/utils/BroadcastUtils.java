package com.example.carplay_android.utils;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.clj.fastble.data.BleDevice;
import com.example.carplay_android.javabeans.JavaBeanDevice;

import java.util.List;

public class BroadcastUtils {
    public static void sendStatus(boolean status, String filter, Context context) {
        sendBroadcast(status, filter, context);
    }

    public static void sendStatus(boolean status, String filter, Context context, String errorMessage) {
        Intent intent = new Intent(filter);
        intent.putExtra("status", status);
        intent.putExtra("errorMessage", errorMessage);
        context.sendBroadcast(intent);
    }
    public static void sendBleDevice(BleDevice bleDevice, String filter, Context context) {
        sendBroadcast(bleDevice, filter, context);
    }

    public static void sendBleDevices(List<BleDevice> bleDevices, String filter, Context context) {
        sendBroadcast(bleDevices, filter, context);
    }

    public static void sendBroadcast(Object object, String filter, Context context) {
        Intent intent = new Intent();
        intent.setAction(filter);
        if (object instanceof Boolean) {
            intent.putExtra(filter, (Boolean) object);
        } else if ((object instanceof BleDevice) || (object instanceof List)) {
            JavaBeanDevice javaBeanDevice = new JavaBeanDevice();
            if (object instanceof BleDevice) {
                javaBeanDevice.setBleDevice((BleDevice) object);
            } else if (object instanceof List) {
                try {
                    @SuppressWarnings("unchecked")
                    List<BleDevice> bleDeviceList = (List<BleDevice>) object;
                    javaBeanDevice.setBleDeviceList(bleDeviceList);
                } catch (ClassCastException e) {
                    e.printStackTrace(); // ou gérer l'exception comme vous le souhaitez
                    return; // ou une autre action pour éviter de poursuivre avec un cast incorrect
                }
            }
            intent.putExtra(filter, javaBeanDevice);
        }
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(context);
        localBroadcastManager.sendBroadcast(intent);
    }
}

