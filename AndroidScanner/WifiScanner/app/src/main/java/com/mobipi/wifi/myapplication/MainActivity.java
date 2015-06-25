package com.mobipi.wifi.myapplication;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.wifi.ScanResult;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.nio.charset.Charset;
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
    private int mOldNaviationBarColor;

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
    //Editor
    private EditText macAddressEditor;

    // for photos grid
    private ImageGridViewAdapter mImageGridViewAdapter;
    private List<ImageGridViewItem> mImageItems;
    private GridView mPhotoGrid;

    private boolean mUpdated = false;

    private WifiScanner mScanner;

    public void needUpdate() {
        mUpdated = true;
    }

    ;

    private boolean wifiInterrupted = false;
    private boolean bPause = false;
    private boolean bScrollable = true;

    private ScanContext mScanContext;
    private long pausedTimeStamp;
    private long startTime;
    private long totalElapsedTime;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            totalElapsedTime = SystemClock.elapsedRealtime() - startTime;
            totalTimeTextView.setText(formatElapseTime(totalElapsedTime));
            timerHandler.postDelayed(this, 500);
        }
    };

    private String formatElapseTime(long mill) {
        int decimal = (int) (mill % 1000);
        decimal = decimal / 10;
        int seconds = (int) (mill / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d.%02d", minutes, seconds, decimal);
    }

    protected void onDestroy() {
        super.onDestroy();
        Log.d("wifirssi", "on destroy");
    }

    protected void onResume() {
        super.onResume();
        Log.d("wifirssi", "on resume");
        if (wifiInterrupted) {
            resumeTimer();
            long duration = (SystemClock.elapsedRealtime() - pausedTimeStamp);
            addToLogView("Resumed by OS, paused " + (double) duration / 1000.0 + " seconds.", true);
            mScanner.run();

        }
    }

    protected void onPause() {
        super.onPause();
        Log.d("wifirssi", "on pause");
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
        mImageGridViewAdapter = new ImageGridViewAdapter(this, mImageItems);
        mPhotoGrid.setAdapter(mImageGridViewAdapter);

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
        mUpdated = false;
        Log.d("wifirssi", "oncreate");

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

        COLOR_MAP = new int[COLOR_ARRAY.length];
        for (int i = 0; i < COLOR_MAP.length; ++i) {
            COLOR_MAP[i] = Color.parseColor(COLOR_ARRAY[i]);
        }
        mOldNaviationBarColor = getWindow().getNavigationBarColor();

    }

    private void changeBarColor(int channel) {

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (channel < 0)
                getWindow().setNavigationBarColor(mOldNaviationBarColor);
            else
                getWindow().setNavigationBarColor(COLOR_MAP[channel]);
        }
    }

    public void addToLogView(CharSequence str, boolean bTimeNow) {
        if (bTimeNow)
            appendTimeToLogView();
        logStrBuffer.append(str).append("\n");
    }

    public void addToLogView(String str, boolean bTimeNow) {
        int length = logStrBuffer.length();
        if (length > 4096) { //shrink the buffer
            int midLength = logStrBuffer.length() / 2;
            for (int i = midLength; i < length; ++i) {
                if (logStrBuffer.charAt(i) == '\n') {
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

    public void openCamera(View view) {
        // Do something in response to button click
        Log.d("wifirssi", "The take photo button is clicked");

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    public void deletePhoto(View view) {
        boolean bUpdate = false;
        for (Iterator<ImageGridViewItem> iter = mImageItems.iterator(); iter.hasNext(); ) {
            ImageGridViewItem item = iter.next();
            if (item.bSelect && !item.bLock) {
                iter.remove();
                bUpdate = true;
            }
        }
        if (bUpdate) {
            mImageGridViewAdapter.notifyDataSetChanged();
            needUpdate();
        }
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

    public void startScan(View view) {
        Log.d("wifirssi", "start scan");

        currentChannelTextView.setText("N/A");
        scannedChannelsTextView.setText("0");
        totalTimeTextView.setText("0");
        avgRSSITextView.setText("0");

        startTimer();

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
        bPause = false;
        addToLogView("Begin WiFi scan...", true);
        updateLogView();
    }

    public void endScan(View view) {
        Log.d("wifirssi", "end scan");
        stopTimer();
        mScanner.stop();
        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        pauseButton.setText("pause");
        bPause = false;
        pauseButton.setEnabled(false);
        addToLogView("End WiFi scan.", true);
        updateLogView();
        macAddressEditor.setEnabled(true);
        changeBarColor(-1);
    }

    public void saveScan(View view) {
        Log.d("wifirssi", "save scan");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            SimpleDateFormat format = new SimpleDateFormat();

            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
            Date date = new Date();
            ImageGridViewItem item = new ImageGridViewItem(imageBitmap, dateFormat.format(date));
            mImageItems.add(item);
            mImageGridViewAdapter.notifyDataSetChanged();
            needUpdate();
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
            mHistoryFrame.setVisibility(View.GONE);
            mMainFrame.setVisibility(View.VISIBLE);
            return true;
        } else if (id == R.id.action_history) {
            mMainFrame.setVisibility(View.GONE);
            mHistoryFrame.setVisibility(View.VISIBLE);
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
            //Log.d("wifirssi","BSSID:"+res.BSSID+" SSID:"+res.SSID+" freq:"+(1+(res.frequency-2412)/5)+" Level(RSSI):"+res.level+" timeStamp(s):"+(double)res.timestamp/1000000.0);
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
                mScanContext.currentChannel = channel;
                mScanContext.currentChannelSamples = 1;
                mScanContext.sumCurrentRSSI = res.level;
                mScanContext.currentChannelStartTime = totalElapsedTime;
                currentChannelTextView.setText("" + channel);
                ++mScanContext.scannedChannel;
                if (mScanContext.scannedChannel > 0) {
                    scannedChannelsTextView.setText("" + mScanContext.scannedChannel);
                }
                changeBarColor(channel);
            } else {
                ++mScanContext.currentChannelSamples;
                mScanContext.sumCurrentRSSI += res.level;
            }
            double avgRSSI = (double) mScanContext.sumCurrentRSSI / (double) mScanContext.currentChannelSamples;
            avgRSSITextView.setText(String.format("%.3f", avgRSSI));
        } else {
            log += "-- Not found.";
        }
        addToLogView(log, true);
        updateLogView();
        timeForCurrentChannelTextView.setText(formatElapseTime(totalElapsedTime - mScanContext.currentChannelStartTime));
        sampledRecordsForCurrentChannel.setText("" + mScanContext.currentChannelSamples);
    }

    public int getChannelFromFreq(int freq) {
        return (freq - 2412) / 5 + 1;
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
}

class ImageGridViewItem {
    public final Bitmap imageBitmap;
    public final String title;
    public boolean bSelect;
    public boolean bLock;

    public ImageGridViewItem(Bitmap bitmap, String title) {
        this.imageBitmap = bitmap;
        this.title = title;
        bSelect = false;
        bLock = false;
    }
}

class ImageGridViewAdapter extends BaseAdapter {
    private Context mContext;
    private List<ImageGridViewItem> mItems;
    private View.OnClickListener mCallback;
    private final static int SELECT_COLOR = Color.parseColor("#FFCCCC");
    private final static int NORMAL_COLOR = Color.TRANSPARENT;
    private final static int LOCK_COLOR = Color.parseColor("#FFB505");

    public ImageGridViewAdapter(Context context, List<ImageGridViewItem> items) {
        mContext = context;
        mItems = items;
        mCallback = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("wifirssi", "click photo");
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
        viewHolder.mThumbnailImageView.setOnClickListener(mCallback);
        viewHolder.mPhotoFrame.setOnClickListener(mCallback);
        viewHolder.mTitleTextView.setOnClickListener(mCallback);
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