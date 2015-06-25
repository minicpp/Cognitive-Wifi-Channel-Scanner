package com.mobipi.wifi.myapplication;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import java.util.List;

/**
 * Created by wynter on 6/24/2015.
 */
public class WifiScanner {
    MainActivity mContext;
    WifiManager mWifiMgr;
    List<ScanResult> mWifiList;
    WifiReceiver mReceiver;
    boolean mRunning = false;
    public WifiScanner(MainActivity context){
        mContext = context;
    }
    public void init(){
        mWifiMgr = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mReceiver = new WifiReceiver();
    }

    public boolean isRun(){
        return mRunning;
    }

    public void run(){
        /*
        We consider have set the wifi in scanning mode
         */
        if(!mRunning) {
            mContext.registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            mRunning = true;
            mWifiMgr.startScan();
        }
    }

    public void stop(){
        if(mRunning) {
            mContext.unregisterReceiver(mReceiver);
            mRunning = false;
        }
    }

    class WifiReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            mWifiList = mWifiMgr.getScanResults();
            mContext.wifiScanCallback(mWifiList);
            mWifiMgr.startScan();
        }

    }
}

