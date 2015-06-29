package com.mobipi.wifi.myapplication;

import android.net.wifi.ScanResult;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/**
 * Created by wynter on 6/25/2015.
 */
public class ChannelMgr {
    private List<ChannelItem> channelItemList;
    private List<ChannelSummaryItem> channelSummaryList;
    FileManager fileMgr;
    ChannelItem prevItem;
    StringBuffer strBuffer = new StringBuffer();

    public List<ChannelSummaryItem> getChannelSummaryItemList(){
        return channelSummaryList;
    }

    public ChannelMgr(FileManager fileMgr) {
        channelItemList = new Vector<ChannelItem>();
        channelSummaryList = new Vector<ChannelSummaryItem>();
        prevItem = null;
        this.fileMgr = fileMgr;
    }

    public void stop() {

        //write rest of records from memory to file
        ChannelItem item = null;
        for (Iterator<ChannelItem> it = channelItemList.iterator(); it.hasNext(); ) {
            item = it.next();
            fileMgr.appendToRecordFile(item.toString());
            it.remove();
        }

        fileMgr.closeRecordFile();
        prevItem = null;
    }

    public void start() {
        fileMgr.reopenRecordFile();
        prevItem = null;
        fileMgr.appendToRecordFile(ChannelItem.getStringHeader());
        if (channelSummaryList.size() == 0)
            for (int i = 0; i < 11; ++i) {
                channelSummaryList.add(new ChannelSummaryItem(i + 1));
            }
        else
            for (int i = 0; i < 11; ++i) {
                channelSummaryList.set(i, new ChannelSummaryItem(i + 1));
            }
    }

    private void _debugStatistics(ChannelItem sitem){
        double rssiSum = 0;
        double mean = 0;
        double stddev = 0;
        ChannelItem item = null;
        double totalHit = 0;
        for(Iterator<ChannelItem> it = channelItemList.iterator(); it.hasNext(); ) {
            item = it.next();
            if(item.hit && item.channel == sitem.channel) {
                rssiSum += item.rssi;
                totalHit += 1;
            }
        }
        if(totalHit > 0)
            mean = rssiSum / totalHit;
        double sqSum = 0;
        for(Iterator<ChannelItem> it = channelItemList.iterator(); it.hasNext(); ) {
            item = it.next();
            if(item.hit && item.channel == sitem.channel)
                sqSum += (item.rssi - mean)*(item.rssi-mean);
        }
        if(totalHit > 1)
            stddev = Math.sqrt(sqSum/(totalHit-1.0));
        Log.d(MainActivity.LOG_TAG, "mean: " + mean + ", stddev: " + stddev);
    }

    public void addRecord(ScanResult result, long deltaTime) {
        ChannelItem currentItem = null;
        if (result == null)
            currentItem = _addNullRecord();
        else
            currentItem = _addRecord(result);

        if (prevItem != null && currentItem.channel != prevItem.channel) {
            //write all items before prevItem (include) to file
            //_debugStatistics(prevItem);
            ChannelItem item = null;
            for (Iterator<ChannelItem> it = channelItemList.iterator(); it.hasNext(); ) {
                item = it.next();
                if (item == currentItem) {
                    break;
                }
                fileMgr.appendToRecordFile(item.toString());
                it.remove();
            }
        }

        //process summary
        if (currentItem.channel >= 1) {
            int position = currentItem.channel - 1;
            ChannelSummaryItem item = channelSummaryList.get(position);

            if (!currentItem.hit)
                ++item.noFoundCounts;
            else
                ++item.sampleSize;
            if (prevItem == null || prevItem.channel != currentItem.channel) {
                ++item.round;
                item.oldDuration += item.newDuration;
                item.newDuration = deltaTime;
            } else
                item.newDuration = deltaTime;
            if(currentItem.hit) {
                item.sumRSSI += currentItem.rssi;
                if (item.sampleSize > 0) {
                    item.meanPrev = item.mean;
                    item.mean = (double) item.sumRSSI / (double) item.sampleSize;
                }
                if (item.sampleSize > 1) {
                    item.sCurrent = item.sCurrent +
                            ((double) currentItem.rssi - item.meanPrev) * ((double) currentItem.rssi - item.mean);
                    item.variation = item.sCurrent / (double) (item.sampleSize - 1);
                }
            }

        }
        prevItem = currentItem;
    }

    private ChannelItem _addNullRecord() {
        ChannelItem item = new ChannelItem();
        item.actualTimestamp = System.currentTimeMillis();
        if (channelItemList.size() > 0) {
            item.channel = prevItem.channel;
            item.mac = prevItem.mac;
            item.ssid = prevItem.ssid;
            item.intervalToPrev = item.actualTimestamp - prevItem.actualTimestamp;
        }
        channelItemList.add(item);
        return item;
    }

