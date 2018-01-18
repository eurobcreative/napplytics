package com.eurobcreative.monroe;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.StringBuilderPrinter;

import com.eurobcreative.monroe.measurements.HttpTask;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.MeasurementJsonConvertor;
import com.eurobcreative.monroe.util.PhoneUtils;
import com.eurobcreative.monroe.util.Util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * POJO that represents the result of a measurement
 *
 * @see MeasurementDesc
 */
public class MeasurementResult implements Parcelable {

    private String deviceId;
    private DeviceProperty properties;// TODO needed for sending back the
    // results to server
    private long timestamp;
    private String type;
    private TaskProgress taskProgress;
    private MeasurementDesc parameters;
    private HashMap<String, String> values;
    private ArrayList<HashMap<String, String>> contextResults;

    public enum TaskProgress {
        COMPLETED, PAUSED, FAILED, RESCHEDULED
    }

    /**
     * @param deviceProperty  DeviceProperty object which will be attached to the result
     * @param type            measurement type
     * @param timeStamp
     * @param taskProgress    progress of the task: COMPLETED, PAUSED, FAILED
     * @param measurementDesc MeasurementDesc of the task
     */
    public MeasurementResult(String id, DeviceProperty deviceProperty, String type, long timeStamp,
                             TaskProgress taskProgress, MeasurementDesc measurementDesc) {
        super();

        this.deviceId = id;
        this.type = type;
        this.properties = deviceProperty;
        this.timestamp = timeStamp;
        this.taskProgress = taskProgress;
        this.parameters = measurementDesc;
        this.parameters.parameters = measurementDesc.parameters;
        this.values = new HashMap<>();
        this.contextResults = new ArrayList<>();
    }

    public MeasurementDesc getMeasurementDesc() {
        return this.parameters;
    }

    public DeviceProperty getDeviceProperty() {
        return this.properties;
    }

    @SuppressWarnings("unchecked")
    public void addContextResults(ArrayList<HashMap<String, String>> contextResults) {
        this.contextResults = (ArrayList<HashMap<String, String>>) contextResults.clone();
    }

    private static String getStackTrace(Throwable error) {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        error.printStackTrace(printWriter);
        return result.toString();
    }

    /**
     * Creates measurement result for the failed task by including the error message
     *
     * @param task  input task that failed
     * @param error that occurred during the execution of task
     * @return list of failure measurement results for created for the input task
     */
    public static MeasurementResult[] getFailureResult(MeasurementTask task, Throwable error) {
        PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
        ArrayList<MeasurementResult> results = new ArrayList<>();

        MeasurementResult r = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
                phoneUtils.getDeviceProperty(task.getKey()), task.getType(),
                System.currentTimeMillis() * 1000, TaskProgress.FAILED, task.measurementDesc);
        Logger.e(error.toString() + "\n" + getStackTrace(error));
        r.addResult("error", error.toString());
        results.add(r);
        return results.toArray(new MeasurementResult[results.size()]);
    }

    /**
     * Returns the type of this result
     */
    public String getType() {
        return parameters.getType();
    }

    /**
     * @return Task progress for this task
     */
    public TaskProgress getTaskProgress() {
        return this.taskProgress;
    }

    /* Add the measurement results of type String into the class */
    public void addResult(String resultType, Object resultVal) {
        this.values.put(resultType, MeasurementJsonConvertor.toJsonString(resultVal));
    }

    /* Returns a string representation of the result */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        StringBuilderPrinter printer = new StringBuilderPrinter(builder);
        try {
            if (type.equals(HttpTask.TYPE)) {
                getHttpResult(printer, values);

            } else {
                Logger.e("Failed to get results for unknown measurement type " + type);
            }
            return builder.toString();
        } catch (NumberFormatException e) {
            Logger.e("Exception occurs during constructing result string for user", e);
        } catch (ClassCastException e) {
            Logger.e("Exception occurs during constructing result string for user", e);
        } catch (Exception e) {
            Logger.e("Exception occurs during constructing result string for user", e);
        }
        return "Measurement has failed";
    }

    private void getHttpResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        HttpTask.HttpDesc desc = (HttpTask.HttpDesc) parameters;
        printer.println("[HTTP]");
        printer.println("URL: " + desc.url);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));

        if (taskProgress == TaskProgress.COMPLETED) {
            int headerLen = Integer.parseInt(values.get("headers_len"));
            int bodyLen = Integer.parseInt(values.get("body_len"));
            int time = Integer.parseInt(values.get("time_ms"));
            printer.println("");
            printer.println("Downloaded " + (headerLen + bodyLen) + " bytes in " + time + " ms");
            printer.println("Bandwidth: " + (headerLen + bodyLen) * 8 / time + " Kbps"); // 1 kb = 1000 b
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("Http paused!");
        } else {
            printer.println("Http download failed, status code " + values.get("code"));
            printer.println("Error: " + values.get("error"));
        }
    }

    /**
     * Necessary function for Parcelable
     **/
    private MeasurementResult(Parcel in) {
        deviceId = in.readString();
        properties = in.readParcelable(DeviceProperty.class.getClassLoader());
        timestamp = in.readLong();
        type = in.readString();
        taskProgress = (TaskProgress) in.readSerializable();
        parameters = in.readParcelable(MeasurementDesc.class.getClassLoader());
        int valuesSize = in.readInt();
        values = new HashMap<>();
        for (int i = 0; i < valuesSize; i++) {
            values.put(in.readString(), in.readString());
        }
        contextResults = new ArrayList<>();
        int contextResultsSize = in.readInt();
        for (int i = 0; i < contextResultsSize; i++) {
            int contextResultsHashMapSize = in.readInt();
            HashMap<String, String> tempHashMap = new HashMap<>();
            for (int j = 0; j < contextResultsHashMapSize; j++) {
                tempHashMap.put(in.readString(), in.readString());
            }
            contextResults.add(tempHashMap);
        }
    }

    public static final Parcelable.Creator<MeasurementResult> CREATOR = new Parcelable.Creator<MeasurementResult>() {
        public MeasurementResult createFromParcel(Parcel in) {
            return new MeasurementResult(in);
        }

        public MeasurementResult[] newArray(int size) {
            return new MeasurementResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flag) {
        out.writeString(deviceId);
        out.writeParcelable(properties, flag);
        out.writeLong(timestamp);
        out.writeString(type);
        out.writeSerializable(taskProgress);
        out.writeParcelable(parameters, flag);
        out.writeInt(values.size());
        for (String s : values.keySet()) {
            out.writeString(s);
            out.writeString(values.get(s));
        }
        out.writeInt(contextResults.size());
        for (HashMap<String, String> map : contextResults) {
            out.writeInt(map.size());
            for (String s : map.keySet()) {
                out.writeString(s);
                out.writeString(map.get(s));
            }
        }
    }
}