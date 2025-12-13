package com.example.carplay_android;

import static com.example.carplay_android.javabeans.JavaBeanFilters.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.data.BleDevice;
import com.example.carplay_android.services.BleService;
import com.example.carplay_android.utils.BroadcastUtils;
import com.example.carplay_android.utils.ScanBleDeviceUtils;

import java.util.ArrayList;

public class BleScanPage extends AppCompatActivity {

    private static final String TAG = "BleScanPage";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private Button buttonScan;
    private Button buttonConnect;
    private ListView bleList;
    private TextView deviceName;
    private TextView deviceAddress;


    private LeDeviceListAdapter leDeviceListAdapter = new LeDeviceListAdapter(this);

    private BleService.BleBinder controlBle;
    private ServiceConnBle serviceConnBle;

    private BleDevice deviceSelected;

    private ReceiverForConnectionStatus receiverForConnectionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_scan_page);
        
        init();

        checkPermissionsAndScan();

        bleList.setAdapter(leDeviceListAdapter);
        bleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                TextView textView = view.findViewById(R.id.addressForSingle);
                if(textView.getText() != deviceAddress.getText()){
                    deviceAddress.setText(textView.getText());
                    textView = view.findViewById(R.id.nameForSingle);
                    deviceName.setText(textView.getText());
                    deviceSelected = ScanBleDeviceUtils.getResultList().get(i);
                }else{
                    connectDevice();
                }
            }
        });

        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermissionsAndScan();
            }
        });

        buttonConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectDevice();
            }
        });
    }

    private void checkPermissionsAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null || (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
                new AlertDialog.Builder(this)
                        .setTitle("Enable Location")
                        .setMessage("To scan for Bluetooth devices, the application needs you to enable location services.")
                        .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }

            ArrayList<String> permissionsToRequest = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
                }
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
                }
            }
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }

            if (!permissionsToRequest.isEmpty()) {
                requestPermissions(permissionsToRequest.toArray(new String[0]), REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                ScanBleDeviceUtils.scanLeDevice(getApplicationContext());
            }
        } else {
            ScanBleDeviceUtils.scanLeDevice(getApplicationContext());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int grantResult : grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }

            if (allGranted) {
                ScanBleDeviceUtils.scanLeDevice(getApplicationContext());
            } else {
                Toast.makeText(this, "Permissions are required to scan for Bluetooth devices.", Toast.LENGTH_LONG).show();
            }
        }
    }


    private void init(){
        initComponents();
        initBroadcastReceiver();
        initService();
    }

    private void initComponents(){
        buttonScan = findViewById(R.id.buttonNotification);
        buttonConnect = findViewById(R.id.buttonConnectOld);
        bleList = findViewById(R.id.deviceList);
        deviceAddress = findViewById(R.id.deviceAddress);
        deviceName = findViewById(R.id.deviceName);
    }

    private void initBroadcastReceiver(){
        LocalBroadcastManager localBroadcastManagerForScanning = LocalBroadcastManager.getInstance(getApplicationContext());
        ReceiverForScanning receiverForScanning = new ReceiverForScanning();
        IntentFilter intentFilterForScanning = new IntentFilter(getFilterDeviceList());
        localBroadcastManagerForScanning.registerReceiver(receiverForScanning, intentFilterForScanning);

        Log.d(TAG, "Registering connection status receiver.");
        receiverForConnectionStatus = new ReceiverForConnectionStatus();
        IntentFilter intentFilterForConnection = new IntentFilter(getFILTER_DEVICE_STATUS());
        LocalBroadcastManager.getInstance(this).registerReceiver(receiverForConnectionStatus, intentFilterForConnection);
    }

    private void initService(){
        serviceConnBle = new ServiceConnBle();
        Intent intent = new Intent(this, BleService.class);
        bindService(intent, serviceConnBle, BIND_AUTO_CREATE);
        startService(intent);//bind the service
    }

    private void connectDevice(){//try to connect a device
        if(deviceSelected == null){
            Toast.makeText(getApplicationContext(), "No device selected", Toast.LENGTH_SHORT).show();
        }else{
            Log.d(TAG, "Attempting to connect to device: " + deviceSelected.getMac());
            controlBle.connectLeDevice(deviceSelected);
        }
    }

    private class ServiceConnBle implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder){
            controlBle = (BleService.BleBinder)iBinder;
        }
        @Override
        public void onServiceDisconnected(ComponentName name){
        }
    }

    private class ReceiverForScanning extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            leDeviceListAdapter.addDeviceList(ScanBleDeviceUtils.getResultList());
            leDeviceListAdapter.notifyDataSetChanged();
        }
    }

    private class ReceiverForConnectionStatus extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Connection status broadcast received.");
            boolean isConnected = intent.getBooleanExtra("status", false);
            Log.d(TAG, "isConnected: " + isConnected);

            if (isConnected) {
                Toast.makeText(BleScanPage.this, "Connected", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Attempting to launch Google Maps.");
                Intent mapIntent = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.maps");
                if (mapIntent != null) {
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
        unbindService(serviceConnBle);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiverForConnectionStatus);
    }
}
