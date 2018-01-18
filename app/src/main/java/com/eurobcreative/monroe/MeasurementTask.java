package com.eurobcreative.monroe;

import android.os.Parcel;
import android.os.Parcelable;

import com.eurobcreative.monroe.exceptions.MeasurementError;
import com.eurobcreative.monroe.measurements.HttpTask;
import com.eurobcreative.monroe.util.Util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;

public abstract class MeasurementTask implements Callable<MeasurementResult[]>, Comparable, Parcelable {
    protected MeasurementDesc measurementDesc;
    protected String taskId;


    public static final int USER_PRIORITY = Integer.MIN_VALUE;
    //Used for Server tasks
    public static final int INVALID_PRIORITY = Integer.MAX_VALUE;
    public static final int GCM_PRIORITY = 1234;//TODO just for testing
    public static final int INFINITE_COUNT = -1;

    private static HashMap<String, Class> measurementTypes;
    // Maps between the type of task and its readable name
    private static HashMap<String, String> measurementDescToType;

    static {
        measurementTypes = new HashMap<>();
        measurementDescToType = new HashMap<>();
        measurementTypes.put(HttpTask.TYPE, HttpTask.class);
        measurementDescToType.put(Util.HTTP_DESCRIPTOR, HttpTask.TYPE);
    }

    /**
     * @param measurementDesc
     */
    protected MeasurementTask(MeasurementDesc measurementDesc) {
        super();
        this.measurementDesc = measurementDesc;
        generateTaskID();
    }

    /* Compare priority as the first order. Then compare start time. */
    @Override
    public int compareTo(Object t) {
        MeasurementTask another = (MeasurementTask) t;

        if (this.measurementDesc.startTime != null && another.measurementDesc.startTime != null) {
            return this.measurementDesc.startTime.compareTo(another.measurementDesc.startTime);
        }
        return 0;
    }

    public long timeFromExecution() {
        return this.measurementDesc.startTime.getTime() - System.currentTimeMillis();
    }

    public String getMeasurementType() {
        return this.measurementDesc.type;
    }

    public String getKey() {
        return this.measurementDesc.key;
    }

    public MeasurementDesc getDescription() {
        return this.measurementDesc;
    }

    /**
     * Gets the currently available measurement types
     */
    public static Set<String> getMeasurementTypes() {
        return measurementTypes.keySet();
    }

    public static Class getTaskClassForMeasurement(String type) {
        return measurementTypes.get(type);
    }

    public String getTaskId() {
        return taskId;
    }

    /**
     * Returns a brief human-readable descriptor of the task.
     */
    public abstract String getDescriptor();

    @Override
    public abstract MeasurementResult[] call() throws MeasurementError;

    /**
     * Return the string indicating the measurement type.
     */
    public abstract String getType();

    @Override
    public abstract MeasurementTask clone();

    /**
     * Stop the measurement, even when it is running. There is no side effect if the measurement has
     * not started or is already finished.
     */
    public abstract boolean stop();

    public abstract long getDuration();
    public abstract void setDuration(long newDuration);

    @Override
    public boolean equals(Object o) {
        MeasurementTask another = (MeasurementTask) o;
        if (this.getDescription().equals(another.getDescription()) && this.getType().equals(another.getType())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        StringBuilder taskstrbld = new StringBuilder(getMeasurementType());
        taskstrbld.append(",")
                .append(this.measurementDesc.key).append(",")
                .append(this.measurementDesc.startTime).append(",")
                .append(this.measurementDesc.endTime).append(",")
                .append(this.measurementDesc.intervalSec).append(",")
                .append(this.measurementDesc.priority);

        Object[] keys = this.measurementDesc.parameters.keySet().toArray();
        Arrays.sort(keys);
        for (Object k : keys) {
            taskstrbld.append(",").append(this.measurementDesc.parameters.get(k));
        }

        return taskstrbld.toString().hashCode();
    }

    /**
     * return hashcode of MeasurementTask as taskId.
     */
    public void generateTaskID() {
        taskId = this.hashCode() + "";
    }

    protected MeasurementTask(Parcel in) {
        measurementDesc = in.readParcelable(MeasurementDesc.class.getClassLoader());
        taskId = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(measurementDesc, flags);
        dest.writeString(taskId);
    }

    /**
     * All measurement tasks must provide measurements of how much data they have
     * used to be fetched when the task completes.  This allows us to make sure we
     * stay under the data limit.
     *
     * @return Data consumed, in bytes
     */
    public abstract long getDataConsumed();
}