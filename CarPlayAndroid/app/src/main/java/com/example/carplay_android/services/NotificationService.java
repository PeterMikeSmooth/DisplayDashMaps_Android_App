package com.example.carplay_android.services;
import android.text.SpannableString;
import android.graphics.drawable.Icon;


import static com.example.carplay_android.javabeans.JavaBeanFilters.*;

import static java.lang.Character.toUpperCase;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.carplay_android.MainActivity;
import com.example.carplay_android.R;
import com.example.carplay_android.utils.BroadcastUtils;
import com.example.carplay_android.utils.DirectionUtils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NotificationService extends NotificationListenerService {

    private BleService.BleBinder controlBle;
    private ServiceConnToBle serviceConnToBle;
    private static String[] informationMessageSentLastTime = new String[7];

    public NotificationService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        if (sbn != null && sbn.getPackageName().contains("com.google.android.apps.maps")) {
            if (isGMapNotification(sbn)) {
                handleGMapNotification(sbn);
            } else {
                String reason = "isOngoing: " + sbn.isOngoing() + ", id: " + sbn.getId();
                Log.w("NotificationService", "Not a valid GMap notification: " + reason);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        Log.d("Notification", "removed");
    }

    public static void cleanLastTimeSent() {
        Arrays.fill(informationMessageSentLastTime, "");
    }


    private boolean isGMapNotification(StatusBarNotification sbn) {
        if (!sbn.isOngoing() || !sbn.getPackageName().contains("com.google.android.apps.maps")) {
            return false;
        }
        return (sbn.getId() == 1);
    }

    private String removeAccentsAndSpecialCharacters(String input) {
        input = input.replace("ẞ", "SS").replace("ß", "SS").replace("ä", "AE").replace("Ä", "AE").replace("ö", "OE").replace("Ö", "OE").replace("ü", "UE").replace("Ü", "UE");
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "").replaceAll("[^a-zA-Z0-9 .',\\-]", "");
        return normalized.toUpperCase();
    }


    private void handleGMapNotification(StatusBarNotification sbn) {
        Bundle bundle = sbn.getNotification().extras;

        String[] informationMessage = new String[7];
        boolean isDataExtracted = false;

        String[] strings;
        String string = null;

        Object value = bundle.getCharSequence(Notification.EXTRA_TEXT);

        if (value instanceof SpannableString) {
            string = ((SpannableString) value).toString();
        } else if (value instanceof String) {
            string = (String) value;
        } else if (value != null) {
            string = value.toString();
        }
        if (string != null) {
            strings = string.split("-");
            informationMessage[0] = strings[0].trim();
            informationMessage[0] = removeAccentsAndSpecialCharacters(informationMessage[0]);
            isDataExtracted = true;
        }

        value = bundle.getCharSequence(Notification.EXTRA_TITLE);

        if (value instanceof SpannableString) {
            string = ((SpannableString) value).toString();
        } else if (value instanceof String) {
            string = (String) value;
        }

        if (string != null) {
            strings = string.split("-");
            if (strings.length == 2) {
                informationMessage[2] = strings[0].trim();
                informationMessage[3] = strings[1].trim().replace("\u00A0", " ");
            } else if (strings.length == 1) {
                informationMessage[2] = "Useless ?";
                informationMessage[3] = strings[0].trim().replace("\u00A0", " ");
            }
            isDataExtracted = true;
        }

        string = bundle.getString(Notification.EXTRA_SUB_TEXT);
        if (string != null) {
            strings = string.split("·");
            if (strings.length >= 3) {
                informationMessage[4] = strings[0].trim().replace("\u00A0", " ");
                informationMessage[5] = strings[1].trim().replace("\u00A0", " ");

                Pattern pattern = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
                Matcher matcher = pattern.matcher(strings[2]);
                if (matcher.find()) {
                    informationMessage[1] = matcher.group(1);
                } else {
                    informationMessage[1] = "00:00";
                }
                isDataExtracted = true;
            }
        }

        Icon largeIcon = sbn.getNotification().getLargeIcon();

        if (largeIcon != null) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) largeIcon.loadDrawable(getApplicationContext());
            if (bitmapDrawable != null) {
                informationMessage[6] = String.valueOf(DirectionUtils.getDirectionNumber(DirectionUtils.getDirectionByComparing(bitmapDrawable.getBitmap())));
                isDataExtracted = true;
            }
        }

        if (!isDataExtracted) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "GMap notification caught, but failed to extract data.", Toast.LENGTH_LONG).show());
            return;
        }

        if (controlBle != null && controlBle.isConnected()) {
            if (informationMessage[0] != null && !informationMessage[0].equals(informationMessageSentLastTime[0])) {
                controlBle.sendNextStreet(informationMessage[0]);
            }
            if (informationMessage[1] != null && !Objects.equals(informationMessage[1], informationMessageSentLastTime[1])) {
                controlBle.sendEta(informationMessage[1]);
            }
            if (informationMessage[3] != null && !Objects.equals(informationMessage[3], informationMessageSentLastTime[3])) {
                controlBle.sendDistanceToNextDir(informationMessage[3]);
            }
            if (informationMessage[4] != null && !Objects.equals(informationMessage[4], informationMessageSentLastTime[4])) {
                controlBle.sendEtaInMinutes(informationMessage[4]);
            }
            if (informationMessage[5] != null && !Objects.equals(informationMessage[5], informationMessageSentLastTime[5])) {
                controlBle.sendDistance(informationMessage[5]);
            }
            if (informationMessage[6] != null && !Objects.equals(informationMessage[6], informationMessageSentLastTime[6])) {
                controlBle.sendArrow(informationMessage[6]);
            }
            informationMessageSentLastTime = informationMessage;
        } else {
             Log.d("NotificationService", "Device not connected, not sending BLE data.");
             runOnUiThread(() -> Toast.makeText(getApplicationContext(), "GMap notification caught, but BLE device not connected.", Toast.LENGTH_LONG).show());
        }

    }

    private void runOnUiThread(Runnable r) {
        new android.os.Handler(getMainLooper()).post(r);
    }


    private void init() {
        Arrays.fill(informationMessageSentLastTime, "");
        initService();
        BroadcastUtils.sendStatus(true, getFILTER_NOTIFICATION_STATUS(), getApplicationContext());
        DirectionUtils.loadSamplesFromAsserts(getApplicationContext());
    }

    private void initService() {
        serviceConnToBle = new ServiceConnToBle();
        Intent intent = new Intent(this, BleService.class);
        bindService(intent, serviceConnToBle, BIND_AUTO_CREATE);
        startService(intent);
    }

    private class ServiceConnToBle implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            controlBle = (BleService.BleBinder) iBinder;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bindService(new Intent(NotificationService.this, BleService.class), serviceConnToBle, BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("NotificationService", "onDestroy");
        BroadcastUtils.sendStatus(false, getFILTER_NOTIFICATION_STATUS(), getApplicationContext());
        unbindService(serviceConnToBle);
    }
}
