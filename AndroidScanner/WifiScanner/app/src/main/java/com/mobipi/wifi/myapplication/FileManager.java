package com.mobipi.wifi.myapplication;

import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Vector;

/**
 * Created by wynter on 6/25/2015.
 */
public class FileManager {
    private String rootPath;
    private String tempPath;
    private String dataPath;

    private File tempLogFile = null;
    private String tempLogFilePath;
    private OutputStream tempLogFileOutputStream = null;

    private File tempRecordFile = null;
    private String tempRecordFilePath;
    private OutputStream tempRecordFileOutputStream = null;

    public String getTempPath() {
        return tempPath;
    }

    public String getDataPath(){
        return dataPath;
    }

    public boolean reopenLogFile() {
        tempLogFilePath = tempPath + "/log.txt";
        tempLogFile = new File(tempLogFilePath);
        tempLogFileOutputStream = null;
        try {
            if (tempLogFileOutputStream != null) {
                tempLogFileOutputStream.close();
                tempLogFileOutputStream = null;
            }
            tempLogFileOutputStream = new BufferedOutputStream(new FileOutputStream(tempLogFile, false));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean reopenRecordFile(){
        tempRecordFilePath = tempPath + "/record.csv";
        tempRecordFile = new File(tempRecordFilePath);
        tempRecordFileOutputStream = null;
        try {
            if (tempRecordFileOutputStream != null) {
                tempRecordFileOutputStream.close();
                tempRecordFileOutputStream = null;
            }
            tempRecordFileOutputStream = new BufferedOutputStream(new FileOutputStream(tempRecordFile, false));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean closeLogFile() {
        try {
            if (tempLogFileOutputStream != null) {
                tempLogFileOutputStream.close();
                tempLogFileOutputStream = null;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean closeRecordFile() {
        try {
            if (tempRecordFileOutputStream != null) {
                tempRecordFileOutputStream.close();
                tempRecordFileOutputStream = null;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean appendToLogFile(String str) {
        try {
            if (tempLogFileOutputStream == null)
                return false;
            tempLogFileOutputStream.write(str.getBytes());
            tempLogFileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean appendToRecordFile(String str) {
        try {
            if (tempRecordFileOutputStream == null)
                return false;
            tempRecordFileOutputStream.write(str.getBytes());
            tempRecordFileOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean isProfileFolderExist(String profileStr) {
        String path = dataPath + "/" + profileStr;
        File file = new File(path);
        return file.exists();
    }


    public boolean copyAllFilesToProfilefolder(String profile) {
        String path = dataPath + "/" + profile;
        return copyFiles(tempPath, path);
    }

    public void clearTxtFilesInTempFolder() {
        File path = new File(tempPath);
        if (path.isDirectory())
            for (File child : path.listFiles())
                if (child.isFile()) {
                    String name = child.getName().toLowerCase();
                    if (!name.endsWith(".jpg")) {
                        child.delete();
                    }
                }
    }



    public void createFolders() {
        // File path = Environment.getExternalStoragePublicDirectory(
        //       Environment.DIRECTORY_DOCUMENTS);
        File path = Environment.getExternalStorageDirectory();
        String absPath = path.getAbsolutePath();
        rootPath = absPath + "/Handheld_Wifi_RSSI_Meter";
        tempPath = rootPath + "/temp";
        dataPath = rootPath + "/data";
        path = new File(tempPath);
        if (path.isDirectory()) {
            clearAllTempFiles();
            Log.d(MainActivity.LOG_TAG, "clear temp path");
        } else {
            path.mkdirs();
            Log.d(MainActivity.LOG_TAG, "create temp path");
        }

        path = new File(dataPath);
        if (path.mkdirs() || path.isDirectory())
            Log.d(MainActivity.LOG_TAG, "data path is determined");
        else
            Log.d(MainActivity.LOG_TAG, "check data path failed");
    }

    private void clearAllTempFiles() {
        File path = new File(tempPath);
        _deleteRecursive(path);
        path = new File(tempPath);
        path.mkdir();

    }

    private static void _deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                _deleteRecursive(child);
        fileOrDirectory.delete();
    }

    public void saveToProfileFolder(String filename, String profileName, String content){
        BufferedOutputStream out;
        String path = this.dataPath+"/"+profileName+"/"+filename;
        File file = new File(path);
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            out.write(content.getBytes());
            out.close();
        } catch (Exception e) {
            Log.d(MainActivity.LOG_TAG,"write file to:"+path+" failed");
        }
    }
    public static boolean copyFiles(String srcDir, String dstDir) {

        try {
            File src = new File(srcDir);

            if (!src.isDirectory())
                return false;

            String files[] = src.list();
            int filesLength = files.length;
            for (int i = 0; i < filesLength; i++) {
                File src1 = new File(src, files[i]);
                File dst = new File(dstDir + "/" + src1.getName());
                copyFile(src1, dst);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean deleteFile(String fullpath) {
        File file = new File(fullpath);
        boolean deleted = file.delete();
        if (deleted)
            Log.d(MainActivity.LOG_TAG, "delete file " + fullpath + " success");
        else
            Log.d(MainActivity.LOG_TAG, "delete file " + fullpath + " failed");
        return deleted;
    }



    public static boolean copyFile(File sourceFile, File destFile) throws IOException {
        if (!sourceFile.exists())
            return false;

        if (!destFile.getParentFile().exists())
            destFile.getParentFile().mkdirs();

        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;

        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
            return true;
        }
    }

    public void writeChannelSummaryCollectorObject(String profileName, ChannelSummaryCollector obj){
        File path = new File(dataPath+"/"+profileName+"/ChannelSummaryCollector.bin");
        try{
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path));
            obj.writeObject(oos);
            oos.flush();
            oos.close();
        }
        catch(Exception e){
            Log.d(MainActivity.LOG_TAG, "Get the excpetion when saving an object.");
        }
    }

    public Object readChannelSummaryCollectorObject(String fullProfileFolder){

        File path = new File(fullProfileFolder+"/ChannelSummaryCollector.bin");
        if(!path.isFile())
            return null;
        ChannelSummaryCollector obj= new ChannelSummaryCollector();
        try{

            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path));
            obj.readObject(ois);
            if(obj.version <= 1){
                ois.close();
                ois = new ObjectInputStream(new FileInputStream(path));
                obj.readObject_v1(ois);
                obj.update_v1_to_v2(this);
                ois.close();
                //writeChannelSummaryCollectorObject(obj.profileName, obj); //update
                //saveToProfileFolder("statistic.txt", obj.profileName, obj.toString());
            }
            if(obj.version >= 2){
                obj.update_v2_to_v3(this);
                writeChannelSummaryCollectorObject(obj.profileName, obj); //update
                saveToProfileFolder("statistic.txt", obj.profileName, obj.toString());
            }
            else {
                ois.close();
            }
        }
        catch(Exception e){
            Log.d(MainActivity.LOG_TAG, "Get the excpetion when read an object.");
        }
        return obj;
    }

    public List<String> getFolderListInDataPath(){
        List<String> pathList = new Vector<String>();
        File path = new File(dataPath);
        if (path.isDirectory())
            for (File child : path.listFiles())
                if (child.isDirectory()) {
                    pathList.add(child.getName());
                }
        return pathList;
    }
}