    private ChannelItem _addRecord(ScanResult result) {
        ChannelItem item = new ChannelItem();
        item.actualTimestamp = System.currentTimeMillis();
        item.hit = true;
        if (channelItemList.size() > 0) {
            item.intervalToPrev = item.actualTimestamp - prevItem.actualTimestamp;
        }
        item.channel = MainActivity.getChannelFromFreq(result.frequency);
        item.freq = result.frequency;
        item.mac = result.BSSID;
        if (result.SSID != null && result.SSID.length() > 0)
            item.ssid = result.SSID;
        item.rssi = result.level;
        item.givenTimestamp = result.timestamp;
        channelItemList.add(item);
        return item;
    }

    public String toString() {
        strBuffer.setLength(0);
        if (channelSummaryList == null || channelSummaryList.size() == 0)
            return "There is no data at this time";
        for (ChannelSummaryItem item : channelSummaryList) {
            strBuffer.append(item.toString()).append("\n");
        }
        return strBuffer.toString();
    }

    public void saveChannelSummary(String profileName) {
        ChannelSummaryCollector obj = new ChannelSummaryCollector();
        obj.profileName = profileName;
        obj.channelSummaryList = channelSummaryList;
        fileMgr.writeChannelSummaryCollectorObject(profileName, obj);
    }

    public ChannelSummaryCollector readChannelSummary(String folderName) {
        String folderPath = fileMgr.getDataPath() + "/" + folderName;
        Object obj = fileMgr.readChannelSummaryCollectorObject(folderPath);
        if (obj == null)
            return null;
        return (ChannelSummaryCollector) obj;
    }

    public List<String> getHistoryRecords(){
        return fileMgr.getFolderListInDataPath();
    }
}

class ChannelSummaryCollector {
    public String profileName;
    public int version=3;
    public List<ChannelSummaryItem> channelSummaryList;



