package com.eurobcreative.monroe;

import android.content.Context;

import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.PhoneUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

/**
 * A basic power manager implementation that decides whether a measurement can be scheduled based on
 * the current battery level: no measurements will be scheduled if the current battery is lower than
 * a threshold.
 */
public class ResourceCapManager {
    /**
     * The minimum threshold below which no measurements will be scheduled
     */
    private int minBatteryThreshold;
    private Context context = null;
    private int dataLimit;// in Byte
    private MeasurementScheduler.DataUsageProfile dataUsageProfile;

    // Constants for how much data can be consumed under each profile
    private static int UNLIMITED_LIMIT = -1;
    private static int PROFILE1_LIMIT = 50 * 1024 * 1024;
    private static int PROFILE2_LIMIT = 100 * 1024 * 1024;
    private static int PROFILE3_LIMIT = 250 * 1024 * 1024;
    private static int PROFILE4_LIMIT = 500 * 1024 * 1024;

    // Looking up various phone util data uses the network. It's hard to measure how much.
    // The good news is that this value is basically constant!
    public static int PHONEUTILCOST = 3 * 1024;

    public ResourceCapManager(int batteryThresh, Context context) {
        this.minBatteryThreshold = batteryThresh;
        this.dataLimit = PROFILE3_LIMIT;
        this.context = context;
        this.dataUsageProfile = MeasurementScheduler.DataUsageProfile.PROFILE3;
    }

    /**
     * Sets the minimum battery percentage below which measurements cannot be run.
     *
     * @param batteryThresh the battery percentage threshold between 0 and 100
     */
    public synchronized void setBatteryThresh(int batteryThresh) throws IllegalArgumentException {
        // if (batteryThresh < 0 || batteryThresh > 100) {
        // throw new IllegalArgumentException("batteryCap must fall between 0 and 100, inclusive");
        // }
        this.minBatteryThreshold = batteryThresh;
    }

    public synchronized int getBatteryThresh() {
        return this.minBatteryThreshold;
    }

    /**
     * Given a data profile, set the data limit and profile code accordingly.
     * <p>
     * If an invalid code is given, leave as default (250 MB) and print a warning.
     *
     * @param profile String describing the profile
     */
    public synchronized void setDataUsageLimit(MeasurementScheduler.DataUsageProfile profile) {
        dataUsageProfile = profile;
        if (profile.equals(MeasurementScheduler.DataUsageProfile.PROFILE1)) {
            dataLimit = PROFILE1_LIMIT;
        } else if (profile.equals(MeasurementScheduler.DataUsageProfile.PROFILE2)) {
            dataLimit = PROFILE2_LIMIT;
        } else if (profile.equals(MeasurementScheduler.DataUsageProfile.PROFILE3)) {
            dataLimit = PROFILE3_LIMIT;
        } else if (profile.equals(MeasurementScheduler.DataUsageProfile.PROFILE4)) {
            dataLimit = PROFILE4_LIMIT;
        } else if (profile.equals(MeasurementScheduler.DataUsageProfile.UNLIMITED)) {
            dataLimit = UNLIMITED_LIMIT;
        } else {
            Logger.w("Specified limit profile not found!");
        }
    }

    /**
     * @return The current data limit in bytes.
     */
    public synchronized int getDataLimit() {
        return this.dataLimit;
    }

    /**
     * Reset the data used in the data usage file to 0. This should never be done unless the file does
     * not exist.
     */
    private void resetDataUsage() {
        File file = new File(context.getFilesDir(), "datausage");
        if (file.exists()) {
            Logger.e("Attempting to overwrite a file that exists!!!!");
        }
        long usageStartTimeSec = (System.currentTimeMillis() / 1000);
        writeDataUsageToFile(0, usageStartTimeSec);
    }

    /**
     * Store the data used this period and the beginning of the period in a file, in the format [time
     * reset, in seconds]_[bytes used].
     * <p>
     * Note that the data used can be negative, due to a underused data budget from last period.
     *
     * @param dataUsed The updated amount of data to write
     * @param time     The updated time to write
     */
    private synchronized void writeDataUsageToFile(long dataUsed, long time) {
        try {
            FileOutputStream outputStream = context.openFileOutput("datausage", Context.MODE_PRIVATE);
            String usageStat = time + "_" + dataUsed;
            outputStream.write(usageStat.getBytes());
            Logger.i("Updating data usage: " + dataUsed + " Byte used from " + time);
            outputStream.close();
        } catch (IOException e) {
            Logger.e("Error in creating data usage file");
            e.printStackTrace();
        }
    }

