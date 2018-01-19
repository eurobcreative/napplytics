package com.eurobcreative.monroe.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;

import com.eurobcreative.monroe.CustomPhoneStateListener;
import com.eurobcreative.monroe.WebBrowsingUtils;
import com.eurobcreative.monroe.MeasurementTask;
import com.eurobcreative.monroe.R;
import com.eurobcreative.monroe.VideoStreamingUtils;
import com.eurobcreative.monroe.api.API;
import com.eurobcreative.monroe.exceptions.MeasurementError;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.NetworkUtils;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.AdaptiveMediaSourceEventListener;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.eurobcreative.monroe.VideoStreamingUtils.HSPA_NETWORK;
import static com.eurobcreative.monroe.VideoStreamingUtils.LTE_NETWORK;
import static com.eurobcreative.monroe.VideoStreamingUtils.urlVideoStreamingDashModeArray;

public class MainActivity extends AppCompatActivity implements AdaptiveMediaSourceEventListener,
        BandwidthMeter.EventListener, Player.EventListener, ExtractorMediaSource.EventListener {
    /**
     * Keep track of the task to ensure we can cancel it if requested.
     */
    private SaveData saveData = null;

    private String android_id = null;

    private API api;

    private ListView console_lv;
    private ArrayAdapter<String> arrayAdapter;

    private Spinner names_sp;
    private ArrayAdapter<String> stringArrayAdapter;

    private long throughput = -1;
    private int rssi = 1;
    private int rsrp = 1;

    private int counter = 0;
    private boolean isFinished = true;
    private boolean access = true;

    private TelephonyManager telephonyManager;
    private CustomPhoneStateListener customPhoneStateListener;

    // Video
    private String[] urlServerVideoArray = VideoStreamingUtils.urlVideoStreamingDashModeArray;

    private int video_mode = VideoStreamingUtils.DASH_MODE;

    private SimpleExoPlayer player;

    private DataSource.Factory mediaDataSourceFactory;
    private DefaultTrackSelector trackSelector;
    private boolean shouldAutoPlay;
    private BandwidthMeter bandwidthMeter;

    private long total_size = 0;
    private long total_ms = 0;

    private static final int WEB_BROWSING_OPTION = 0;
    private static final int VIDEO_STREAMING_OPTION = 1;
    private int selected_option = -1;

    private int network_type = VideoStreamingUtils.HSPA_NETWORK;

    public static final String NAPPLYTICS_ACTION = "napplytics_action";
    public static final String THROUGHPUT_RESULT = "throughput_result";
    public static final String RSSI_RESULT = "rssi_result";

    private static final String WEB_BROWSING_SERVICE = "Web Browsing";
    private static final String VIDEO_STREAMING_SERVICE = "Video Streaming";

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (selected_option) {
                case WEB_BROWSING_OPTION:
                    if (action.equals(NAPPLYTICS_ACTION)) {

                        if (intent.getLongExtra(THROUGHPUT_RESULT, -1) != -1) {
                            throughput = intent.getLongExtra(THROUGHPUT_RESULT, -1);
                        }

                        if (intent.getIntExtra(RSSI_RESULT, 1) != 1) {
                            rssi = intent.getIntExtra(RSSI_RESULT, 1);
                        }

                        if (access && throughput != -1 && rssi != 1) {
                            access = false;

                            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                            for (CellInfo cellInfo : cellInfoList) {
                                if (cellInfo instanceof CellInfoLte) {
                                    if (cellInfo.isRegistered()) {
                                        rsrp = ((CellInfoLte) cellInfo).getCellSignalStrength().getDbm();
                                    }
                                }
                            }

                            float si_throughput = WebBrowsingUtils.SIThroughtputWebBrowsing(throughput);
                            float si_rssi = WebBrowsingUtils.SIRssiWebBrowsing(rssi);
                            float si_rsrp = WebBrowsingUtils.SIRsrpWebBrowsing(rsrp);

                            double http1_1 = WebBrowsingUtils.calculateWebBrowsingHttp1_1(si_throughput, si_rsrp, si_rssi);
                            double http1_1tls = WebBrowsingUtils.calculateWebBrowsingHttp1_1TLS(si_throughput, si_rsrp, si_rssi);
                            double http2 = WebBrowsingUtils.calculateWebBrowsingHttp2(si_throughput, si_rsrp, si_rssi);

                            String better_http = WebBrowsingUtils.getBetterWebBrowsingService(http1_1, http1_1tls, http2);

                            long current_timestamp = System.currentTimeMillis();
                            Date current_date = new Date(current_timestamp);
                            Locale currentLocale = context.getResources().getConfiguration().locale;
                            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", currentLocale);
                            String date = formatter.format(current_date);

                            DecimalFormat decimalFormatSI = new DecimalFormat("0.0000");
                            DecimalFormat decimalFormat = new DecimalFormat("0.000000");
                            String result = getResources().getString(R.string.web_browsing_result, date, WebBrowsingUtils.urlWebBrowsingArray[counter], throughput, rssi, rsrp,
                                    decimalFormatSI.format(si_throughput), decimalFormatSI.format(si_rsrp), decimalFormatSI.format(si_rssi),
                                    decimalFormat.format(http1_1), decimalFormat.format(http1_1tls), decimalFormat.format(http2), better_http);

                            arrayAdapter.insert(result, 0);

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    arrayAdapter.notifyDataSetChanged();
                                }
                            });

                            saveData = new SaveData(android_id, "http", null, better_http, throughput, si_throughput,
                                rssi, si_rssi, rsrp, si_rsrp, current_timestamp);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                saveData.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                            else
                                saveData.execute((Void[]) null);

                            counter++;
                            if (counter < WebBrowsingUtils.urlWebBrowsingArray.length && !isFinished) {
                                access = true;

                                // Reset values
                                throughput = -1;
                                rssi = 1;
                                rsrp = 1;

                                callHttp(WebBrowsingUtils.urlWebBrowsingArray[counter]);

                            } else {
                                // Reset counter
                                counter = 0;

                                access = false;

                                // Reset values
                                throughput = -1;
                                rssi = 1;
                                rsrp = 1;

                                isFinished = true;

                                selected_option = -1;

                                stopPhoneStateListener();
                            }
                        }
                    }
                    break;

                case VIDEO_STREAMING_OPTION:
                    if (action.equals(NAPPLYTICS_ACTION)) {

                        if (intent.getIntExtra(RSSI_RESULT, 1) != 1) {
                            rssi = intent.getIntExtra(RSSI_RESULT, 1);
                        }

                        if (access && throughput != -1 && rssi != 1) {
                            access = false;

                            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                            List<CellInfo> cellInfoList = telephonyManager.getAllCellInfo();
                            for (CellInfo cellInfo : cellInfoList) {
                                if (cellInfo instanceof CellInfoLte) {
                                    if (cellInfo.isRegistered()) {
                                        rsrp = ((CellInfoLte) cellInfo).getCellSignalStrength().getDbm();
                                    }
                                }
                            }

                            float si_throughput, si_rssi, si_rsrp;
                            double http, rtsp;

                            if (network_type == LTE_NETWORK){
                                si_throughput = VideoStreamingUtils.SIThroughputVideoStreamingLTE(throughput);
                                si_rssi = VideoStreamingUtils.SIRssiVideoStreamingLTE(rssi);
                                si_rsrp = VideoStreamingUtils.SIRsrpVideoStreamingLTE(rsrp);

                                http = VideoStreamingUtils.calculateVideoStreamingHttpLTE(si_throughput, si_rsrp, si_rssi);
                                rtsp = VideoStreamingUtils.calculateVideoStreamingRTSPLTE(si_throughput, si_rsrp, si_rssi);

                            } else {
                                si_throughput = VideoStreamingUtils.SIThroughputVideoStreamingHSPA(throughput);
                                si_rssi = VideoStreamingUtils.SIRssiVideoStreamingHSPA(rssi);
                                si_rsrp = VideoStreamingUtils.SIRsrpVideoStreamingHSPA(rsrp);

                                http = VideoStreamingUtils.calculateVideoStreamingHttpHSPA(si_throughput, si_rsrp, si_rssi);
                                rtsp = VideoStreamingUtils.calculateVideoStreamingRTSPHSPA(si_throughput, si_rsrp, si_rssi);
                            }

                            String better_video = VideoStreamingUtils.calculateBetterVideoStreamingService(http, rtsp);

                            long current_timestamp = System.currentTimeMillis();
                            Date current_date = new Date(current_timestamp);
                            Locale currentLocale = getResources().getConfiguration().locale;
                            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", currentLocale);
                            String date = formatter.format(current_date);

                            DecimalFormat decimalFormatSI = new DecimalFormat("0.0000");
                            DecimalFormat decimalFormat = new DecimalFormat("0.000000");
                            String result = getResources().getString(R.string.video_streaming_result, date,
                                    urlServerVideoArray[counter], VideoStreamingUtils.networkTypeArray[network_type], throughput, rssi, rsrp,
                                    decimalFormatSI.format(si_throughput), decimalFormatSI.format(si_rsrp), decimalFormatSI.format(si_rssi),
                                    decimalFormat.format(http), decimalFormat.format(rtsp), better_video);
                            arrayAdapter.insert(result, 0);

                            runOnUiThread(new Runnable() {
                                public void run() {
                                    arrayAdapter.notifyDataSetChanged();
                                }
                            });

                            saveData = new SaveData(android_id, "video", VideoStreamingUtils.networkTypeArray[counter],
                                    better_video, throughput, si_throughput, rssi, si_rssi, rsrp,
                                    si_rsrp, current_timestamp);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                                saveData.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                            else
                                saveData.execute((Void[]) null);

                            counter++;
                            if (counter < urlServerVideoArray.length && !isFinished) {
                                access = true;

                                // Reset values
                                throughput = -1;
                                rssi = 1;
                                rsrp = 1;
                                total_size = 0;

                                callVideo(urlServerVideoArray[counter]);

                            } else {
                                // Reset counter
                                counter = 0;
                                total_size = 0;

                                access = false;

                                // Reset values
                                throughput = -1;
                                rssi = 1;
                                rsrp = 1;

                                network_type = HSPA_NETWORK;

                                isFinished = true;

                                selected_option = -1;

                                stopPhoneStateListener();
                            }
                        }
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_clear_white_24dp);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        checkPermissions();

        console_lv = (ListView) findViewById(R.id.console_lv);
        arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_item);
        console_lv.setAdapter(arrayAdapter);

        names_sp = (Spinner) findViewById(R.id.names_sp);
        List<String> stringArrayList = new ArrayList<>();
        stringArrayList.add(WEB_BROWSING_SERVICE);
        stringArrayList.add(VIDEO_STREAMING_SERVICE);
        stringArrayAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.support_simple_spinner_dropdown_item, stringArrayList);
        stringArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        names_sp.setAdapter(stringArrayAdapter);

        api = API.getAPI(MainActivity.this, NAPPLYTICS_ACTION);

        android_id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        Button run_bt = (Button) findViewById(R.id.run_bt);
        run_bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                access = true;

                String selected_item = names_sp.getSelectedItem().toString();
                switch (selected_item) {
                    case WEB_BROWSING_SERVICE:
                        if (isFinished) {
                            selected_option = WEB_BROWSING_OPTION;

                            isFinished = false;

                            startPhoneStateListener();

                            callHttp(WebBrowsingUtils.urlWebBrowsingArray[counter]);
                        }
                        break;

                    case VIDEO_STREAMING_SERVICE:
                        if (isFinished) {
                            selected_option = VIDEO_STREAMING_OPTION;

                            isFinished = false;

                            startPhoneStateListener();

                            callVideo(urlVideoStreamingDashModeArray[counter]);
                        }
                        break;
                }
            }
        });

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(NAPPLYTICS_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    private void callHttp(String url) {
        // Reset values
        throughput = -1;
        rssi = 1;
        rsrp = 1;

        HashMap<String, String> params = new HashMap<>();
        params.put("url", url);

        try {
            MeasurementTask measurementTask = api.createTask(API.TaskType.HTTP, Calendar.getInstance().getTime(),
                    null, 5, 1L, MeasurementTask.USER_PRIORITY, 1, params, MainActivity.this);

            if (measurementTask != null) {
                api.submitTask(measurementTask);
            }

        } catch (MeasurementError measurementError) {
            measurementError.printStackTrace();
        }
    }

    private void callVideo(String url) {
        // Reset values
        throughput = -1;
        rssi = 1;
        rsrp = 1;
        total_size = 0;

        shouldAutoPlay = true;

        Handler eventHandler = new Handler();
        bandwidthMeter = new DefaultBandwidthMeter(eventHandler, this);
        mediaDataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this,
                "mediaPlayerSample"), (TransferListener<? super DataSource>) bandwidthMeter);

        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

        player.setPlayWhenReady(shouldAutoPlay);

        MediaSource mediaSource = null;

        switch (video_mode) {
            case VideoStreamingUtils.HLS_MODE:
                mediaSource = new HlsMediaSource(Uri.parse(url), mediaDataSourceFactory, eventHandler, this);
                break;

            case VideoStreamingUtils.DASH_MODE:
                mediaSource = new DashMediaSource(Uri.parse(url), mediaDataSourceFactory,
                        new DefaultDashChunkSource.Factory(mediaDataSourceFactory), eventHandler, this);
                break;

            case VideoStreamingUtils.MP4_MODE:
                DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

                mediaSource = new ExtractorMediaSource(Uri.parse(url), mediaDataSourceFactory,
                        extractorsFactory, eventHandler, this);
                break;
        }

        player.addListener(this);
        player.prepare(mediaSource);
    }

    //RSSI
    private void startPhoneStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        int currentNetworkType = telephonyManager.getNetworkType();
        if (currentNetworkType == TelephonyManager.NETWORK_TYPE_LTE){
            network_type = VideoStreamingUtils.LTE_NETWORK;

        } else {
            network_type = VideoStreamingUtils.HSPA_NETWORK;
        }
        Log.d("Network_type", VideoStreamingUtils.networkTypeArray[network_type]);

        customPhoneStateListener = new CustomPhoneStateListener(this);
        telephonyManager.listen(customPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    private void stopPhoneStateListener() {
        telephonyManager.listen(customPhoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE}, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("PERMISSION", "ACCEPTED");

                } else {
                    Log.i("PERMISSION", "DENIED");
                }

                return;
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        return super.dispatchTouchEvent(ev);
    }

    public class SaveData extends AsyncTask<Void, Void, String> {
        private final String android_id;
        private final String request_type;
        private final String network_type;
        private final String better_option;
        private final long throughput;
        private final float si_throughput;
        private final int rssi;
        private final float si_rssi;
        private final int rsrp;
        private final float si_rsrp;
        private final long timestamp;

        SaveData(String android_id, String request_type, String network_type,
                 String better_option, long throughput, float si_throughput, int rssi,
                 float si_rssi, int rsrp, float si_rsrp, long timestamp) {
            this.android_id = android_id;
            this.request_type = request_type;
            this.network_type = network_type;
            this.better_option = better_option;
            this.throughput = throughput;
            this.si_throughput = si_throughput;
            this.rssi = rssi;
            this.si_rssi = si_rssi;
            this.rsrp = rsrp;
            this.si_rsrp = si_rsrp;
            this.timestamp = timestamp;
        }

        @Override
        protected String doInBackground(Void... params) {
            String success;

            boolean isConnected = NetworkUtils.isNetworkAvailable(MainActivity.this);
            if (isConnected) {
                success = NetworkUtils.saveData(android_id, request_type, network_type, better_option,
                        throughput, si_throughput, rssi, si_rssi, rsrp, si_rsrp, timestamp);

            } else {
                success = NetworkUtils.CODE_NOT_CONNECTION;
            }

            return success;
        }

        @Override
        protected void onPostExecute(final String success) {
            if (success.equals(NetworkUtils.CODE_OK)) {
                //myHandler.sendEmptyMessage(Utils.SAVE_USER_OK);

            } else if (success.equals(NetworkUtils.CODE_ERR)) {
                //myHandler.sendEmptyMessage(Utils.SAVE_USER_ERROR);

            } else if (success.equals(NetworkUtils.CODE_NOT_CONNECTION)) {
                //myHandler.sendEmptyMessage(Utils.SAVE_USER_ERROR);
            }

            saveData = null;
        }

        @Override
        protected void onCancelled() {
            saveData = null;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                // Close to application
                System.exit(0);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLoadStarted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                              int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                              long mediaEndTimeMs, long elapsedRealtimeMs) {}

    @Override
    public void onLoadCompleted(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                                int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                                long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {}

    @Override
    public void onLoadCanceled(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                               int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                               long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded) {}

    @Override
    public void onLoadError(DataSpec dataSpec, int dataType, int trackType, Format trackFormat,
                            int trackSelectionReason, Object trackSelectionData, long mediaStartTimeMs,
                            long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs, long bytesLoaded,
                            IOException error, boolean wasCanceled) {
        Log.d("VIDEO_ERROR", error.toString());
    }

    @Override
    public void onUpstreamDiscarded(int trackType, long mediaStartTimeMs, long mediaEndTimeMs) {}

    @Override
    public void onDownstreamFormatChanged(int trackType, Format trackFormat, int trackSelectionReason,
                                          Object trackSelectionData, long mediaTimeMs) {}

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrate) {
        Log.d("VIDEO_BANDWIDTH", elapsedMs + " ms, " + bytes + " B, " + bitrate + " bitrate (bps)");

        total_size += bytes;
        total_ms += elapsedMs;
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {}

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {}

    @Override
    public void onLoadingChanged(boolean isLoading) {}

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        String log = "";
        switch (playbackState) {
            case Player.STATE_IDLE:
                log = "State: Idle";
                break;

            case Player.STATE_BUFFERING:
                log = "State: Buffering";
                break;

            case Player.STATE_READY:
                log = "State: Ready";
                break;

            case Player.STATE_ENDED:
                log = "State: Ended";

                Log.d("VIDEO_TOTAL", "Size: " + total_size + " B, time: " + total_ms + " ms");

                throughput = ((total_size * 8) / 1000000) / (total_ms / 1000); // video_throughput -> Mbps (1000000 = 1000 x 1000)

                Intent intent = new Intent(NAPPLYTICS_ACTION);
                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(intent);
                break;
        }

        Log.d("VIDEO_STATE", "PlayWhenReady: " + playWhenReady + ", " + log);
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {}

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.d("VIDEO_PLAYER_ERROR", error.toString());
    }

    @Override
    public void onPositionDiscontinuity() {}

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

    @Override
    public void onLoadError(IOException error) {
        Log.d("VIDEO_ERROR", error.toString());
    }

    @Override
    protected void onDestroy() {
        Logger.d("onDestroy called");
        super.onDestroy();

        api.unbind();

        try {
            this.unregisterReceiver(broadcastReceiver);

        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }

        try {
            stopPhoneStateListener();

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}