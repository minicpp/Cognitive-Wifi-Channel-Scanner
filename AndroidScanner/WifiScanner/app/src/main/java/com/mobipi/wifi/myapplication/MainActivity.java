package com.mobipi.wifi.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


public class MainActivity extends Activity {

    private final static int REQUEST_IMAGE_CAPTURE = 1;
    private final static String[] COLOR_ARRAY = {"#7be5b5", "#f03b20", "#c51b8a", "#b083ea", "#feb24c", "#ce6950", "#9ebcda", "#a4fcc1",
            "#fca58f", "#85a019", "#e57d7b", "#8102e2", "#7376ce"};
    private static int[] COLOR_MAP;
    public  static String LOG_TAG = "wifirssi";

    private int mOldNavigationBarColor;

    private LinearLayout mHistoryFrame;
    private LinearLayout mMainFrame;

    //button
    private Button startButton;
    private Button stopButton;
    private Button saveButton;
    private Button pauseButton;

    //view
    private TextView logView;
    private StringBuffer logStrBuffer;
    private ScrollView logScrollView;
    private TextView currentChannelTextView;
    private TextView scannedChannelsTextView;
    private TextView totalTimeTextView;
    private TextView timeForCurrentChannelTextView;
    private TextView sampledRecordsForCurrentChannel;
    private TextView avgRSSITextView;
    private TextView statisticsTextView;
    private TextView reActiveTextView;
    int reActiveCount = 0;

    //Editor
    private EditText macAddressEditor;
    private EditText profileNameEditor;
    private EditText remarksEditor;

    // for photos grid
    private ImageGridViewAdapter mImageGridViewAdapter;
    private List<ImageGridViewItem> mImageItems;
    private GridView mPhotoGrid;
    private ImageView bigImageView;

    private LinearLayout statisticsFrame;

    private WifiScanner mScanner;



    private boolean wifiInterrupted = false;
    private boolean bPause = false;
    private boolean bScrollable = true;

    private ScanContext mScanContext;
    private long pausedTimeStamp;
    private long startTime;
    private long totalElapsedTime;


    private FileManager fileMgr;
    private String mCurrentPhotoPath;
    private String mCurrentPhotoPathTimeStamp;
    private ChannelMgr channelMgr;

    //
    private ListView historyListView;
    private ArrayAdapter<String> historyAdaptor;
    private TextView historyDetailsTextView;

