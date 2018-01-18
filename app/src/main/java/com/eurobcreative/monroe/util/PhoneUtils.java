package com.eurobcreative.monroe.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings.Secure;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.NeighboringCellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import com.eurobcreative.monroe.Config;
import com.eurobcreative.monroe.DeviceInfo;
import com.eurobcreative.monroe.DeviceProperty;
import com.eurobcreative.monroe.R;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Phone related utilities.
 */
@SuppressLint("NewApi")
public class PhoneUtils {
    /**
     * Returned by {@link #getNetwork()}.
     */
    public static final String NETWORK_WIFI = "Wifi";
    /**
     * IP type
     */
    public static final String IP_TYPE_UNKNOWN = "UNKNOWN";
    public static final String IP_TYPE_NONE = "Neither IPv4 nor IPv6";
    public static final String IP_TYPE_IPV4_ONLY = "IPv4 only";
    public static final String IP_TYPE_IPV6_ONLY = "IPv6 only";
    public static final String IP_TYPE_IPV4_IPV6_BOTH = "IPv4 and IPv6";

    public static volatile HashSet<String> clientKeySet = new HashSet<>();
    /**
     * The app that uses this class. The app must remain alive for longer than
     * PhoneUtils objects are in use.
     *
     * @see #setGlobalContext(Context)
     */
    private static Context globalContext = null;

    /**
     * A singleton instance of PhoneUtils.
     */
    private static PhoneUtils singletonPhoneUtils = null;

    /**
     * Phone context object giving access to various phone parameters.
     */
    private Context context = null;

    /**
     * Allows to obtain the phone's location, to determine the country.
     */
    private LocationManager locationManager = null;

    /**
     * The name of the location provider with "coarse" precision (cell/wifi).
     */
    private String locationProviderName = null;

    /**
     * Allows to disable going to low-power mode where WiFi gets turned off.
     */
    WakeLock wakeLock = null;

    /**
     * Call initNetworkManager() before using this var.
     */
    private ConnectivityManager connectivityManager = null;

    /**
     * Call initNetworkManager() before using this var.
     */
    private TelephonyManager telephonyManager = null;

    /**
     * Tells whether the phone is charging
     */
    private boolean isCharging;
    /**
     * Current battery level in percentage
     */
    private int curBatteryLevel;
    /**
     * Receiver that handles battery change broadcast intents
     */
    private BroadcastReceiver broadcastReceiver;
    private String currentSignalStrength = "UNKNOWN";

    private DeviceInfo deviceInfo = null;
    /**
     * IP compatibility status
     */
    // Indeterministic type due to client side timer expired
    private int IP_TYPE_CANNOT_DECIDE = 2;
    // Cannot resolve the hostname or cannot reach the destination address
    private int IP_TYPE_UNCONNECTIVITY = 1;
    private int IP_TYPE_CONNECTIVITY = 0;
    /**
     * Domain name resolution status
     */
    private int DN_UNKNOWN = 2;
    private int DN_UNRESOLVABLE = 1;
    private int DN_RESOLVABLE = 0;
    //server configuration port on M-Lab servers
    private int portNum = 6003;
    private int tcpTimeout = 3000;