    public void writeObject(ObjectOutputStream out) {
        try {
            out.writeInt(version);
            out.writeObject(profileName);
            out.writeInt(channelSummaryList.size());
            for (ChannelSummaryItem item : channelSummaryList) {
                item.writeObject(out);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ChannelSummaryCollector readObject(ObjectInputStream ino) {
        try {
            version = 1;
            int v = ino.readInt();
            version = v;
            profileName = (String)ino.readObject();
            int size = ino.readInt();
            channelSummaryList = new Vector<ChannelSummaryItem>();
            for (int i = 0; i < size; ++i) {
                ChannelSummaryItem item = new ChannelSummaryItem(0);
                item.readObject(ino);
                channelSummaryList.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return this;
    }

    public ChannelSummaryCollector readObject_v1(ObjectInputStream ino) {
        try {
            version = 1;
            profileName = (String) ino.readObject();
            int size = ino.readInt();
            channelSummaryList = new Vector<ChannelSummaryItem>();
            for (int i = 0; i < size; ++i) {
                ChannelSummaryItem item = new ChannelSummaryItem(0);
                item.readObject_v1(ino);
                channelSummaryList.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return this;
    }

    public void update_v2_to_v3(FileManager fileMgr) {
        update_v1_to_v2(fileMgr);
        version = 3;
    }

    public void update_v1_to_v2(FileManager fileMgr){
        version = 2;
        File bin = new File(fileMgr.getDataPath()+"/"+this.profileName+"/record.csv");
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(bin));
            String line;
            int lineNumber = 0;
            for (int i = 0; i < 11; ++i) {
                ChannelSummaryItem item = channelSummaryList.get(i);
                item.sampleSize = 0;
                item.sumRSSI = 0;

                item.sCurrent = 0;
                item.mean = 0;
                item.meanPrev = 0;
                item.variation = 0;
            }
            while ((line = br.readLine()) != null) {
                // process the line.
                ++lineNumber;
                if(lineNumber == 1)
                    continue;
                String lineArray[] = line.split(",");
                int hit = Integer.parseInt(lineArray[0].trim());
                int channel = Integer.parseInt(lineArray[5].trim());
                int rssi = Integer.parseInt(lineArray[3].trim());
                if (channel >= 1) {
                    int position = channel - 1;
                    ChannelSummaryItem item = channelSummaryList.get(position);

                    if (hit == 1) {
                        ++item.sampleSize;

                        item.sumRSSI += rssi;
                        if (item.sampleSize > 0) {
                            item.meanPrev = item.mean;
                            item.mean = (double) item.sumRSSI / (double) item.sampleSize;
                        }
                        if (item.sampleSize > 1) {
                            item.sCurrent = item.sCurrent +
                                    ((double) rssi - item.meanPrev) * ((double) rssi - item.mean);
                            item.variation = item.sCurrent / (double) (item.sampleSize - 1);
                        }
                    }
                }
            }
            br.close();
        }
        catch(Exception e){
            try {
                if(br != null)
                    br.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return;
    }

    public String toString(){
        StringBuffer buffer = new StringBuffer();
        buffer.append("(v ").append(version).append(" )");
        buffer.append("Profile Name: ").append(profileName).append("\n");
        for(ChannelSummaryItem item:channelSummaryList){
            buffer.append(item.toString()).append("\n");
        }
        return buffer.toString();
    }
}

class ChannelSummaryItem {
    public int channel; //*
    public int sampleSize;  //*
    public int noFoundCounts;   //*
    public int round;   //*
    public int sumRSSI;
    public long newDuration;
    public long oldDuration;

    public double sCurrent=0;
    public double meanPrev=0;
    public double variation=0;
    public double mean = 0;

    public StringBuffer strBuffer = new StringBuffer();

    public double getConfidenceInterval95Upper(){
        return mean + 1.96*getStdDev()/Math.sqrt(sampleSize);
    }

    public double getConfidenceInterval95Lower(){
        return mean - 1.96*getStdDev()/Math.sqrt(sampleSize);
    }

    public double getStdDev(){
        return Math.sqrt(variation);
    }

    public double getVariation(){
        return variation;
    }

    public void writeObject(ObjectOutputStream out) {
        try {
            out.writeInt(channel);
            out.writeInt(sampleSize);
            out.writeInt(noFoundCounts);
            out.writeInt(round);
            out.writeInt(sumRSSI);
            out.writeLong(newDuration);
            out.writeLong(oldDuration);

            out.writeDouble(variation);
            out.writeDouble(mean);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ChannelSummaryItem readObject(ObjectInputStream ino) {
        try {
            channel = ino.readInt();
            sampleSize = ino.readInt();
            noFoundCounts = ino.readInt();
            round = ino.readInt();
            sumRSSI = ino.readInt();
            newDuration = ino.readLong();
            oldDuration = ino.readLong();

            variation = ino.readDouble();
            mean = ino.readDouble();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public ChannelSummaryItem readObject_v1(ObjectInputStream ino) {
        try {
            channel = ino.readInt();
            sampleSize = ino.readInt();
            noFoundCounts = ino.readInt();
            round = ino.readInt();
            sumRSSI = ino.readInt();
            newDuration = ino.readLong();
            oldDuration = ino.readLong();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    public ChannelSummaryItem(int channel) {
        this.channel = channel;
        this.round = 0;
        this.newDuration = 0;
        this.oldDuration = 0;
    }

    public double getAverageRSSI() {
        return mean;
    }

    public double getDetectRatio() {
        if (sampleSize + noFoundCounts == 0)
            return 0;
        double res = (double) sampleSize / (double) (sampleSize + noFoundCounts);
        return res;
    }

    public long getDuration() {
        return newDuration + oldDuration;
    }

    public String toString() {
        strBuffer.setLength(0);
        strBuffer.append("Channel ").append(channel).append(":\t Average RSSI: ").append(String.format("%.2f", getAverageRSSI()))
                .append(String.format(", Standard Deviation: %.2f", getStdDev()))
                .append("\n\t\tSample size: ").append(sampleSize).append(", Missed: ").append(noFoundCounts)
                .append(", Detected ratio:").append(String.format("%.2f", getDetectRatio() * 100)).append("%")
                .append("\n\t\tScan round: ").append(round)
                .append(", Total duration: ").append(MainActivity.formatElapseTime(getDuration()));
        return strBuffer.toString();
    }

}


class ChannelItem {
    public boolean hit = false;       //*
    public String mac = "[null]";     //BSSID
    public String ssid = "[null]";    //*
    public int rssi = 0;          //*
    public int freq;            //*
    public int channel = 0;  //*
    public long givenTimestamp = 0;
    public long actualTimestamp = 0;  //*
    public long intervalToPrev = 0;   //*

    public String toString() {
        StringBuffer buf = new StringBuffer();
        if (hit)
            buf.append("1, ");
        else
            buf.append("0, ");
        buf.append(mac + ", ").append(ssid + ", ").append(rssi + ", ").append(freq + ", ").append(channel + ", ")
                .append(actualTimestamp + ", ").append(givenTimestamp + ", ").append(intervalToPrev + "\n");
        return buf.toString();
    }

    public static String getStringHeader() {
        StringBuffer buf = new StringBuffer();
        buf.append("hit, ").append("MAC, ").append("SSID, ").append("RSSI, ")
                .append("frequency, ").append("channel, ").append("Actual Timestamp (ms), ")
                .append("Given Timestamp (us), ").append("Interval to previous (ms)\n");    // 9 columns
        return buf.toString();
    }
}


