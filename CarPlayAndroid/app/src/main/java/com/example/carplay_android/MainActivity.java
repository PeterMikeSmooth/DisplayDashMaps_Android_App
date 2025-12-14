package com.example.carplay_android;

import static com.example.carplay_android.javabeans.JavaBeanFilters.getFILTER_BLE_STATUS;
import static com.example.carplay_android.javabeans.JavaBeanFilters.getFILTER_BT_STATUS;
import static com.example.carplay_android.javabeans.JavaBeanFilters.getFILTER_DEVICE_STATUS;
import static com.example.carplay_android.javabeans.JavaBeanFilters.getFILTER_DEVICE_USED;
import static com.example.carplay_android.javabeans.JavaBeanFilters.getFILTER_NOTIFICATION_STATUS;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.clj.fastble.data.BleDevice;
import com.example.carplay_android.services.BleService;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String LAST_DEVICE_MAC = "lastDeviceMac";

    private BleService.BleBinder controlBle;
    private ServiceConnToBLE serviceConnToBLE;

    private Button buttonOpenNotification;
    private Button buttonScanNewDevice;
    private Button buttonConnectToOld;
    private ImageView imageViewBTStatus;
    private ImageView imageViewBleStatus;
    private ImageView imageViewNotificationStatus;
    private ImageView imageViewDeviceStatus;
    private TextView deviceName;
    private TextView textViewNotificationAccess;

    private BleDevice deviceUsed;

    public static boolean isForeground = false;

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        checkInitialStatuses();
        loadLastDevice();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

        buttonOpenNotification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {//open the settings for turn on notification
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });

        buttonConnectToOld.setOnClickListener(new View.OnClickListener() {//connect to previous device
            @Override
            public void onClick(View view) {
                Log.d(TAG, "Connect to previous device button clicked.");
                SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
                String lastDeviceMac = sharedPreferences.getString(LAST_DEVICE_MAC, null);

                if (lastDeviceMac == null) {
                    Log.w(TAG, "No previous device to connect to.");
                    CharSequence text = "No previous device";
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(getApplicationContext(), text, duration);
                    toast.show();
                } else {
                    Log.i(TAG, "Connecting to device: " + lastDeviceMac);
                    if (controlBle != null) {
                        controlBle.connectLeDevice(lastDeviceMac);
                    }
                }
            }
        });

        buttonScanNewDevice.setOnClickListener(new View.OnClickListener() {//scan new device
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), BleScanPage.class);
                startActivity(intent);
            }
        });

    }

    private void init() {
        askPermission();
        initComponents();
        initBroadcastReceiver();
        initService();
    }

    private void initComponents() {
        buttonOpenNotification = findViewById(R.id.buttonNotification);
        buttonConnectToOld = findViewById(R.id.buttonConnectOld);
        buttonScanNewDevice = findViewById(R.id.buttonScanNew);
        imageViewBTStatus = findViewById(R.id.imageViewBT);
        imageViewBleStatus = findViewById(R.id.imageViewBleStatus);
        imageViewNotificationStatus = findViewById(R.id.imageViewNotification);
        imageViewDeviceStatus = findViewById(R.id.imageViewDevice);
        deviceName = findViewById(R.id.textViewDeviceName);
        textViewNotificationAccess = findViewById(R.id.textViewNotificationAccess);
    }

    private void loadLastDevice() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        String lastDeviceMac = sharedPreferences.getString(LAST_DEVICE_MAC, null);

        if (lastDeviceMac != null) {
            // The service will use the MAC address string to connect.
            String buttonText = "Connect to previous device" + "<br/><small>" + lastDeviceMac + "</small>";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                buttonConnectToOld.setText(Html.fromHtml(buttonText, Html.FROM_HTML_MODE_LEGACY));
            } else {
                buttonConnectToOld.setText(Html.fromHtml(buttonText));
            }
            buttonConnectToOld.setEnabled(true);
            Log.d(TAG, "Loaded last device: " + lastDeviceMac);
        } else {
            buttonConnectToOld.setText("Connect to previous device");
            buttonConnectToOld.setEnabled(false);
            Log.d(TAG, "No last device found.");
        }
    }


    private void askPermission() {
        String[] permissions = {
                "android.permission.BLUETOETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
        };
        requestPermissions(permissions, 200);
    }

    private void initBroadcastReceiver() {
        IntentFilter intentFilter;
        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());

        intentFilter = new IntentFilter(getFILTER_DEVICE_USED());
        ReceiverForDeviceUsed receiverForDeviceUsed = new ReceiverForDeviceUsed();
        localBroadcastManager.registerReceiver(receiverForDeviceUsed, intentFilter);

        intentFilter = new IntentFilter(getFILTER_BT_STATUS());
        ReceiverForBTStatus receiverForBTStatus = new ReceiverForBTStatus();
        localBroadcastManager.registerReceiver(receiverForBTStatus, intentFilter);

        intentFilter = new IntentFilter(getFILTER_BLE_STATUS());
        ReceiverForBleStatus receiverForBleStatus = new ReceiverForBleStatus();
        localBroadcastManager.registerReceiver(receiverForBleStatus, intentFilter);

        intentFilter = new IntentFilter(getFILTER_NOTIFICATION_STATUS());
        ReceiverForNotificationStatus receiverForNotificationStatus = new ReceiverForNotificationStatus();
        localBroadcastManager.registerReceiver(receiverForNotificationStatus, intentFilter);

        intentFilter = new IntentFilter(getFILTER_DEVICE_STATUS());
        ReceiverForDeviceStatus receiverForDeviceStatus = new ReceiverForDeviceStatus();
        localBroadcastManager.registerReceiver(receiverForDeviceStatus, intentFilter);
    }

    private void initService() {
        serviceConnToBLE = new ServiceConnToBLE();
        Intent intent = new Intent(this, BleService.class);
        startService(intent);//bind the service
        bindService(intent, serviceConnToBLE, BIND_AUTO_CREATE);
        requestIgnoreBatteryOptimizations();
    }

    private void checkInitialStatuses() {
        updateUIWithNotificationStatus(isNotificationServiceEnabled());
        if (controlBle != null) {
            controlBle.requestStatusUpdate();
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && cn.getPackageName().equals(pkgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateUIWithNotificationStatus(boolean hasNotificationAccess) {
        imageViewNotificationStatus.setActivated(hasNotificationAccess);
        if (hasNotificationAccess) {
            buttonScanNewDevice.setEnabled(true);
            buttonConnectToOld.setEnabled(true);
            textViewNotificationAccess.setVisibility(View.GONE);
        } else {
            buttonScanNewDevice.setEnabled(false);
            buttonConnectToOld.setEnabled(false);
            textViewNotificationAccess.setVisibility(View.VISIBLE);
        }
    }

    class ServiceConnToBLE implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            controlBle = (BleService.BleBinder) iBinder;
            checkInitialStatuses();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            initService();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void requestIgnoreBatteryOptimizations() {
        boolean isIgnored = false;
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            isIgnored = powerManager.isIgnoringBatteryOptimizations(getPackageName());
        }
        if (!isIgnored) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class ReceiverForDeviceUsed extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            deviceUsed = intent.getParcelableExtra(getFILTER_DEVICE_USED());
            if (deviceUsed != null) {
                SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(LAST_DEVICE_MAC, deviceUsed.getMac());
                editor.apply();
                Log.d(TAG, "Saved last device: " + deviceUsed.getMac());
                loadLastDevice(); // Refresh button state
            }
        }
    }

    class ReceiverForBTStatus extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            imageViewBTStatus.setActivated(intent.getBooleanExtra(getFILTER_BT_STATUS(), false));
        }
    }

    class ReceiverForBleStatus extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean bleStatus = intent.getBooleanExtra(getFILTER_BLE_STATUS(), false);
            imageViewBleStatus.setActivated(bleStatus);
            String errorMessage = intent.getStringExtra("BLE_ERROR_MESSAGE");
            if (errorMessage != null) {
                showStatusMessage("Bluetooth error: " + errorMessage);
            }
        }

        private void showStatusMessage(String message) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    class ReceiverForNotificationStatus extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUIWithNotificationStatus(intent.getBooleanExtra(getFILTER_NOTIFICATION_STATUS(), false));
        }
    }

    class ReceiverForDeviceStatus extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isConnected = intent.getBooleanExtra("status", false); // Correct key
            imageViewDeviceStatus.setActivated(isConnected);
            if (isConnected) {
                Log.d(TAG, "Device connected. Attempting to launch Google Maps.");
                Intent mapIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
                if (mapIntent != null) {
                    mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(mapIntent);
                } else {
                    Log.e(TAG, "Could not find Google Maps launch intent.");
                    Toast.makeText(context, "Google Maps not found.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnToBLE);
    }
}
