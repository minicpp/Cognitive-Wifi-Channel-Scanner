package com.mobipi.wifi.myapplication;

import android.net.wifi.ScanResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * Created by wynter on 6/25/2015.
 */
public class ChannelMgr{
    private List<ChannelItem> channelItemList;
    FileManager fileMgr;
    ChannelItem prevItem;
    public ChannelMgr(FileManager fileMgr){
        channelItemList = new Vector<ChannelItem>();
        prevItem = null;
        this.fileMgr = fileMgr;
    }

    public void stop(){

        //write rest of records from memory to file
        ChannelItem item = null;
        for(Iterator<ChannelItem> it=channelItemList.iterator(); it.hasNext();){
            item = it.next();
            fileMgr.appendToRecordFile(item.toString());
            it.remove();
        }

        fileMgr.closeRecordFile();
        prevItem = null;
    }

    public void start(){
        fileMgr.reopenRecordFile();
        prevItem = null;
        fileMgr.appendToRecordFile(ChannelItem.getStringHeader());
    }

    public void addRecord(ScanResult result){
        ChannelItem currentItem = null;
        if(result == null)
            currentItem = _addNullRecord();
        else
            currentItem = _addRecord(result);

        if(prevItem != null && currentItem.channel != prevItem.channel){
            //write all items before prevItem (include) to file
            ChannelItem item = null;
            for(Iterator<ChannelItem> it=channelItemList.iterator(); it.hasNext();){
                item = it.next();
                if(item == currentItem){
                    break;
                }
                fileMgr.appendToRecordFile(item.toString());
                it.remove();
            }
        }
        prevItem = currentItem;
    }
    private ChannelItem _addNullRecord(){
        ChannelItem item = new ChannelItem();
        item.actualTimestamp = System.currentTimeMillis();
        if(channelItemList.size() > 0){
            item.channel = prevItem.channel;
            item.mac = prevItem.mac;
            item.ssid = prevItem.ssid;
            item.intervalToPrev = item.actualTimestamp - prevItem.actualTimestamp;
        }
        channelItemList.add(item);
        return item;
    }
    private ChannelItem _addRecord(ScanResult result){
        ChannelItem item = new ChannelItem();
        item.actualTimestamp = System.currentTimeMillis();
        item.hit = true;
        if(channelItemList.size() > 0){
            item.intervalToPrev = item.actualTimestamp - prevItem.actualTimestamp;
        }
        item.channel = MainActivity.getChannelFromFreq(result.frequency);
        item.freq = result.frequency;
        item.mac = result.BSSID;
        if(result.SSID != null && result.SSID.length()>0)
            item.ssid = result.SSID;
        item.rssi = result.level;
        item.givenTimestamp = result.timestamp;
        channelItemList.add(item);
        return item;
    }
}

class ChannelSummaryItem{
    public int channel;
    public int sampleSize;
    public int noFoundCounts;
    public int round;
    public int sumRSSI;
    public int duration;
}


class ChannelItem{
    public boolean hit=false;       //*
    public String mac="[null]";     //BSSID
    public String ssid="[null]";    //*
    public int rssi=0;          //*
    public int freq;            //*
    public int channel = 0;  //*
    public long givenTimestamp=0;
    public long actualTimestamp=0;  //*
    public long intervalToPrev=0;   //*
    public String toString(){
        StringBuffer buf = new StringBuffer();
        if(hit)
            buf.append("1, ");
        else
            buf.append("0, ");
        buf.append(mac+", ").append(ssid+", ").append(rssi+", ").append(freq+", ").append(channel+", ")
                .append(actualTimestamp+", ").append(givenTimestamp+", ").append(intervalToPrev+"\n");
        return buf.toString();
    }
    public static String getStringHeader(){
        StringBuffer buf = new StringBuffer();
        buf.append("hit, ").append("MAC, ").append("SSID, ").append("RSSI, ")
            .append("frequency, ").append("channel, ").append("Actual Timestamp (ms), ")
            .append("Given Timestamp (us), ").append("Interval to previous (ms)\n");    // 9 columns
        return buf.toString();
    }
}


