package com.example.carplay_android;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.clj.fastble.data.BleDevice;

import java.util.ArrayList;
import java.util.List;

public class LeDeviceListAdapter extends BaseAdapter {
    private List<BleDevice> bleDeviceLeDevices;
    private Context mContext;

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    public LeDeviceListAdapter(Context context){
        mContext = context;
        bleDeviceLeDevices = new ArrayList<>();
    }

    public void addDeviceList(List<BleDevice> devices) {
        bleDeviceLeDevices = devices;
    }

    public void clear() {
        if (bleDeviceLeDevices != null) {
            bleDeviceLeDevices.clear();
        }
    }

    @Override
    public int getCount() {
        if (bleDeviceLeDevices != null) {
            return bleDeviceLeDevices.size();
        }
        return 0;
    }

    @Override
    public Object getItem(int i) {
        return bleDeviceLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.device_information, viewGroup, false);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.addressForSingle);
            viewHolder.deviceName = (TextView) view.findViewById(R.id.nameForSingle);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BleDevice device = bleDeviceLeDevices.get(i);
        String deviceName = device.getName();

        if (deviceName != null && !deviceName.isEmpty()) {
            viewHolder.deviceName.setText(deviceName);
        } else {
            viewHolder.deviceName.setText("Appareil inconnu");
        }
        viewHolder.deviceAddress.setText(device.getMac());

        if ("Display Dash".equals(deviceName)) {
            viewHolder.deviceName.setTypeface(null, Typeface.BOLD);
            viewHolder.deviceAddress.setTypeface(null, Typeface.BOLD);
        } else {
            viewHolder.deviceName.setTypeface(null, Typeface.NORMAL);
            viewHolder.deviceAddress.setTypeface(null, Typeface.NORMAL);
        }

        return view;
    }
}