    protected PhoneUtils(Context context) {
        this.context = context;
        broadcastReceiver = new PowerStateChangeReceiver();
        // Registers a receiver for battery change events.
        Intent powerIntent = globalContext.registerReceiver(broadcastReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateBatteryStat(powerIntent);
    }

    /**
     * The owner app class must call this method from its onCreate(), before
     * getPhoneUtils().
     */
    public static synchronized void setGlobalContext(Context newGlobalContext) {
        assert newGlobalContext != null;
        assert singletonPhoneUtils == null;  // Should not yet be created
        // Not supposed to change the owner app
        assert globalContext == null || globalContext == newGlobalContext;

        globalContext = newGlobalContext;
    }

    public static synchronized void releaseGlobalContext() {
        globalContext = null;
        singletonPhoneUtils = null;
    }

    /**
     * Returns a singleton instance of PhoneUtils. The caller must call
     * {@link #setGlobalContext(Context)} before calling this method.
     */
    public static synchronized PhoneUtils getPhoneUtils() {

        if (singletonPhoneUtils == null) {
            assert globalContext != null;
            singletonPhoneUtils = new PhoneUtils(globalContext);
        }

        return singletonPhoneUtils;
    }

    /**
     * Lazily initializes the network managers.
     * <p>
     * As a side effect, assigns connectivityManager and telephonyManager.
     */
    private synchronized void initNetwork() {
        if (connectivityManager == null) {
            ConnectivityManager tryConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            TelephonyManager tryTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

            // Assign to member vars only after all the get calls succeeded,
            // so that either all get assigned, or none get assigned.
            connectivityManager = tryConnectivityManager;
            telephonyManager = tryTelephonyManager;

            // Some interesting info to look at in the logs
            NetworkInfo[] infos = connectivityManager.getAllNetworkInfo();
            for (NetworkInfo networkInfo : infos) {
                Logger.i("Network: " + networkInfo);
            }
            Logger.i("Phone type: " + getTelephonyPhoneType() + ", Carrier: " + getTelephonyCarrierName());
        }
        assert connectivityManager != null;
        assert telephonyManager != null;
    }

    /**
     * This method must be called in the service thread, as the system will create a Looper in
     * the calling thread which will handle the callbacks.
     */
    public void registerSignalStrengthListener() {
        initNetwork();
        telephonyManager.listen(new SignalStrengthChangeListener(), PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    }

    /**
     * Returns the network that the phone is on (e.g. Wifi, Edge, GPRS, etc).
     */
    public String getNetwork() {
        initNetwork();
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
            Logger.d("Current Network: WIFI");
            return NETWORK_WIFI;
        } else {
            return getTelephonyNetworkType();
        }
    }

    /**
     * Detect whether there is an Internet connection available
     */
    public boolean isNetworkAvailable() {
        initNetwork();
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private static final String[] NETWORK_TYPES = {
            "UNKNOWN",  // 0  - NETWORK_TYPE_UNKNOWN
            "GPRS",     // 1  - NETWORK_TYPE_GPRS
            "EDGE",     // 2  - NETWORK_TYPE_EDGE
            "UMTS",     // 3  - NETWORK_TYPE_UMTS
            "CDMA",     // 4  - NETWORK_TYPE_CDMA
            "EVDO_0",   // 5  - NETWORK_TYPE_EVDO_0
            "EVDO_A",   // 6  - NETWORK_TYPE_EVDO_A
            "1xRTT",    // 7  - NETWORK_TYPE_1xRTT
            "HSDPA",    // 8  - NETWORK_TYPE_HSDPA
            "HSUPA",    // 9  - NETWORK_TYPE_HSUPA
            "HSPA",     // 10 - NETWORK_TYPE_HSPA
            "IDEN",     // 11 - NETWORK_TYPE_IDEN
            "EVDO_B",   // 12 - NETWORK_TYPE_EVDO_B
            "LTE",      // 13 - NETWORK_TYPE_LTE
            "EHRPD",    // 14 - NETWORK_TYPE_EHRPD
            "HSPAP",    // 15 - NETWORK_TYPE_HSPAP
    };

    /**
     * Returns mobile data network connection type.
     */
    public String getTelephonyNetworkType() {
        assert NETWORK_TYPES[14].compareTo("EHRPD") == 0;

        int networkType = telephonyManager.getNetworkType();
        if (networkType < NETWORK_TYPES.length) {
            return NETWORK_TYPES[telephonyManager.getNetworkType()];
        } else {
            return "Unrecognized: " + networkType;
        }
    }

    /**
     * Returns "GSM", "CDMA".
     */
    private String getTelephonyPhoneType() {
        switch (telephonyManager.getPhoneType()) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                return "CDMA";
            case TelephonyManager.PHONE_TYPE_GSM:
                return "GSM";
            case TelephonyManager.PHONE_TYPE_NONE:
                return "None";
        }
        return "Unknown";
    }

    /**
     * Returns current mobile phone carrier name, or empty if not connected.
     */
    private String getTelephonyCarrierName() {
        return telephonyManager.getNetworkOperatorName();
    }

    /**
     * Returns the information about cell towers in range. Returns null if the information is
     * not available
     * <p>
     * TODO(wenjiezeng): As folklore has it and Wenjie has confirmed, we cannot get cell info from
     * Samsung phones.
     */
    public String getCellInfo(boolean cidOnly) {
        initNetwork();
        List<NeighboringCellInfo> infos = telephonyManager.getNeighboringCellInfo();
        StringBuffer buf = new StringBuffer();
        String tempResult;
        if (infos.size() > 0) {
            for (NeighboringCellInfo info : infos) {
                tempResult = cidOnly ? info.getCid() + ";" : info.getLac() + "," + info.getCid() + "," + info.getRssi() + ";";
                buf.append(tempResult);
            }
            // Removes the trailing semicolon
            buf.deleteCharAt(buf.length() - 1);
            return buf.toString();
        } else {
            return null;
        }
    }

    /**
     * Lazily initializes the location manager.
     * <p>
     * As a side effect, assigns locationManager and locationProviderName.
     */
    private synchronized void initLocation() {
        if (locationManager == null) {
            LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            Criteria criteriaCoarse = new Criteria();
            /* "Coarse" accuracy means "no need to use GPS". Typically a gShots phone would be located
             * in a building, and GPS may not be able to acquire a location. We only care about the
             * location to determine the country, so we don't need a super accurate location, cell/wifi is good enough.
			 */
            criteriaCoarse.setAccuracy(Criteria.ACCURACY_COARSE);
            criteriaCoarse.setPowerRequirement(Criteria.POWER_LOW);
            String providerName = manager.getBestProvider(criteriaCoarse, true);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                // ActivityCompat#requestPermissions here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            /* Make sure the provider updates its location. Without this, we may get a very old location, even a
             * device powercycle may not update it. {@see android.location.LocationManager.getLastKnownLocation}.
			 */
            manager.requestLocationUpdates(providerName, 0, 0, new LoggingLocationListener(), Looper.getMainLooper());
            locationManager = manager;
            locationProviderName = providerName;
        }
        assert locationManager != null;
        assert locationProviderName != null;
    }

    /**
     * Returns the location of the device.
     *
     * @return the location of the device
     */
    public Location getLocation() {
        if (!(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            return new Location("unknown");
        }

        try {
            initLocation();
            Location location = locationManager.getLastKnownLocation(locationProviderName);
            if (location == null) {
                Logger.e("Cannot obtain location from provider " + locationProviderName);
                return new Location("unknown");
            }
            return location;
        } catch (IllegalArgumentException e) {
            Logger.e("Cannot obtain location", e);
            return new Location("unknown");
        }
    }

    /**
     * Wakes up the CPU of the phone if it is sleeping.
     */
    public synchronized void acquireWakeLock() {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tag");
        }
        Logger.d("PowerLock acquired");
        wakeLock.acquire();
    }

    /**
     * Release the CPU wake lock. WakeLock is reference counted by default: no need to worry
     * about releasing someone else's wake lock
     */
    public synchronized void releaseWakeLock() {
        if (wakeLock != null) {
            try {
                wakeLock.release();
                Logger.i("PowerLock released");
            } catch (RuntimeException e) {
                Logger.e("Exception when releasing wakeup lock", e);
            }
        }
    }

    /**
     * Release all resource upon app shutdown
     */
    public synchronized void shutDown() {
        if (this.wakeLock != null) {
			/* Wakelock are ref counted by default. We disable this feature here to ensure that
			 * the power lock is released upon shutdown.
			 */
            wakeLock.setReferenceCounted(false);
            wakeLock.release();
        }
        context.unregisterReceiver(broadcastReceiver);
        releaseGlobalContext();
    }

    /**
     * A dummy listener that just logs callbacks.
     */
    private static class LoggingLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            Logger.d("location changed");
        }