    /**
     * Read the usage data (start of usage period and quantity used in bytes) from the usage data
     * file.
     *
     * @return An array consisting of the start of the usage period, then the data used so far. If the
     * file does not exist, returns -1 in each argument.
     */
    private synchronized long[] readUsageFromFile() {
        long[] retval = {-1, -1};
        File file = new File(context.getFilesDir(), "datausage");
        if (!file.exists()) {
            return retval;
        }
        try {
            String content = "";
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            while ((line = br.readLine()) != null) {
                content += line;
            }
            String[] toks = content.split("_");
            long usageStartTimeSec = Long.parseLong(toks[0]);
            long dataUsed = Long.parseLong(toks[1]);

            retval[0] = usageStartTimeSec;
            retval[1] = dataUsed;

            br.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return retval;
    }

    /**
     * Updates the data consumption period: using the current time move ahead to the correct data
     * consumption period, and also update the data used so far.
     * <p>
     * Data assigned to a previous data period is subtracted; this can go below zero, effectively
     * crediting unused data to future tasks.
     *
     * @param dataUsed          Data consumed since the start of the last period
     * @param usageStartTimeSec Time since the start of the last period
     * @return
     */
    private long setNewDataConsumptionPeriod(long dataUsed, long usageStartTimeSec) {
        long time_per_period = Config.DEFAULT_DATA_MONITOR_PERIOD_DAY * 24 * 60 * 60;

        Logger.i("Finished data consumption period that began at time:" + usageStartTimeSec + " having " + dataUsed + " consumed");

        // Figure out how many periods have passed
        int periods = (int) (((float) ((System.currentTimeMillis() / 1000) - usageStartTimeSec)) / (float) time_per_period);

        // Update usageStarTimeSec to the appropriate period
        usageStartTimeSec += periods * time_per_period;

        // Discount from the data used data that is budgeted to previous periods.
        // Note that this could go less than zero if we are below budget.
        long datalimit_per_period = (getDataLimit() * Config.DEFAULT_DATA_MONITOR_PERIOD_DAY) / 30;
        dataUsed = dataUsed - (periods * datalimit_per_period);
        Logger.i("Net data usage at start of period: " + dataUsed);

        writeDataUsageToFile(dataUsed, usageStartTimeSec);
        return dataUsed;
    }

    /**
     * Helper function: given the beginning of the data usage period currrently under consideration,
     * determine if we're still in that period.
     *
     * @param usageStartTimeSec The start of the last stored data usage period
     * @return True if we are still in the same data usage period.
     */
    private boolean isInDataLimitPeriod(long usageStartTimeSec) {
        long timeSoFar = (System.currentTimeMillis() / 1000) - usageStartTimeSec;
        Logger.i("Time passed since data period last changed: " + timeSoFar);
        return timeSoFar <= Config.DEFAULT_DATA_MONITOR_PERIOD_DAY * 24 * 60 * 60;
    }

    /**
     * Determines if the data limit has been exceeded.
     * <p>
     * If there is no data limit, always returns false. If there is no valid data usage file, creates
     * a new one and returns false.
     * <p>
     * Otherwise, checks if we are over the limit yet or if we can run another task. If a new data
     * period needs to be started, we do that too. *
     *
     * @return True if over the data limit
     * @throws IOException
     */
    public boolean isOverDataLimit() throws IOException {
        Logger.i("Checking data limit...");

        if (getDataLimit() == UNLIMITED_LIMIT) {
            Logger.i("No data limit!");
            return false;
        }
        long[] usagedata = readUsageFromFile();
        long usageStartTimeSec = usagedata[0];
        long dataUsed = usagedata[1];

        if (usageStartTimeSec != -1) {
            if (!isInDataLimitPeriod(usageStartTimeSec)) {
                // Update our file to the next period, and update our data usage
                // budget accordingly.
                dataUsed = setNewDataConsumptionPeriod(dataUsed, usageStartTimeSec);
            }
            long dataLimit = (getDataLimit() * Config.DEFAULT_DATA_MONITOR_PERIOD_DAY) / 30;
            Logger.i("Data limit is: " + dataLimit + " Data used is:" + dataUsed);
            if (dataUsed >= dataLimit) {
                Logger.i("Exceeded data limit:  Total data limit:" + getDataLimit());
                return true;
            } else {
                return false;
            }
        }
        // If the file wasn't there we need to reset the data limit period.
        resetDataUsage();
        return false;
    }

    /**
     * Determine how much data was consumed by a task and update the data usage accordingly.
     *
     * @param taskDataUsed The type of measurement task completed
     * @throws IOException
     */
    public void updateDataUsage(long taskDataUsed) throws IOException {

        Logger.i("Amount of data used in the last task: " + taskDataUsed);

        long[] usagedata = readUsageFromFile();
        long usageStartTimeSec = usagedata[0];
        long dataUsed = usagedata[1];
        // If we have a valid file
        if (usageStartTimeSec != -1) {
            dataUsed += taskDataUsed;
            if (!isInDataLimitPeriod(usageStartTimeSec)) {
                // If we are in a new data consumption period, update it
                setNewDataConsumptionPeriod(dataUsed, usageStartTimeSec);
            } else {
                // Otherwise just write to a file
                writeDataUsageToFile(dataUsed, usageStartTimeSec);
            }
        } else {
            // If we don't have a data usage file, initialize it with the data just used
            Logger.i("Data usage file not found, creating a new one...");
            usageStartTimeSec = (System.currentTimeMillis() / 1000);
            dataUsed = taskDataUsed;
            writeDataUsageToFile(dataUsed, usageStartTimeSec);
        }
    }

    /**
     * Returns whether a measurement can be run.
     */
    public synchronized boolean canScheduleExperiment() {
        return (PhoneUtils.getPhoneUtils().isCharging() || PhoneUtils.getPhoneUtils().getCurrentBatteryLevel() > minBatteryThreshold);
    }
}