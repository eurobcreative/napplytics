package com.eurobcreative.monroe.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import com.eurobcreative.monroe.CustomPhoneStateListener;
import com.eurobcreative.monroe.MeasurementTask;
import com.eurobcreative.monroe.R;
import com.eurobcreative.monroe.UpdateIntent;
import com.eurobcreative.monroe.UtilsMonroe;
import com.eurobcreative.monroe.api.API;
import com.eurobcreative.monroe.exceptions.MeasurementError;
import com.eurobcreative.monroe.measurements.DnsLookupTask;
import com.eurobcreative.monroe.measurements.HttpTask;
import com.eurobcreative.monroe.measurements.PingTask;
import com.eurobcreative.monroe.measurements.TCPThroughputTask;
import com.eurobcreative.monroe.measurements.TracerouteTask;
import com.eurobcreative.monroe.measurements.UDPBurstTask;
import com.eurobcreative.monroe.measurements.VideoQoETask;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.MLabNS;
import com.eurobcreative.monroe.util.video.util.DemoUtil;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.eurobcreative.monroe.UtilsMonroe.MONROE_ACTION;
import static com.eurobcreative.monroe.UtilsMonroe.RSSI_RESULT;
import static com.eurobcreative.monroe.UtilsMonroe.SPEED_RESULT;

public class MainActivity extends AppCompatActivity {

    private API api;
    private String URL = "www.google.com";

    private ListView console_lv;
    private ArrayAdapter<String> arrayAdapter;
    private BroadcastReceiver receiver;
    //private ProgressBar progressBar;
    //MeasurementScheduler scheduler = null;
    private boolean userResultsActive = false;

    private Spinner names_sp;
    private ArrayAdapter<String> stringArrayAdapter;
    private Button run_bt;

    private long speed = -1;
    private int rssi = 1;
    private int rsrp = 1;
    private boolean access = true;

    private TelephonyManager tManager;
    private EditText url_et;