        @Override
        public void onProviderDisabled(String provider) {
            Logger.d("provider disabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Logger.d("provider enabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Logger.d("status changed: " + provider + "=" + status);
        }
    }

    public String getAppVersionName() {
        try {
            String packageName = context.getPackageName();
            return context.getPackageManager().getPackageInfo(packageName, 0).versionName;

        } catch (Exception e) {
            Logger.e("version name of the application cannot be found", e);
        }
        return "Unknown";
    }

    /**
     * Returns the current battery level
     */
    public synchronized int getCurrentBatteryLevel() {
        return curBatteryLevel;
    }

    /**
     * Returns if the batter is charing
     */
    public synchronized boolean isCharging() {
        return isCharging;
    }

    /**
     * Sets the current RSSI value
     */
    public synchronized void setCurrentRssi(String rssi) {
        currentSignalStrength = rssi;
    }

    /**
     * Returns the last updated RSSI value
     */
    public synchronized String getCurrentRssi() {
        initNetwork();
        return currentSignalStrength;
    }

    public String getCellRssi() {
        String cellRssi1 = getCurrentRssi();
        String cellRssi2 = "";
        HashMap<String, Integer> cellInfosMap = getAllCellInfoSignalStrength();
        for (String cinfo : cellInfosMap.keySet()) {
            cellRssi2 += cinfo + ":" + cellInfosMap.get(cinfo) + "|";
        }

        return cellRssi1 + cellRssi2;
    }