    //barchart
    private BarChart barchart;
    private CandleStickChart candlechart;
    private LinearLayout chartLayout;
    private BarDraw barDraw;
    private CandleDraw candleDraw;
    ChannelSummaryCollector lastHistoryCollector=null;
    private Button barBtn;
    private Button ciBtn;


    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            totalElapsedTime = SystemClock.elapsedRealtime() - startTime;
            totalTimeTextView.setText(formatElapseTime(totalElapsedTime));
            if(mScanner.keepScanAlive()) {
                addToLogView("RE-ACTIVATE WIFI SCANNER for Unknown bugs.", true);
                ++reActiveCount;
                reActiveTextView.setText(""+reActiveCount);
            }
            timerHandler.postDelayed(this, 500);
        }
    };

    static public String formatElapseTime(long mill) {
        int decimal = (int) (mill % 1000);
        decimal = decimal / 10;
        int seconds = (int) (mill / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d.%02d", minutes, seconds, decimal);
    }

    protected void onDestroy() {
        super.onDestroy();
        fileMgr.closeLogFile();
        channelMgr.stop();
        mScanner.release();
        Log.d(MainActivity.LOG_TAG, "on destroy");
        
    }

    protected void onResume() {
        super.onResume();
        Log.d(MainActivity.LOG_TAG, "on resume");
        if (wifiInterrupted) {
            resumeTimer();
            long duration = (SystemClock.elapsedRealtime() - pausedTimeStamp);
            addToLogView("Resumed by OS, paused " + (double) duration / 1000.0 + " seconds.", true);
            mScanner.run();

        }
    }

    protected void onPause() {
        super.onPause();
        Log.d(MainActivity.LOG_TAG, "on pause");
        if (mScanner.isRun()) {
            wifiInterrupted = true;
            mScanner.stop();
            addToLogView("Paused by OS", true);
            pausedTimeStamp = SystemClock.elapsedRealtime();
            stopTimer();
        } else
            wifiInterrupted = false;
    }

    public void pause(View view) {
        if (mScanner.isRun()) {
            mScanner.stop();
            pauseButton.setText("Resume");
            bPause = true;
            addToLogView("Paused", true);
            pausedTimeStamp = SystemClock.elapsedRealtime();
            stopTimer();
        } else if (bPause) {
            resumeTimer();
            long duration = (SystemClock.elapsedRealtime() - pausedTimeStamp);
            addToLogView("Resumed, paused " + (double) duration / 1000.0 + " seconds.", true);
            mScanner.run();
            pauseButton.setText("Pause");
            bPause = false;
        }
        updateLogView();
    }

    public void logViewClick(View view) {
        if (bScrollable)
            bScrollable = false;
        else
            bScrollable = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mMainFrame = (LinearLayout) findViewById(R.id.mainFrame);
        mMainFrame.setVisibility(View.VISIBLE);
        mHistoryFrame = (LinearLayout) findViewById(R.id.historyFrame);
        mHistoryFrame.setVisibility(View.GONE);

        mPhotoGrid = (GridView) findViewById(R.id.photoGrid);
        mImageItems = new ArrayList<ImageGridViewItem>();
        bigImageView = (ImageView) findViewById(R.id.bigImageView);
        mImageGridViewAdapter = new ImageGridViewAdapter(this, mImageItems, bigImageView);
        mPhotoGrid.setAdapter(mImageGridViewAdapter);
        bigImageView.setVisibility(View.GONE);

        statisticsFrame=(LinearLayout) findViewById(R.id.statisticsFrame);
        statisticsFrame.setVisibility(View.GONE);

        mScanner = new WifiScanner(this);
        mScanner.init();

        startButton = (Button) findViewById(R.id.startButton);
        stopButton = (Button) findViewById(R.id.endButton);
        saveButton = (Button) findViewById(R.id.saveButton);
        pauseButton = (Button) findViewById(R.id.pauseButton);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        saveButton.setEnabled(false);
        pauseButton.setEnabled(false);

        Log.d(MainActivity.LOG_TAG, "oncreate");

        logView = (TextView) findViewById(R.id.logView);
        logStrBuffer = new StringBuffer();
        addToLogView(logView.getText(), false);

        logScrollView = (ScrollView) findViewById(R.id.logScrollView);

        //editor
        macAddressEditor = (EditText) findViewById(R.id.macAddressEditor);

        currentChannelTextView = (TextView) findViewById(R.id.currentChannelTextView);
        currentChannelTextView.setText("N/A");
        scannedChannelsTextView = (TextView) findViewById(R.id.scannedChannelsTextView);
        scannedChannelsTextView.setText("0");
        totalTimeTextView = (TextView) findViewById(R.id.totalTimeTextView);
        totalTimeTextView.setText("0");
        timeForCurrentChannelTextView = (TextView) findViewById(R.id.timeForCurrentChannelTextView);
        timeForCurrentChannelTextView.setText("0");
        sampledRecordsForCurrentChannel = (TextView) findViewById(R.id.sampledRecordsForCurrentChannel);
        avgRSSITextView = (TextView) findViewById(R.id.avgRSSITextView);
        statisticsTextView = (TextView) findViewById(R.id.statisticsTextView);
        reActiveTextView = (TextView) findViewById(R.id.reActiveTextView);

        COLOR_MAP = new int[COLOR_ARRAY.length];
        for (int i = 0; i < COLOR_MAP.length; ++i) {
            COLOR_MAP[i] = Color.parseColor(COLOR_ARRAY[i]);
        }
        mOldNavigationBarColor = getWindow().getNavigationBarColor();

        fileMgr = new FileManager();
        fileMgr.createFolders();

        channelMgr = new ChannelMgr(fileMgr);

        profileNameEditor = (EditText)findViewById(R.id.profileNameEditor);
        String profileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        profileName = "rec_"+profileName;
        profileNameEditor.setText(profileName);
        remarksEditor = (EditText)findViewById(R.id.remarksEditor);

        historyDetailsTextView = (TextView)findViewById(R.id.historyDetailsTextView);
        historyListView = (ListView)findViewById(R.id.historyListView);
        historyListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                ChannelSummaryCollector summaryCollector = channelMgr.readChannelSummary(historyAdaptor.getItem(position));
                lastHistoryCollector = summaryCollector;
                String str = summaryCollector.toString();
                historyDetailsTextView.setText(str);
                if(barchart.getVisibility() == View.VISIBLE)
                    barDraw.update(summaryCollector.channelSummaryList);
                if(candlechart.getVisibility() == View.VISIBLE)
                    candleDraw.update(summaryCollector.channelSummaryList);
            }
        });
        historyAdaptor = new ArrayAdapter<String>(this, R.layout.simple_textview,
                channelMgr.getHistoryRecords());
        historyListView.setAdapter(historyAdaptor);
        ChannelSummaryCollector lastHistoryCollector=null;

        barchart = (BarChart) findViewById(R.id.barchart);
        candlechart = (CandleStickChart) findViewById(R.id.candlechart);
        chartLayout = (LinearLayout)findViewById(R.id.chartLayout);
        chartLayout.setVisibility(View.GONE);
        barDraw = new BarDraw(barchart);
        candleDraw = new CandleDraw(candlechart);
        barBtn = (Button)findViewById(R.id.barBtn);
        ciBtn = (Button)findViewById(R.id.ciBtn);
    }

    private void changeBarColor(int channel) {

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (channel < 0)
                getWindow().setNavigationBarColor(mOldNavigationBarColor);
            else
                getWindow().setNavigationBarColor(COLOR_MAP[channel]);
        }
    }

    public void addToLogView(CharSequence str, boolean bTimeNow) {
        if (bTimeNow)
            appendTimeToLogView();
        logStrBuffer.append(str).append("\n");
    }

    public void clearLogView(){
        logStrBuffer = new StringBuffer();
    }

    public void addToLogView(String str, boolean bTimeNow) {
        int length = logStrBuffer.length();
        if (length > 4096) { //shrink the buffer
            int midLength = logStrBuffer.length() / 2;
            for (int i = midLength; i < length; ++i) {
                if (logStrBuffer.charAt(i) == '\n') {
                    fileMgr.appendToLogFile(logStrBuffer.substring(0, i+1));
                    logStrBuffer.delete(0, i + 1);
                    break;
                }
            }
        }
        if (bTimeNow)
            appendTimeToLogView();
        logStrBuffer.append(str).append("\n");
    }

    private void appendTimeToLogView() {
        logStrBuffer.append("[");
        DateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        Date date = new Date();
        logStrBuffer.append(dateFormat.format(date)).append("]  ");
    }


    public void updateLogView() {
        if(mMainFrame.getVisibility() != View.VISIBLE)
            return;
        logView.setText(logStrBuffer.toString());
        if (bScrollable) {
            logScrollView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (bScrollable)
                        logScrollView.fullScroll(View.FOCUS_DOWN);
                    //logScrollView.scrollTo(0, logScrollView.getBottom());
                }
            }, 500);
        }
    }

    public void closeBigImageView(View view){
        ImageView iview = (ImageView)view;
        iview.setVisibility(View.GONE);
        iview.setImageDrawable(null);
    }

    public void openCamera(View view) {
        // Do something in response to button click
        Log.d(MainActivity.LOG_TAG, "The take photo button is clicked");

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

                mCurrentPhotoPathTimeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "JPEG_" + mCurrentPhotoPathTimeStamp + "_";
                File storageDir = new File(fileMgr.getTempPath());
                File image = null;

                image = File.createTempFile(
                        imageFileName,  /* prefix */
                        ".jpg",         /* suffix */
                        storageDir      /* directory */
                );
                mCurrentPhotoPath = image.getAbsolutePath();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(image));
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } catch (IOException e) {
            Log.d(MainActivity.LOG_TAG, "Create Image file failed");
        }
    }

    public void deletePhoto(View view) {
        boolean bUpdate = false;
        for (Iterator<ImageGridViewItem> iter = mImageItems.iterator(); iter.hasNext(); ) {
            ImageGridViewItem item = iter.next();
            if (item.bSelect && !item.bLock) {
                fileMgr.deleteFile(item.path);
                iter.remove();
                bUpdate = true;
            }
        }
        if (bUpdate) {
            mImageGridViewAdapter.notifyDataSetChanged();
        }
    }

    public void onCiBtn(View view){
        barchart.setVisibility(View.GONE);
        candlechart.setVisibility(View.VISIBLE);
    }

    public void onBarBtn(View view){
        barchart.setVisibility(View.VISIBLE);
        candlechart.setVisibility(View.GONE);
    }

    private void startTimer() {
        startTime = SystemClock.elapsedRealtime();
        totalElapsedTime = 0;
        timerHandler.postDelayed(timerRunnable, 0);
    }

    private void stopTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void resumeTimer() {
        startTime += SystemClock.elapsedRealtime() - pausedTimeStamp;
        timerHandler.postDelayed(timerRunnable, 0);
    }

    public void askSave(){
        new AlertDialog.Builder(this)
                .setMessage("You do not save the current record.\nDo you want discard?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startScan();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void clearTempBeforeStart(){
        fileMgr.clearTxtFilesInTempFolder(); //remove log and records' files

        boolean bUpdate = false;
        for (Iterator<ImageGridViewItem> iter = mImageItems.iterator(); iter.hasNext(); ) {
            ImageGridViewItem item = iter.next();
            if (!item.bLock) {
                fileMgr.deleteFile(item.path);
                iter.remove();
                bUpdate = true;
            }
        }
        if (bUpdate) {
            mImageGridViewAdapter.notifyDataSetChanged();
        }
    }

    public void startScan(){
        currentChannelTextView.setText("N/A");
        scannedChannelsTextView.setText("0");
        totalTimeTextView.setText("0");
        avgRSSITextView.setText("0");



        String profileName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        profileName = "rec_"+profileName;
        profileNameEditor.setText(profileName);

        clearTempBeforeStart();

        //check mac address is valid or not
        mScanContext = new ScanContext();
        mScanContext.macAddress = macAddressEditor.getText().toString().trim().toLowerCase();
        if (!mScanContext.macAddress.matches("^([0-9a-f]{2}[:-]){5}([0-9a-f]{2})$")) {
            Toast.makeText(this, "The MAC address is not valid. Cannot start.", Toast.LENGTH_LONG).show();
            macAddressEditor.requestFocus();
            return;
        }
        macAddressEditor.setEnabled(false);

        mScanner.run();
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        pauseButton.setEnabled(true);
        saveButton.setEnabled(false);
        bPause = false;
        clearLogView();
        fileMgr.reopenLogFile();
        channelMgr.start();
        addToLogView("Begin WiFi scan for AP with MAC: " + mScanContext.macAddress, true);
        updateLogView();

        startTimer();
    }


    public void onStartScan(View view) {
        Log.d(MainActivity.LOG_TAG, "start scan");
        if(saveButton.isEnabled())
            askSave();
        else
            startScan();
    }

    public void endScan(View view) {
        Log.d(MainActivity.LOG_TAG, "end scan");
        stopTimer();
        mScanner.stop();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        saveButton.setEnabled(true);

        pauseButton.setText("pause");
        bPause = false;
        pauseButton.setEnabled(false);
        addToLogView("End WiFi scan.", true);
        addToLogView("\tTotal elapsed Time (s): "+ (double)totalElapsedTime/1000.0 , false);
        addToLogView("\tScanned channels: " + mScanContext.scannedChannel, false);

        endLog();
        channelMgr.stop();
        updateLogView();
        macAddressEditor.setEnabled(true);
        changeBarColor(-1);
    }

    private void endLog(){
        fileMgr.appendToLogFile(logStrBuffer.toString());
        fileMgr.closeLogFile();
    }

    public void saveScan(View view) {
        Log.d(MainActivity.LOG_TAG, "save scan");
        String folderName = profileNameEditor.getText().toString();
        folderName = folderName.trim();
        if (!folderName.matches("^[0-9a-zA-Z_]+$")) {
            Toast.makeText(this, "The profile name is not valid. Cannot save.", Toast.LENGTH_LONG).show();
            profileNameEditor.requestFocus();
            return;
        }
        else if(fileMgr.isProfileFolderExist(folderName))
        {
            Toast.makeText(this, "Duplicated profile name. Cannot save. Please give an unique name.", Toast.LENGTH_LONG).show();
            profileNameEditor.requestFocus();
            return;
        }

        //begin save
        //Move log
        fileMgr.copyAllFilesToProfilefolder(folderName);
        //write remarks
        fileMgr.saveToProfileFolder("remark.txt", folderName, remarksEditor.getText().toString());
        fileMgr.saveToProfileFolder("statistic.txt", folderName, channelMgr.toString());
        channelMgr.saveChannelSummary(folderName);
        //ChannelSummaryCollector c = channelMgr.readChannelSummary(folderName);

        //end save
        Toast.makeText(this, "Your records have been saved in folder: "+folderName, Toast.LENGTH_LONG).show();
        saveButton.setEnabled(false);
    }

    static public Bitmap getPic(int targetW, int targetH, String picPath){
        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(picPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;
        return BitmapFactory.decodeFile(picPath, bmOptions);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.d(MainActivity.LOG_TAG, "Get image: " + mCurrentPhotoPath);

            Bitmap bitmap = getPic(121, 162, mCurrentPhotoPath);

            ImageGridViewItem item = new ImageGridViewItem(bitmap, mCurrentPhotoPath, mCurrentPhotoPathTimeStamp);
            mImageItems.add(item);
            mImageGridViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_home) {
            mMainFrame.setVisibility(View.VISIBLE);
            logView.setText(logStrBuffer.toString());
            mHistoryFrame.setVisibility(View.GONE);
            bigImageView.setVisibility(View.GONE);
            statisticsFrame.setVisibility(View.GONE);
            chartLayout.setVisibility(View.GONE);
            return true;
        } else if (id == R.id.action_history) {
            mHistoryFrame.setVisibility(View.VISIBLE);
            mMainFrame.setVisibility(View.GONE);
            bigImageView.setVisibility(View.GONE);
            statisticsFrame.setVisibility(View.GONE);
            chartLayout.setVisibility(View.VISIBLE);

            historyAdaptor.clear();
            historyAdaptor.addAll(channelMgr.getHistoryRecords());
            historyAdaptor.notifyDataSetChanged();

            if(lastHistoryCollector == null) {
                barDraw.updateZero();
                candleDraw.updateZero();
            }
            else {
                barDraw.update(lastHistoryCollector.channelSummaryList);
                candleDraw.update(lastHistoryCollector.channelSummaryList);
            }
            return true;
        }
        else if(id == R.id.action_statistic) {
            statisticsTextView.setText(channelMgr.toString());
            statisticsFrame.setVisibility(View.VISIBLE);
            mMainFrame.setVisibility(View.GONE);
            mHistoryFrame.setVisibility(View.GONE);
            bigImageView.setVisibility(View.GONE);
            chartLayout.setVisibility(View.VISIBLE);

            barDraw.update(channelMgr.getChannelSummaryItemList());
            candleDraw.update(channelMgr.getChannelSummaryItemList());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onBackPressed() {
        // Do Here what ever you want do on back press;
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        MainActivity.this.finish();
                    }
                })
                .setNegativeButton("No", null)
                .show();
    }


    public void wifiScanCallback(List<ScanResult> list) {
        ++mScanContext.scanCounts;
        ScanResult res = null;
        String log = "[" + mScanContext.scanCounts + "] Get " + list.size() + " APs; ";
        for (ScanResult sres : list) {
            //Log.d(MainActivity.LOG_TAG,"BSSID:"+res.BSSID+" SSID:"+res.SSID+" freq:"+(1+(res.frequency-2412)/5)+" Level(RSSI):"+res.level+" timeStamp(s):"+(double)res.timestamp/1000000.0);
            if (sres.BSSID.equals(mScanContext.macAddress)) {
                res = sres;
                break;
            }
        }

        if (res != null) {
            String ssid = res.SSID;
            if (ssid == null || ssid.length() == 0)
                ssid = "[hide]";
            int channel = getChannelFromFreq(res.frequency);
            log += "** Found, SSID:" + ssid + ", CH:" + channel + "; RSSI:" + res.level + ";\n\tTS:" + (double) res.timestamp / 1000000.0;
            if (mScanContext.currentChannel != channel) {

                if(mScanContext.currentChannel > 0) {
                    addToLogView("^^^^^^^^ Elapsed time for channel [" + mScanContext.currentChannel + "]: " +
                            formatElapseTime(totalElapsedTime - mScanContext.currentChannelStartTime), false);
                    addToLogView("^^^^^^^^ Number of samples for channel [" + mScanContext.currentChannel + "]: " +
                            mScanContext.currentChannelSamples, false);
                    if (mScanContext.currentChannelSamples > 0)
                        addToLogView("^^^^^^^^ Average RSSI for channel [" + mScanContext.currentChannel + "]: " +
                                mScanContext.getAverageRSSI(), false);
                }

                mScanContext.currentChannel = channel;
                mScanContext.currentChannelSamples = 1;
                mScanContext.sumCurrentRSSI = res.level;
                mScanContext.currentChannelStartTime = totalElapsedTime;

                ++mScanContext.scannedChannel;
                if (mScanContext.scannedChannel > 0) {
                    scannedChannelsTextView.setText("" + mScanContext.scannedChannel);
                }
                changeBarColor(channel);
            } else {
                ++mScanContext.currentChannelSamples;
                mScanContext.sumCurrentRSSI += res.level;
            }
            currentChannelTextView.setText(channel+" ("+mScanContext.currentChannelSamples+")");
            double avgRSSI = mScanContext.getAverageRSSI();
            avgRSSITextView.setText(String.format("%.3f", avgRSSI));
        } else {
            log += "-- Not found.";
        }
        long duration = totalElapsedTime - mScanContext.currentChannelStartTime;
        channelMgr.addRecord(res, duration);
        addToLogView(log, true);
        updateLogView();
        timeForCurrentChannelTextView.setText(formatElapseTime(duration));
        sampledRecordsForCurrentChannel.setText("" + mScanContext.currentChannelSamples);
        if(statisticsFrame.getVisibility() == View.VISIBLE){
            statisticsTextView.setText(channelMgr.toString());
        }
        if(statisticsFrame.getVisibility() == View.VISIBLE && chartLayout.getVisibility() == View.VISIBLE){
            if(barchart.getVisibility() == View.VISIBLE)
                barDraw.update(channelMgr.getChannelSummaryItemList());
            if(candlechart.getVisibility() == View.VISIBLE)
                candleDraw.update(channelMgr.getChannelSummaryItemList());
        }
    }

    public static int getChannelFromFreq(int freq) {
        return (freq - 2412) / 5 + 1;
    }

    class BarDraw{
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<String>();
        BarData data;
        BarDataSet dataset;
        BarChart chart;
        public BarDraw(BarChart chart){
            for(int i=0; i<11; ++i){
                entries.add(new BarEntry(0.0f, i));
                labels.add("CH "+(i+1));
            }
            dataset =  new BarDataSet(entries, "Average RSSI");
            data = new BarData(labels, dataset);
            this.chart = chart;

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setAxisMinValue(-100);
            leftAxis.setAxisMaxValue(10);
            leftAxis.setStartAtZero(false);
            chart.setDescription("Average RSSI");
            chart.setData(data);
            //barchart.notifyDataSetChanged();
        }
        public void update(List<ChannelSummaryItem> sumCol){
            if(sumCol==null || sumCol.size() == 0)
            {
                updateZero();
                return;
            }
            for(int i=0;i <11; ++i){
                ChannelSummaryItem item = sumCol.get(i);
                BarEntry ent= entries.get(i);
                ent.setVal((float) item.getAverageRSSI());
                //ent.setVal((float)0.02f);
            }
            chart.notifyDataSetChanged();
            chart.invalidate();
        }

        public void updateZero(){
            for(int i=0;i <11; ++i){
                BarEntry ent= entries.get(i);
                ent.setVal((float)0);
                //ent.setVal((float)0.02f);
            }
            chart.notifyDataSetChanged();
            chart.invalidate();
        }
    }

    class CandleDraw{
        ArrayList<CandleEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<String>();
        CandleData data;
        CandleDataSet dataset;
        CandleStickChart chart;
        public CandleDraw(CandleStickChart chart){
            for(int i=0; i<11; ++i){
                entries.add(new CandleEntry(i, 0f, 0f, 0f, 0f, 0f));
                labels.add("CH "+(i+1));
            }

            dataset =  new CandleDataSet(entries, "95% Confidence Interval");
            dataset.setShadowColor(Color.DKGRAY);
            dataset.setDecreasingColor(Color.RED);
            dataset.setIncreasingColor(Color.RED);

            data = new CandleData(labels, dataset);
            this.chart = chart;

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setAxisMinValue(-100);
            leftAxis.setAxisMaxValue(10);
            leftAxis.setStartAtZero(false);
            chart.setDescription("95% Confidence Interval of mean RSSI");
            chart.setData(data);
            //barchart.notifyDataSetChanged();
        }
        public void update(List<ChannelSummaryItem> sumCol){
            if(sumCol==null || sumCol.size() == 0)
            {
                updateZero();
                return;
            }
            for(int i=0;i <11; ++i){
                ChannelSummaryItem item = sumCol.get(i);
                CandleEntry ent= entries.get(i);
                //ent.setVal((float) item.getAverageRSSI());
                ent.setHigh((float) item.getConfidenceInterval95Upper());
                ent.setLow((float) item.getConfidenceInterval95Lower());
                ent.setOpen((float) item.getConfidenceInterval95Upper());
                ent.setClose((float) item.getConfidenceInterval95Lower());

                //ent.setVal((float)0.02f);
            }
            chart.notifyDataSetChanged();
            chart.invalidate();
        }

        public void updateZero(){
            for(int i=0;i <11; ++i){
                CandleEntry ent= entries.get(i);
                ent.setVal((float) 0);
                ent.setHigh((float) 0);
                ent.setLow((float) 0);
                ent.setOpen((float) 0);
                ent.setClose((float) 0);
                //ent.setVal((float)0.02f);
            }
            chart.notifyDataSetChanged();
            chart.invalidate();
        }
    }
}

