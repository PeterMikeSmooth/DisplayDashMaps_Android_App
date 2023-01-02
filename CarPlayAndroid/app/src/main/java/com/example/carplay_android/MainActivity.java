package com.example.carplay_android;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;


public class MainActivity extends AppCompatActivity {

    private BleService.BleBinder controlBle;
    private BleService bleService;
    private MyServiceConn myServiceConn;
    private Intent intent;

    private Button buttonScanNewDevice;
    private Button buttonConnectToOld;
    private ImageView imageViewBTStatus;
    private ImageView imageViewBleStatus;

    private ReceiverForBTStatus receiverForBTStatus;
    private LocalBroadcastManager localBroadcastManagerForBTStatus;
    private IntentFilter intentFilterForBTStatus;

    private BleDevice deviceOld;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();

        buttonConnectToOld.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(deviceOld == null){
                    CharSequence text = "No previous device";
                    int duration = Toast.LENGTH_SHORT;
                    Toast toast = Toast.makeText(getApplicationContext(), text, duration);
                    toast.show();
                }else{
                    controlBle.connectLeDevice(deviceOld);
                }
            }
        });

        buttonScanNewDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), BleScanPage.class);
                startActivity(intent);
            }
        });









    }

    private void init(){
        askPermission();
        initComponents();
        initBroadcastReceiver();
        initService();
    }

    private void initComponents(){
        buttonConnectToOld = findViewById(R.id.buttonConnectOld);
        buttonScanNewDevice = findViewById(R.id.buttonScanNew);
        imageViewBleStatus = findViewById(R.id.imageViewBleStatus);
        imageViewBTStatus = findViewById(R.id.imageViewBT);
    }

    private void askPermission(){
        String[] permissions = {
                "android.permission.BLUETOOTH",
                "android.permission.BLUETOOTH_ADMIN",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
        };
        requestPermissions(permissions, 200);
    }

    private void initBroadcastReceiver(){
        localBroadcastManagerForBTStatus = LocalBroadcastManager.getInstance(getApplicationContext());
        receiverForBTStatus = new ReceiverForBTStatus();
        intentFilterForBTStatus = new IntentFilter("BT");
        localBroadcastManagerForBTStatus.registerReceiver(receiverForBTStatus, intentFilterForBTStatus);
    }

    private void initService(){
        myServiceConn = new MyServiceConn();
        intent = new Intent(this, BleService.class);
        bindService(intent, myServiceConn, BIND_AUTO_CREATE);
        startService(intent);//bind the service
    }



    class MyServiceConn implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder){
            controlBle = (BleService.BleBinder)iBinder;
            bleService = controlBle.getService();
        }
        @Override
        public void onServiceDisconnected(ComponentName name){
        }
    }
    class ReceiverForBTStatus extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getBooleanExtra("BT",false)){
                imageViewBTStatus.setColorFilter(Color.GREEN);
            }else{
                imageViewBTStatus.setColorFilter(0x9c9c9c);
            }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(myServiceConn);
    }
}