    public String getWifiBSSID() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            return wifiInfo.getBSSID();
        }
        return null;
    }

    public String getWifiSSID() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            return wifiInfo.getSSID();
        }
        return null;
    }

    public String getWifiIpAddress() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            int ip = wifiInfo.getIpAddress();
            if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
                ip = Integer.reverseBytes(ip);
            }
            byte[] bytes = BigInteger.valueOf(ip).toByteArray();
            String address;
            try {
                address = InetAddress.getByAddress(bytes).getHostAddress();
                return address;
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }

        }
        return null;
    }

    public HashMap<String, Integer> getAllCellInfoSignalStrength() {
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();

        HashMap<String, Integer> results = new HashMap<>();

        if (cellInfos == null) {
            return results;
        }

        for (CellInfo cellInfo : cellInfos) {
            if (cellInfo.getClass().equals(CellInfoLte.class)) {
                results.put("LTE", ((CellInfoLte) cellInfo).getCellSignalStrength().getDbm());
            } else if (cellInfo.getClass().equals(CellInfoGsm.class)) {
                results.put("GSM", ((CellInfoGsm) cellInfo).getCellSignalStrength().getDbm());
            } else if (cellInfo.getClass().equals(CellInfoCdma.class)) {
                results.put("CDMA", ((CellInfoCdma) cellInfo).getCellSignalStrength().getDbm());
            } else if (cellInfo.getClass().equals(CellInfoWcdma.class)) {
                results.put("WCDMA", ((CellInfoWcdma) cellInfo).getCellSignalStrength().getDbm());
            }
        }

        return results;
    }

    public int getWifiRSSI() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            return wifiInfo.getRssi();
        }
        return -1;
    }


    private synchronized void updateBatteryStat(Intent powerIntent) {
        int scale = powerIntent.getIntExtra(BatteryManager.EXTRA_SCALE, Config.DEFAULT_BATTERY_SCALE);
        int level = powerIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, Config.DEFAULT_BATTERY_LEVEL);
        // change to the unit of percentage
        this.curBatteryLevel = level * 100 / scale;
        this.isCharging = powerIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) == BatteryManager.BATTERY_STATUS_CHARGING;

        Logger.i("Current power level is " + curBatteryLevel + " and isCharging = " + isCharging);
    }

    private class PowerStateChangeReceiver extends BroadcastReceiver {
        /**
         * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
         * android.content.Intent)
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryStat(intent);
        }
    }

    private class SignalStrengthChangeListener extends PhoneStateListener {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            String rssis = "";

            rssis += "CDMA:" + signalStrength.getCdmaDbm() + "|";
            rssis += "EVDO:" + signalStrength.getEvdoDbm() + "|";
            rssis += "GSM:" + ((2 * signalStrength.getGsmSignalStrength()) - 113) + "|";
            try {
                Method[] methods = android.telephony.SignalStrength.class.getMethods();
                for (Method mthd : methods) {
                    if (mthd.getName().equals("getLteDbm")) {
                        rssis += "LTE:" + mthd.invoke(signalStrength) + "|";
                    }
                }
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }

            setCurrentRssi(rssis);
        }
    }

    private String getVersionStr() {
        return String.format("INCREMENTAL:%s, RELEASE:%s, SDK_INT:%s", Build.VERSION.INCREMENTAL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
    }

    private String getDeviceId() {
        // This ID is permanent to a physical phone.
        String deviceId = telephonyManager.getDeviceId();

        // "generic" means the emulator.
        if (deviceId == null || Build.DEVICE.equals("generic")) {

            // This ID changes on OS reinstall/factory reset.
            deviceId = Secure.getString(context.getContentResolver(), Secure.ANDROID_ID);
        }

        return deviceId;
    }

    public DeviceInfo getDeviceInfo() {

        if (deviceInfo == null) {
            deviceInfo = new DeviceInfo();
            deviceInfo.deviceId = getDeviceId();
            deviceInfo.manufacturer = Build.MANUFACTURER;
            deviceInfo.model = Build.MODEL;
            deviceInfo.os = getVersionStr();
            deviceInfo.user = Build.VERSION.CODENAME;
        }

        return deviceInfo;
    }

    private Location getMockLocation() {
        return new Location("MockProvider");
    }

    // Hongyi: it is not a good idea to hard code here. Instead we can move those strings from string.xml to Config.java
    public String getServerUrl() {
        return Config.SERVER_URL;
    }

    public String getAnonymousServerUrl() {
        return Config.ANONYMOUS_SERVER_URL;
    }

    public String getTestingServerUrl() {
        return Config.TEST_SERVER_URL;
    }

    public boolean isTestingServer(String serverUrl) {
        return serverUrl == getTestingServerUrl();
    }

    /**
     * Using MLab service to detect ipv4 or ipv6 compatibility
     *
     * @param ip_detect_type -- "ipv4" or "ipv6"
     * @return IP_TYPE_CANNOT_DECIDE, IP_TYPE_UNCONNECTIVITY, IP_TYPE_CONNECTIVITY
     */
    private int checkIPCompatibility(String ip_detect_type) {
        if (!ip_detect_type.equals("ipv4") && !ip_detect_type.equals("ipv6")) {
            return IP_TYPE_CANNOT_DECIDE;
        }
        Socket tcpSocket = new Socket();
        try {
            ArrayList<String> hostnameList = MLabNS.Lookup("mobiperf", ip_detect_type, "ip");
            // MLabNS returns at least one ip address
            if (hostnameList.isEmpty())
                return IP_TYPE_CANNOT_DECIDE;
            // Use the first result in the element
            String hostname = hostnameList.get(0);
            SocketAddress remoteAddr = new InetSocketAddress(hostname, portNum);
            tcpSocket.setTcpNoDelay(true);
            tcpSocket.connect(remoteAddr, tcpTimeout);
        } catch (ConnectException e) {
            // Server is not reachable due to client not support ipv6
            Logger.e("Connection exception is " + e.getMessage());
            return IP_TYPE_UNCONNECTIVITY;
        } catch (IOException e) {
            // Client timer expired
            Logger.e("Fail to setup TCP in checkIPCompatibility(). " + e.getMessage());
            return IP_TYPE_CANNOT_DECIDE;
        } catch (InvalidParameterException e) {
            // MLabNS service lookup fail
            Logger.e("InvalidParameterException in checkIPCompatibility(). " + e.getMessage());
            return IP_TYPE_CANNOT_DECIDE;
        } catch (IllegalArgumentException e) {
            Logger.e("IllegalArgumentException in checkIPCompatibility(). " + e.getMessage());
            return IP_TYPE_CANNOT_DECIDE;
        } finally {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                Logger.e("Fail to close TCP in checkIPCompatibility().");
                return IP_TYPE_CANNOT_DECIDE;
            }
        }
        return IP_TYPE_CONNECTIVITY;
    }

    /**
     * Use MLabNS slices to check IPv4/IPv6 domain name resolvable
     *
     * @param ip_detect_type -- "ipv4" or "ipv6"
     * @return DN_UNRESOLVABLE, DN_RESOLVABLE
     */
    private int checkDomainNameResolvable(String ip_detect_type) {
        if (!ip_detect_type.equals("ipv4") && !ip_detect_type.equals("ipv6")) {
            return DN_UNKNOWN;
        }
        try {
            ArrayList<String> ipAddressList = MLabNS.Lookup("mobiperf", ip_detect_type, "fqdn");
            String ipAddress;
            // MLabNS returns one fqdn each time
            if (ipAddressList.size() == 1) {
                ipAddress = ipAddressList.get(0);
            } else {
                return DN_UNKNOWN;
            }

            InetAddress inet = InetAddress.getByName(ipAddress);
            if (inet != null)
                return DN_RESOLVABLE;
        } catch (UnknownHostException e) {
            // Fail to resolve domain name
            Logger.e("UnknownHostException in checkDomainNameResolvable() " + e.getMessage());
            return DN_UNRESOLVABLE;
        } catch (InvalidParameterException e) {
            // MLabNS service lookup fail
            Logger.e("InvalidParameterException in checkIPCompatibility(). " + e.getMessage());
            return DN_UNRESOLVABLE;
        } catch (Exception e) {
            // "catch-all"
            Logger.e("Unexpected Exception: " + e.getMessage());
            return DN_UNRESOLVABLE;
        }
        return DN_UNKNOWN;
    }

    /**
     * Summarize ip connectable cases
     *
     * @return ipv4, ipv6, ipv4_ipv6, IP_TYPE_NONE or IP_TYPE_UNKNOWN
     */
    public String getIpConnectivity() {
        int v4Conn = checkIPCompatibility("ipv4");
        int v6Conn = checkIPCompatibility("ipv6");
        if (v4Conn == IP_TYPE_CONNECTIVITY && v6Conn == IP_TYPE_CONNECTIVITY)
            return IP_TYPE_IPV4_IPV6_BOTH;
        if (v4Conn == IP_TYPE_CONNECTIVITY && v6Conn != IP_TYPE_CONNECTIVITY)
            return IP_TYPE_IPV4_ONLY;
        if (v4Conn != IP_TYPE_CONNECTIVITY && v6Conn == IP_TYPE_CONNECTIVITY)
            return IP_TYPE_IPV6_ONLY;
        if (v4Conn == IP_TYPE_UNCONNECTIVITY && v6Conn == IP_TYPE_UNCONNECTIVITY)
            return IP_TYPE_NONE;
        return IP_TYPE_UNKNOWN;
    }

    /**
     * Summarize Domain Name resolvability cases
     *
     * @return ipv4, ipv6, ipv4_ipv6, IP_TYPE_NONE or IP_TYPE_UNKNOWN
     */
    public String getDnResolvability() {
        int v4Resv = checkDomainNameResolvable("ipv4");
        int v6Resv = checkDomainNameResolvable("ipv6");
        if (v4Resv == DN_RESOLVABLE && v6Resv == DN_RESOLVABLE)
            return IP_TYPE_IPV4_IPV6_BOTH;
        if (v4Resv == DN_RESOLVABLE && v6Resv != DN_RESOLVABLE)
            return IP_TYPE_IPV4_ONLY;
        if (v4Resv != DN_RESOLVABLE && v6Resv == DN_RESOLVABLE)
            return IP_TYPE_IPV6_ONLY;
        if (v4Resv == DN_UNRESOLVABLE && v6Resv == DN_UNRESOLVABLE)
            return IP_TYPE_NONE;
        return IP_TYPE_UNKNOWN;
    }

    /**
     * Returns the DeviceProperty needed to report the measurement result
     */
    public DeviceProperty getDeviceProperty(String requestApp) {
        String carrierName = telephonyManager.getNetworkOperatorName();
        Location location;
        if (isTestingServer(getServerUrl())) {
            location = getMockLocation();
        } else {
            location = getLocation();
        }
        //TODO Test on Veriozn and Sprint, as result may be unreliable on CDMA
        // networks (use getPhoneType() to determine if on a CDMA network)
        String networkCountryIso = telephonyManager.getNetworkCountryIso();

        String networkType = PhoneUtils.getPhoneUtils().getNetwork();
        String ipConnectivity = "NOT SUPPORTED";
        String dnResolvability = "NOT SUPPORTED";
        Logger.w("IP connectivity is " + ipConnectivity);
        Logger.w("DN resolvability is " + dnResolvability);
        String versionName = PhoneUtils.getPhoneUtils().getAppVersionName();
        PhoneUtils utils = PhoneUtils.getPhoneUtils();

        Logger.e("Request App is " + requestApp);
        Logger.e("Host apps:");
        for (String app : PhoneUtils.clientKeySet) {
            Logger.e(app);
        }
        String mobilyzerVersion = context.getString(R.string.scheduler_version_name);
        Logger.i("Scheduler version = " + mobilyzerVersion);
        return new DeviceProperty(getDeviceInfo().deviceId, versionName,
                System.currentTimeMillis() * 1000, getVersionStr(), ipConnectivity,
                dnResolvability, location.getLongitude(), location.getLatitude(),
                location.getProvider(), networkType, carrierName, networkCountryIso,
                utils.getCurrentBatteryLevel(), utils.isCharging(),
                utils.getCellInfo(false), getCellRssi(), getWifiRSSI(), getWifiSSID(), getWifiBSSID(), getWifiIpAddress(),
                mobilyzerVersion, PhoneUtils.clientKeySet, requestApp);
    }
}