class ScanContext {
    public int scanCounts = 0;
    public int pausedCounts = 0;
    public int pausedBySystem = 0;
    public int scanAPcounts = 0;
    public int currentChannel = 0;
    public long currentChannelStartTime = 0;
    public int scannedChannel = -1;
    public int sumCurrentRSSI = 0;
    public String macAddress;
    public int currentChannelSamples = 0;
    public double getAverageRSSI(){
        return (double)(sumCurrentRSSI)/(double)(currentChannelSamples);
    }
}





class ImageGridViewItem {
    public final Bitmap imageBitmap;
    public final String title;
    public final String path;
    public boolean bSelect;
    public boolean bLock;

    public ImageGridViewItem(Bitmap bitmap, String path, String title) {
        this.imageBitmap = bitmap;
        this.path = path;
        this.title = title;
        bSelect = false;
        bLock = false;
    }
}

class ImageGridViewAdapter extends BaseAdapter {
    private Context mContext;
    private List<ImageGridViewItem> mItems;
    private View.OnClickListener mSelectCallback;
    private View.OnClickListener mZoomCallback;
    private ImageView bigImageView;
    private final static int SELECT_COLOR = Color.parseColor("#FFCCCC");
    private final static int NORMAL_COLOR = Color.TRANSPARENT;
    private final static int LOCK_COLOR = Color.parseColor("#FFB505");

