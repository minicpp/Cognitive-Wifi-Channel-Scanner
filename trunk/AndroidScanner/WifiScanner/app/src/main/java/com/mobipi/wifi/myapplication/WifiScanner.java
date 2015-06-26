package com.mobipi.wifi.myapplication;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;
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
    WifiStateChange mWifiStateChange;
    boolean mRunning = false;
    WifiManager.WifiLock lock;
    public WifiScanner(MainActivity context){
        mContext = context;
    }
    public void init(){
        mWifiMgr = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        lock = mWifiMgr.createWifiLock(WifiManager.WIFI_MODE_SCAN_ONLY, "wifirssi_scanOnly");
        lock.setReferenceCounted(false);
        mReceiver = new WifiReceiver();
        mWifiStateChange = new WifiStateChange();
        mContext.registerReceiver(mWifiStateChange, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
    }

    public void release(){
        stop();
        mContext.unregisterReceiver(mWifiStateChange);
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
            lock.acquire();
            mWifiMgr.startScan();
        }
    }

    public void stop(){
        if(mRunning) {
            mContext.unregisterReceiver(mReceiver);
            mRunning = false;
            lock.release();
        }
    }

    class WifiReceiver extends BroadcastReceiver {

        public void onReceive(Context c, Intent intent) {
            mWifiList = mWifiMgr.getScanResults();
            mContext.wifiScanCallback(mWifiList);
            Log.d(MainActivity.LOG_TAG, "start scan again");
            mWifiMgr.startScan();
        }

    }

    class WifiStateChange extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            int wifiState = mWifiMgr.getWifiState();
            switch(wifiState){
                case WifiManager.WIFI_STATE_DISABLED:
                    Log.d(MainActivity.LOG_TAG,"WIFI_STATE_DISABLED");
                    break;
                case WifiManager.WIFI_STATE_DISABLING:
                    Log.d(MainActivity.LOG_TAG,"WIFI_STATE_DISABLING");
                    break;
                case WifiManager.WIFI_STATE_ENABLED:
                    Log.d(MainActivity.LOG_TAG,"WIFI_STATE_ENABLED");
                    break;
                case WifiManager.WIFI_STATE_ENABLING:
                    Log.d(MainActivity.LOG_TAG,"WIFI_STATE_ENABLING");
                    break;
                case WifiManager.WIFI_STATE_UNKNOWN:
                    Log.d(MainActivity.LOG_TAG,"WIFI_STATE_UNKNOWN");
                    break;
                default:
                    Log.d(MainActivity.LOG_TAG,"NOT COVERED STATE:"+wifiState);
            }
        }
    }
}

