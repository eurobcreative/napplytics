package com.eurobcreative.monroe;

/**
 * The system defaults.
 */

public interface Config {
    // Important: keep same with the version_code and version_name in strings.xml
    String version = "3";
    /**
     * Strings migrated from string.xml
     */
    String SERVER_URL = "https://openmobiledata.appspot.com";
    String ANONYMOUS_SERVER_URL = "https://openmobiledata.appspot.com/anonymous";
    String TEST_SERVER_URL = "";
    String DEFAULT_USER = "Anonymous";

    int MAX_TASK_QUEUE_SIZE = 100;

    String USER_AGENT = "Mobilyzer-" + version + " (Linux; Android)";
    String PING_EXECUTABLE = "ping";
    String PING6_EXECUTABLE = "ping6";

    String SERVER_TASK_CLIENT_KEY = "LibraryServerTask";
    String CHECKIN_KEY = "MobilyzerCheckin";

    String TASK_STARTED = "TASK_STARTED";
    String TASK_FINISHED = "TASK_FINISHED";
    String TASK_PAUSED = "TASK_PAUSED";
    String TASK_RESUMED = "TASK_RESUMED";
    String TASK_CANCELED = "TASK_CENCELED";
    String TASK_STOPPED = "TASK_STOPPED";
    String TASK_RESCHEDULED = "TASK_RESCHEDULED";

    /**
     * Types for message between API and scheduler
     **/
    int MSG_SUBMIT_TASK = 1;
    int MSG_RESULT = 2;
    int MSG_CANCEL_TASK = 3;
    int MSG_SET_BATTERY_THRESHOLD = 4;
    int MSG_GET_BATTERY_THRESHOLD = 5;
    int MSG_SET_CHECKIN_INTERVAL = 6;
    int MSG_GET_CHECKIN_INTERVAL = 7;
    int MSG_GET_TASK_STATUS = 8;
    int MSG_SET_DATA_USAGE = 9;
    int MSG_GET_DATA_USAGE = 10;
    int MSG_REGISTER_CLIENTKEY = 11;
    int MSG_UNREGISTER_CLIENTKEY = 12;
    int MSG_SET_AUTH_ACCOUNT = 13;
    int MSG_GET_AUTH_ACCOUNT = 14;

    /**
     * The default battery level if we cannot read it from the system
     */
    int DEFAULT_BATTERY_LEVEL = 0;
    /**
     * The default maximum battery level if we cannot read it from the system
     */
    int DEFAULT_BATTERY_SCALE = 100;

    /**
     * Tasks expire in a bit more than two days. Expired tasks will be removed from the scheduler
     */
    long TASK_EXPIRATION_MSEC = 2 * 24 * 3600 * 1000 + 1800 * 1000;
    /**
     * Default interval in seconds between system measurements of a given measurement type
     */
    double DEFAULT_SYSTEM_MEASUREMENT_INTERVAL_SEC = 15 * 60;
    /**
     * Default interval in seconds between context collection
     */
    int DEFAULT_CONTEXT_INTERVAL_SEC = 5;
    int MAX_CONTEXT_INFO_COLLECTIONS_PER_TASK = 120;

    int DEFAULT_DNS_COUNT_PER_MEASUREMENT = 1;
    int PING_COUNT_PER_MEASUREMENT = 10;
    float PING_FILTER_THRES = (float) 1.4;
    double DEFAULT_INTERVAL_BETWEEN_ICMP_PACKET_SEC = 0.5;

    int TRACEROUTE_TASK_DURATION = 4 * 30 * 500;
    int DEFAULT_DNS_TASK_DURATION = 0;
    int DEFAULT_HTTP_TASK_DURATION = 0;
    int DEFAULT_PING_TASK_DURATION = PING_COUNT_PER_MEASUREMENT * 500;
    int DEFAULT_UDPBURST_DURATION = 30 * 1000;
    int DEFAULT_PARALLEL_TASK_DURATION = 60 * 1000;
    int DEFAULT_TASK_DURATION_TIMEOUT = 60 * 1000;
    int DEFAULT_RRC_TASK_DURATION = 30 * 60 * 1000;
    int MAX_TASK_DURATION = 15 * 60 * 1000;//TODO

    // Keys in SharedPrefernce
    String PREF_KEY_SELECTED_ACCOUNT = "PREF_KEY_SELECTED_ACCOUNT";
    String PREF_KEY_BATTERY_THRESHOLD = "PREF_KEY_BATTERY_THRESHOLD";
    String PREF_KEY_CHECKIN_INTERVAL = "PREF_KEY_CHECKIN_INTERVAL";
    String PREF_KEY_DATA_USAGE_PROFILE = "PREF_KEY_DATA_USAGE_PROFILE";

    int MIN_BATTERY_THRESHOLD = 20;
    int MAX_BATTERY_THRESHOLD = 100;
    int DEFAULT_BATTERY_THRESH_PRECENT = 60;

    // The default checkin interval in seconds
    long DEFAULT_CHECKIN_INTERVAL_SEC = 60 * 60L;
    long MIN_CHECKIN_INTERVAL_SEC = 3600L;
    long MAX_CHECKIN_INTERVAL_SEC = 24 * 3600L;
    long MIN_CHECKIN_RETRY_INTERVAL_SEC = 20L;
    long MAX_CHECKIN_RETRY_INTERVAL_SEC = 60L;
    int MAX_CHECKIN_RETRY_COUNT = 3;
    long PAUSE_BETWEEN_CHECKIN_CHANGE_MSEC = 1 * 60 * 1000L;

    int DEFAULT_DATA_MONITOR_PERIOD_DAY = 1;

    // Reschedule delay for RRC task
    long RESCHEDULE_DELAY = 20 * 60 * 1000;


    int INVALID_PROGRESS = -1;
    int MAX_PROGRESS_BAR_VALUE = 100;
    // A progress greater than MAX_PROGRESS_BAR_VALUE indicates the end of the measurement
    int MEASUREMENT_END_PROGRESS = MAX_PROGRESS_BAR_VALUE + 1;
    int DEFAULT_USER_MEASUREMENT_COUNT = 1;
}