    public ImageGridViewAdapter(Context context, List<ImageGridViewItem> items, final ImageView bigImageView) {
        mContext = context;
        mItems = items;
        this.bigImageView = bigImageView;
        mSelectCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(MainActivity.LOG_TAG, "click photo");
                ViewHolder viewHolder = (ViewHolder) v.getTag();
                int position = viewHolder.position;
                ImageGridViewItem item = mItems.get(position);
                if (!item.bSelect) {
                    viewHolder.mPhotoFrame.setBackgroundColor(SELECT_COLOR);
                    item.bSelect = true;
                } else if (item.bSelect && !item.bLock) {
                    viewHolder.mPhotoFrame.setBackgroundColor(LOCK_COLOR);
                    item.bLock = true;
                } else {
                    viewHolder.mPhotoFrame.setBackgroundColor(NORMAL_COLOR);
                    item.bSelect = false;
                    item.bLock = false;
                }
            }
        };

        mZoomCallback = new View.OnClickListener(){
            public void onClick(View v) {
                ViewHolder viewHolder = (ViewHolder) v.getTag();
                int position = viewHolder.position;
                ImageGridViewItem item = mItems.get(position);
                Bitmap b = MainActivity.getPic(v.getWidth(),v.getHeight(),item.path);
                bigImageView.setImageBitmap(b);
                bigImageView.setVisibility(View.VISIBLE);
            }
        };

    }

    @Override
    public int getCount() {
        return mItems.size();
    }

    @Override
    public Object getItem(int position) {
        return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(R.layout.gridimageview_item, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.mThumbnailImageView = (ImageView) convertView.findViewById(R.id.photoThumbnail);
            viewHolder.mTitleTextView = (TextView) convertView.findViewById(R.id.photoTitle);
            viewHolder.mPhotoFrame = (RelativeLayout) convertView.findViewById(R.id.photoFrame);
            viewHolder.mThumbnailImageView.setTag(viewHolder);
            viewHolder.mTitleTextView.setTag(viewHolder);
            convertView.setTag(viewHolder);
        } else
            viewHolder = (ViewHolder) convertView.getTag();
        ImageGridViewItem item = mItems.get(position);
        viewHolder.mThumbnailImageView.setImageBitmap(item.imageBitmap);
        viewHolder.mTitleTextView.setText(item.title);
        viewHolder.mThumbnailImageView.setOnClickListener(mZoomCallback);
        viewHolder.mPhotoFrame.setOnClickListener(mZoomCallback);
        viewHolder.mTitleTextView.setOnClickListener(mSelectCallback);
        viewHolder.position = position;

        if (item.bSelect && !item.bLock)
            viewHolder.mPhotoFrame.setBackgroundColor(SELECT_COLOR);
        else if (item.bLock) {
            viewHolder.mPhotoFrame.setBackgroundColor(LOCK_COLOR);
        } else {
            viewHolder.mPhotoFrame.setBackgroundColor(NORMAL_COLOR);
        }

        return convertView;
    }

    private class ViewHolder {
        ImageView mThumbnailImageView;
        TextView mTitleTextView;
        RelativeLayout mPhotoFrame;
        int position;
    }
}