package com.example.carplay_android.services;
import android.text.SpannableString;
import android.graphics.drawable.Icon;


import static com.example.carplay_android.javabeans.JavaBeanFilters.*;

import static java.lang.Character.toUpperCase;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.carplay_android.utils.BroadcastUtils;
import com.example.carplay_android.utils.DirectionUtils;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NotificationService extends NotificationListenerService {

    private BleService.BleBinder controlBle;
    private ServiceConnToBle serviceConnToBle;
    private Timer timerSendNotification;
    private Boolean ifSendNotification = false;
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
                Toast.makeText(getApplicationContext(), "Not a valid GMap notification: " + reason, Toast.LENGTH_LONG).show();
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
        // Remplacer les caractères spécifiques avant la normalisation
        input = input.replace("ẞ", "SS")
                .replace("ß", "SS")
                .replace("ä", "AE")
                .replace("Ä", "AE")
                .replace("ö", "OE")
                .replace("Ö", "OE")
                .replace("ü", "UE")
                .replace("Ü", "UE");

        // Normalisation et suppression des accents et caractères spéciaux
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^a-zA-Z0-9 .',\\-]", "");

        return normalized.toUpperCase(); // Convertir en majuscules
    }


    private void handleGMapNotification(StatusBarNotification sbn) {
        Bundle bundle = sbn.getNotification().extras;

        for (String key : bundle.keySet()) {
            Log.d("1", key + " : " + bundle.get(key));
        }

        String[] informationMessage = new String[7];

        String[] strings;

        String string = null;

        Object value = bundle.get(Notification.EXTRA_TEXT);

        if (value instanceof SpannableString) {
            SpannableString spannableString = (SpannableString) value;
            string = spannableString.toString();
        } else if (value instanceof String) {
            string = (String) value;
        }
        if (string != null) {
            strings = string.split("-"); //destination
            informationMessage[0] = strings[0].trim();
            Log.d("1", "[0] Next street 1 = " + informationMessage[0]);
            informationMessage[0] = removeAccentsAndSpecialCharacters(informationMessage[0]);
            Log.d("1", "[0] Next street 2 = " + informationMessage[0]);
        } else {
            Log.d("1", "EXTRA_TEXT = null");
        }

        value = bundle.get(Notification.EXTRA_TITLE);

        if (value instanceof SpannableString) {
            SpannableString spannableString = (SpannableString) value;
            string = spannableString.toString();
        } else if (value instanceof String) {
            string = (String) value;
        }

        if (string != null) {
            strings = string.split("-");
            if (strings.length == 2) {
                informationMessage[2] = strings[0].trim();// Direction to somewhere
                informationMessage[3] = strings[1].trim().replace("\u00A0", " ");;// Distance to next direction
                Log.d("1", "[2] A - EXTRA_TITLE Useless = " + informationMessage[2]);
                Log.d("1", "[3] A - EXTRA_TITLE Direction to next dir = " + informationMessage[3]);
            } else if (strings.length == 1) {
                informationMessage[2] = "Useless ?";//Distance to next direction
                informationMessage[3] = strings[0].trim().replace("\u00A0", " ");;//Direction to somewhere
                Log.d("1", "[2] B - EXTRA_TITLE Useless = " + informationMessage[2]);
                Log.d("1", "[3] B - EXTRA_TITLE Direction to next dir = " + informationMessage[3]);
            }
        }
        else {
            Log.d("1", "EXTRA_TITLE = null");
        }

        string = bundle.getString(Notification.EXTRA_SUB_TEXT);
        if (string != null) {
            strings = string.split("·");
            if (strings.length >= 3) {
                informationMessage[4] = strings[0].trim().replace("\u00A0", " ");; // Minutes restantes
                informationMessage[5] = strings[1].trim().replace("\u00A0", " ");; // Distance

                // Utilisation d'une expression régulière pour extraire l'heure au format "88:88"
                Pattern pattern = Pattern.compile("\\b(\\d{1,2}:\\d{2})\\b");
                Matcher matcher = pattern.matcher(strings[2]);
                if (matcher.find()) {
                    informationMessage[1] = matcher.group(1); // L'heure extraite
                } else {
                    // Si l'heure au format "88:88" n'est pas trouvée, une valeur par défaut peut être attribuée
                    informationMessage[1] = "00:00";
                }

                Log.d("1", "[4] EXTRA_SUB_TEXT ETA en minutes = " + informationMessage[4]);
                Log.d("1", "[5] EXTRA_SUB_TEXT Distance = " + informationMessage[5]);
                Log.d("1", "[1] EXTRA_SUB_TEXT ETA = " + informationMessage[1]);
            } else {
                Log.d("1", "EXTRA_SUB_TEXT Le résultat de la division ne contient pas assez d'éléments");
            }
        } else {
            Log.d("1", "EXTRA_SUB_TEXT = null");
        }

        Icon largeIcon = sbn.getNotification().getLargeIcon();

        if (largeIcon != null) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) largeIcon.loadDrawable(getApplicationContext());
            if (bitmapDrawable != null) {
                informationMessage[6] = String.valueOf(DirectionUtils.getDirectionNumber(DirectionUtils.getDirectionByComparing(bitmapDrawable.getBitmap())));
                Log.d("1", "[6] Direction Arrow : " + informationMessage[6]);
            } else {
                Log.d("1", "BitmapDrawable is null");
            }
        } else {
            Log.d("1", "LargeIcon is null");
        }

        if (controlBle != null && controlBle.isConnected()) {
            if (informationMessage[0] != null && !informationMessage[0].equals(informationMessageSentLastTime[0])) {

                    controlBle.sendNextStreet(informationMessage[0]);
                informationMessageSentLastTime[0] = informationMessage[0];
            }
            if (informationMessage[1] != null && !Objects.equals(informationMessage[1], informationMessageSentLastTime[1])) {//ETA
                controlBle.sendEta(informationMessage[1]);
                informationMessageSentLastTime[1] = informationMessage[1];
            }
            /*if (informationMessage[2] != null && !Objects.equals(informationMessage[2], informationMessageSentLastTime[2])) {//direction
                if (informationMessage[2].length() > 20) {
                    controlBle.sendDirection(informationMessage[2].substring(0, 20) + "..");
                } else {
                    controlBle.sendDirection(informationMessage[2]);
                }
                informationMessageSentLastTime[2] = informationMessage[2];
            }*/
            if (informationMessage[3] != null && !Objects.equals(informationMessage[3], informationMessageSentLastTime[3])) {
                controlBle.sendDistanceToNextDir(informationMessage[3]);
                informationMessageSentLastTime[3] = informationMessage[3];
            }
            if (informationMessage[4] != null && !Objects.equals(informationMessage[4], informationMessageSentLastTime[4])) {
                controlBle.sendEtaInMinutes(informationMessage[4]);
                informationMessageSentLastTime[4] = informationMessage[4];
            }
            if (informationMessage[5] != null && !Objects.equals(informationMessage[5], informationMessageSentLastTime[5])) {
                controlBle.sendDistance(informationMessage[5]);
                informationMessageSentLastTime[5] = informationMessage[5];
            }
            if (informationMessage[6] != null && !Objects.equals(informationMessage[6], informationMessageSentLastTime[6])) {
                controlBle.sendArrow(informationMessage[6]);
                informationMessageSentLastTime[6] = informationMessage[6];
            }
            Log.d("d", "done");
            Log.d("d", " ");
            informationMessageSentLastTime = informationMessage;
            ifSendNotification = false;//reduce the frequency of sending messages
            //why not just check if two messages are the same,  why still need to send same message every half second:
            //because if the device lost connection before, we have to keep send message to it to keep it does not
            //receive any wrong message.
            //Toast.makeText(getApplicationContext(), "GMap notification sent via BLE", Toast.LENGTH_SHORT).show();
        } else {
             Log.d("NotificationService", "Device not connected, not sending BLE data.");
             Toast.makeText(getApplicationContext(), "GMap notification caught, but BLE device not connected.", Toast.LENGTH_LONG).show();
        }

    }


    private void init() {
        Arrays.fill(informationMessageSentLastTime, "");

        initService();
        setSendNotificationTimer();
        BroadcastUtils.sendStatus(true, getFILTER_NOTIFICATION_STATUS(), getApplicationContext());
        DirectionUtils.loadSamplesFromAsserts(getApplicationContext());
    }

    private void initService() {
        serviceConnToBle = new ServiceConnToBle();
        Intent intent = new Intent(this, BleService.class);
        bindService(intent, serviceConnToBle, BIND_AUTO_CREATE);
        startService(intent);//bind the service
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

    public void setSendNotificationTimer() {
        if (timerSendNotification == null) {
            timerSendNotification = new Timer();
            TimerTask timerTask = new TimerTask() {
                @Override
                public void run() {
                    ifSendNotification = true;
                }
            };
            timerSendNotification.schedule(timerTask, 10, 2000);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timerSendNotification != null) {
            timerSendNotification.cancel();
            timerSendNotification = null;
        }
        Log.d("NotificationService", "onDestroy");
        BroadcastUtils.sendStatus(false, getFILTER_NOTIFICATION_STATUS(), getApplicationContext());
        unbindService(serviceConnToBle);
    }
}