    private final static String OUTPUT_PATH = "/data/data/com.eurobcreative.monroe/files/";

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            /*if (action.equals(api.userResultAction) || action.equals(API.SERVER_RESULT_ACTION)) {
                Logger.d("Get user result");

                Parcelable[] parcels = intent.getParcelableArrayExtra(UpdateIntent.RESULT_PAYLOAD);
                MeasurementResult[] measurementResults;
                if (parcels != null) {
                    measurementResults = new MeasurementResult[parcels.length];
                    for (int i = 0; i < measurementResults.length; i++) {
                        String result = parcels[i].toString() + "\n";
                        Logger.d("************* RESULT: ***********" + result);
                        arrayAdapter.add(result);
                        measurementResults[i] = (MeasurementResult) parcels[i];
                        try {
                            File file = new File(OUTPUT_PATH);//Environment.getExternalStorageDirectory().toString()//getFilesDir().getAbsolutePath());
                            if (!file.exists()) {
                                file.mkdirs();
                            }
                            File filepath = new File(file, "output.txt");
                            FileOutputStream fileOutputStream = new FileOutputStream(filepath, true);
                            fileOutputStream.write(result.getBytes());
                            fileOutputStream.flush();
                            fileOutputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    arrayAdapter.notifyDataSetChanged();
                }

            } else*/ if (action.equals(MONROE_ACTION)) {
                if (intent.getLongExtra(SPEED_RESULT, -1) != -1) {
                    speed = intent.getLongExtra(SPEED_RESULT, -1);
                }

                if (intent.getIntExtra(RSSI_RESULT, 1) != 1) {
                    rssi = intent.getIntExtra(RSSI_RESULT, 1);
                }

                if (access && speed != -1 && rssi != 1) {
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
                    float si_speed = UtilsMonroe.SISpeed(speed);
                    float si_rssi = UtilsMonroe.SIRssi(rssi);
                    float si_rsrp = UtilsMonroe.SIRsrp(rsrp);
                    String better_http = UtilsMonroe.betterHttpMode(si_speed, si_rsrp, si_rssi);

                    double http1_1 = (0.275906516431794 * si_speed) + (0.20608960296224 * si_rsrp) + (0.187881476178343 * si_rssi);
                    double http2 = (0.274590130897324 * si_speed) + (0.224955809413482 * si_rsrp) + (0.200835527674662 * si_rssi);
                    double http1_1tls = (0.273217929983119 * si_speed) + (0.224515432839308 * si_rsrp) + (0.191683339409728 * si_rssi);

                    /*arrayAdapter.insert("URL: " + URL + ";\n" +
                            "TIMESTAMP: " + timestamp + ";\n" +
                            "SPEED = " + speed + " kbps, SI_SPEED = " + si_speed + ";\n" +
                            "RSSI = " + rssi + " dBm, SI_RSSI = " + si_rssi + ";\n" +
                            "RSRP = " + rsrp + " dBm, SI_RSRP = " + si_rsrp + ";\n\n" +

                            "HTTP1_1: " + http1_1 + ";\n" +
                            "HTTP1_1TLS: " + http1_1tls + ";\n" +
                            "HTTP2: " + http2 + ";\n" +
                            "THE BEST OPTION IS: " + better_http, 0);*/

                    Date today = new Date(System.currentTimeMillis());
                    Locale currentLocale = context.getResources().getConfiguration().locale;
                    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", currentLocale);
                    String date = formatter.format(today);

                    DecimalFormat decimalFormat = new DecimalFormat("0.000000");
                    String result = getResources().getString(R.string.result, date, URL, speed, rssi, rsrp,
                            decimalFormat.format(http1_1), decimalFormat.format(http1_1tls), decimalFormat.format(http2), better_http);
                    arrayAdapter.insert(result, 0);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            arrayAdapter.notifyDataSetChanged();
                        }
                    });
                    //switchBetweenResults(true);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Logger.d("onCreate called");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_clear_white_24dp);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //RSSI
        tManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        tManager.listen(new CustomPhoneStateListener(this), PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        //TODO: ERROR
        /*TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        CellInfoGsm cellinfogsm = (CellInfoGsm)telephonyManager.getAllCellInfo().get(0);
        CellSignalStrengthGsm cellSignalStrengthGsm = cellinfogsm.getCellSignalStrength();
        Log.d("SIGNAL_STRENGTH", Integer.toString(cellSignalStrengthGsm.getDbm()));*/

        checkPermissions();

        console_lv = (ListView) findViewById(R.id.console_lv);
        arrayAdapter = new ArrayAdapter<>(getApplicationContext(), R.layout.list_item);
        console_lv.setAdapter(arrayAdapter);

        /*progressBar = (ProgressBar) findViewById(progress_bar);
        progressBar.setMax(Config.MAX_PROGRESS_BAR_VALUE);
        progressBar.setProgress(Config.MAX_PROGRESS_BAR_VALUE);*/
        userResultsActive = true;

        names_sp = (Spinner) findViewById(R.id.names_sp);
        List<String> stringArrayList = new ArrayList<>();
        //stringArrayList.addAll(API.getMeasurementNames());
        stringArrayList.add(HttpTask.DESCRIPTOR);
        stringArrayAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.support_simple_spinner_dropdown_item, stringArrayList);
        stringArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        names_sp.setAdapter(stringArrayAdapter);

        url_et = (EditText) findViewById(R.id.url_et);

        api = API.getAPI(MainActivity.this, MONROE_ACTION);

        run_bt = (Button) findViewById(R.id.run_bt);
        run_bt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                access = true;

                HashMap<String, String> params = new HashMap<>();

                String selected_item = names_sp.getSelectedItem().toString();
                switch (selected_item) {
                    case DnsLookupTask.DESCRIPTOR:
                        params.put("target", URL);
                        try {
                            //api.getAuthenticateAccount();
                            MeasurementTask measurementTask = api.createTask(API.TaskType.DNSLOOKUP, Calendar.getInstance().getTime(),
                                    null, 5, 1L, MeasurementTask.USER_PRIORITY, 1, params);

                            if (measurementTask != null) {
                                api.submitTask(measurementTask);
                            }
                        } catch (MeasurementError measurementError) {
                            measurementError.printStackTrace();
                        }
                        break;

                    case HttpTask.DESCRIPTOR:
                        URL = url_et.getText().toString();
                        if (!URL.isEmpty()) {
                            params.put("url", URL);
                            params.put("method", "get");
                            try {
                                //api.getAuthenticateAccount();
                                MeasurementTask measurementTask = api.createTask(API.TaskType.HTTP, Calendar.getInstance().getTime(),
                                        null, 5, 1L, MeasurementTask.USER_PRIORITY, 1, params, MainActivity.this);

                                if (measurementTask != null) {
                                    api.submitTask(measurementTask);
                                }
                            } catch (MeasurementError measurementError) {
                                measurementError.printStackTrace();
                            }
                        } else {
                            url_et.setError("This field is required");
                        }
                        break;

                    case PingTask.DESCRIPTOR:
                        params.put("target", URL);
                        try {
                            //api.getAuthenticateAccount();
                            MeasurementTask measurementTask = api.createTask(API.TaskType.PING, Calendar.getInstance().getTime(),
                                    null, 5, 1L, MeasurementTask.USER_PRIORITY, 1, params);

                            if (measurementTask != null) {
                                api.submitTask(measurementTask);
                            }
                        } catch (MeasurementError measurementError) {
                            measurementError.printStackTrace();
                        }
                        break;

                    case TracerouteTask.DESCRIPTOR:
                        params.put("target", URL);
                        try {
                            //api.getAuthenticateAccount();
                            MeasurementTask measurementTask = api.createTask(API.TaskType.TRACEROUTE, Calendar.getInstance().getTime(),
                                    null, 5, 1L, MeasurementTask.USER_PRIORITY, 1, params);

                            if (measurementTask != null) {
                                api.submitTask(measurementTask);
                            }
                        } catch (MeasurementError measurementError) {
                            measurementError.printStackTrace();
                        }
                        break;

                    case TCPThroughputTask.DESCRIPTOR:
                        params.put("target", MLabNS.TARGET);
                        params.put("dir_up", "Up");
                        try {
                            //api.getAuthenticateAccount();
                            MeasurementTask measurementTask = api.createTask(API.TaskType.TCPTHROUGHPUT, Calendar.getInstance().getTime(),
                                    null, 5, 1, MeasurementTask.USER_PRIORITY, 5, params);

                            if (measurementTask != null) {
                                api.submitTask(measurementTask);
                            }
                        } catch (MeasurementError measurementError) {
                            measurementError.printStackTrace();
                        }
                        break;

                    case UDPBurstTask.DESCRIPTOR:
                        // m-lab.
                        params.put("target", MLabNS.TARGET);
                        params.put("direction", "Up");
                        // Get UDP Burst packet size
                        params.put("packet_size_byte", "100");
                        // Get UDP Burst packet count
                        params.put("packet_burst", "16");
                        // Get UDP Burst interval
                        params.put("udp_interval", "1");
                        try {
                            //api.getAuthenticateAccount();
                            MeasurementTask measurementTask = api.createTask(API.TaskType.UDPBURST, Calendar.getInstance().getTime(),
                                    null, 5, 1, MeasurementTask.USER_PRIORITY, 5, params);

                            if (measurementTask != null) {
                                api.submitTask(measurementTask);
                            }
                        } catch (MeasurementError measurementError) {
                            measurementError.printStackTrace();
                        }
                        break;

                    case VideoQoETask.DESCRIPTOR:
                        params.put("content_url", "http://yt-dash-mse-test.commondatastorage.googleapis.com/media/car-20120827-manifest.mpd"); //http://www.youtube.com/watch?v=9sg-A-eS6Ig");
                        params.put("content_id", "car-20120827-85");//"9sg-A-eS6Ig");
                        params.put("content_type", String.valueOf(DemoUtil.TYPE_DASH_VOD));
                        try {
                            //api.getAuthenticateAccount();
                            MeasurementTask measurementTask = api.createTask(API.TaskType.VIDEOQOE, Calendar.getInstance().getTime(),
                                    null, 5, 1, MeasurementTask.USER_PRIORITY, 5, params);

                            if (measurementTask != null) {
                                api.submitTask(measurementTask);
                            }
                        } catch (MeasurementError measurementError) {
                            measurementError.printStackTrace();
                        }
                        break;
                }
            }
        });

        receiver = new BroadcastReceiver() {
            @Override
            // All onXyz() callbacks are single threaded
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION)) {
                    //int progress = intent.getIntExtra(UpdateIntent.PROGRESS_PAYLOAD, Config.INVALID_PROGRESS);
                    int priority = intent.getIntExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.INVALID_PRIORITY);
                    // Show user arrayAdapter if there is currently a user measurement running
                    if (priority == MeasurementTask.USER_PRIORITY) {
                        Logger.d("progress update");
                        switchBetweenResults(true);
                    }
                    //upgradeProgress(progress, Config.MAX_PROGRESS_BAR_VALUE);
                } else if (intent.getAction().equals(UpdateIntent.SCHEDULER_CONNECTED_ACTION)) {
                    Logger.d("scheduler connected");
                    switchBetweenResults(userResultsActive);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(UpdateIntent.SCHEDULER_CONNECTED_ACTION);
        filter.addAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        registerReceiver(receiver, filter);

        getConsoleContentFromScheduler();

        IntentFilter intentFilter = new IntentFilter();
        //intentFilter.addAction(api.userResultAction);
        //intentFilter.addAction(API.SERVER_RESULT_ACTION);
        intentFilter.addAction(MONROE_ACTION);
        registerReceiver(broadcastReceiver, intentFilter);
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
    }

    private void checkPermissions(){
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

    /**
     * Change the underlying adapter for the ListView.
     *
     * @param showUserResults If true, show user arrayAdapter; otherwise, show system arrayAdapter.
     */
    private synchronized void switchBetweenResults(boolean showUserResults) {
        userResultsActive = showUserResults;
        getConsoleContentFromScheduler();
        Logger.d("switchBetweenResults: showing " + arrayAdapter.getCount() + " " + (showUserResults ? "user" : "system") + " arrayAdapter");
    }

    /**
     * Upgrades the progress bar in the UI.
     */
    /*private void upgradeProgress(int progress, int max) {
        Logger.d("Progress is " + progress);
        if (progress >= 0 && progress <= max) {
            progressBar.setProgress(progress);
            this.progressBar.setVisibility(View.VISIBLE);
        } else {
            // UserMeasurementTask broadcast a progress greater than max to indicate the termination of
            // the measurement.
            this.progressBar.setVisibility(View.INVISIBLE);
        }
    }*/

    private synchronized void getConsoleContentFromScheduler() {
        Logger.d("ResultsConsoleActivity.getConsoleContentFromScheduler called");
        /*if (scheduler == null) {
            SpeedometerApp parent = (SpeedometerApp) getParent();
            scheduler = parent.getScheduler();
        }*/
        // Scheduler may have not had time to start yet. When it does, the intent above will call this again.
        if (userResultsActive) {
            Logger.d("Updating measurement arrayAdapter from thread " + Thread.currentThread().getName());
            //TODO/////////
            //arrayAdapter.clear();
            //////////
            /*final List<String> scheduler_results = (userResultsActive ? scheduler.getUserResults() : scheduler.getSystemResults());
            for (String result : scheduler_results) {
                arrayAdapter.add(result);
            }*/
            //TODO/////////
            /*runOnUiThread(new Runnable() {
                public void run() {
                    arrayAdapter.notifyDataSetChanged();
                }
            });*/
            ////////
        }
    }

    /*private void showBusySchedulerStatus() {
        Intent intent = new Intent();
        intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        intent.putExtra(UpdateIntent.STATUS_MSG_PAYLOAD, getString(R.string.userMeasurementBusySchedulerToast));
        sendBroadcast(intent);
    }*/

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
            this.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }
}