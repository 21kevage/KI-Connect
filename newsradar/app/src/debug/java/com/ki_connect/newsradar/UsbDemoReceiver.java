package com.ki_connect.newsradar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class UsbDemoReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.ki_connect.newsradar.USB_DEMO".equals(intent.getAction())) {
            setResultCode(RadarNotifications.showDemo(context) ? 1 : 2);
        }
    }
}
