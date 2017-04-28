package com.eurobcreative.monroe;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.StringBuilderPrinter;

import com.eurobcreative.monroe.measurements.DnsLookupTask;
import com.eurobcreative.monroe.measurements.HttpTask;
import com.eurobcreative.monroe.measurements.ParallelTask;
import com.eurobcreative.monroe.measurements.PingTask;
import com.eurobcreative.monroe.measurements.RRCTask;
import com.eurobcreative.monroe.measurements.SequentialTask;
import com.eurobcreative.monroe.measurements.TCPThroughputTask;
import com.eurobcreative.monroe.measurements.TracerouteTask;
import com.eurobcreative.monroe.measurements.UDPBurstTask;
import com.eurobcreative.monroe.measurements.VideoQoETask;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.MeasurementJsonConvertor;
import com.eurobcreative.monroe.util.PhoneUtils;
import com.eurobcreative.monroe.util.Util;

import org.json.JSONException;

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
    private boolean success;
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
        if (this.taskProgress == TaskProgress.COMPLETED) {
            this.success = true;
        } else {
            this.success = false;
        }
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

        if (task.getType().equals(ParallelTask.TYPE)) {
            ParallelTask pTask = (ParallelTask) task;
            MeasurementResult[] tempResults = MeasurementResult.getFailureResults(pTask.getTasks(), error);
            for (MeasurementResult r : tempResults) {
                results.add(r);
            }
        } else if (task.getType().equals(SequentialTask.TYPE)) {
            SequentialTask sTask = (SequentialTask) task;
            MeasurementResult[] tempResults = MeasurementResult.getFailureResults(sTask.getTasks(), error);
            for (MeasurementResult r : tempResults) {
                results.add(r);
            }
        } else {
            MeasurementResult r = new MeasurementResult(phoneUtils.getDeviceInfo().deviceId,
                    phoneUtils.getDeviceProperty(task.getKey()), task.getType(),
                    System.currentTimeMillis() * 1000, TaskProgress.FAILED, task.measurementDesc);
            Logger.e(error.toString() + "\n" + getStackTrace(error));
            r.addResult("error", error.toString());
            results.add(r);
        }
        return results.toArray(new MeasurementResult[results.size()]);
    }

    /**
     * Generating failure results for the task list in parallel and sequential
     * tasks. Not public visible
     *
     * @param tasks task list in parallel and sequential tasks
     * @param err   error that occurred during the execution of task
     * @return list of failure measurement results of the task list
     */
    private static MeasurementResult[] getFailureResults(MeasurementTask[] tasks, Throwable err) {
        ArrayList<MeasurementResult> results = new ArrayList<>();
        if (tasks != null) {
            for (MeasurementTask t : tasks) {
                MeasurementResult[] tempResults = getFailureResult(t, err);
                for (MeasurementResult r : tempResults) {
                    results.add(r);
                }
            }
        }
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

    /**
     * @param progress new task progress to be set
     */
    public void setTaskProgress(TaskProgress progress) {
        this.taskProgress = progress;
    }

    /**
     * @return key/value pairs of measurement result
     */
    public HashMap<String, String> getValues() {
        return this.values;
    }

    /**
     * Check if this task is succeed
     *
     * @return true if taskProgress equals to COMPLETED, false otherwise
     */
    public boolean isSucceed() {
        return this.taskProgress == TaskProgress.COMPLETED ? true : false;
    }

    /**
     * adds a new task parameter to the list of parameters
     *
     * @param key   key for new parameter
     * @param value value for new parameter
     */
    public void setParameter(String key, String value) {
        this.parameters.parameters.put(key, value);
    }

    /**
     * @param key key for retrieving value
     * @return value for that key
     */
    public String getParameter(String key) {
        return this.parameters.parameters.get(key);
    }

    public void setMeasurmentDesc(MeasurementDesc desc) {
        this.parameters = desc;
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
            if (type.equals(PingTask.TYPE)) {
                getPingResult(printer, values);
            } else if (type.equals(HttpTask.TYPE)) {
                getHttpResult(printer, values);
            } else if (type.equals(DnsLookupTask.TYPE)) {
                getDnsResult(printer, values);
            } else if (type.equals(TracerouteTask.TYPE)) {
                getTracerouteResult(printer, values);
            } else if (type.equals(UDPBurstTask.TYPE)) {
                getUDPBurstResult(printer, values);
            } else if (type.equals(TCPThroughputTask.TYPE)) {
                getTCPThroughputResult(printer, values);
            } else if (type.equals(RRCTask.TYPE)) {
                getRRCResult(printer, values);
            } else if (type.equals(VideoQoETask.TYPE)) {
                getVideoQoEResult(printer, values);
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

    private void getPingResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        PingTask.PingDesc desc = (PingTask.PingDesc) parameters;
        printer.println("[Ping]");
        printer.println("Target: " + desc.target);
        String ipAddress = removeQuotes(values.get("target_ip"));
        // TODO: internationalize 'Unknown'.
        if (ipAddress == null) {
            ipAddress = "Unknown";
        }
        printer.println("IP address: " + ipAddress);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

        if (taskProgress == TaskProgress.COMPLETED) {
            float packetLoss = Float.parseFloat(values.get("packet_loss"));
            int count = Integer.parseInt(values.get("packets_sent"));
            printer.println("\n" + count + " packets transmitted, " + (int) (count * (1 - packetLoss)) + " received, " + (packetLoss * 100) + "% packet loss");

            float value = Float.parseFloat(values.get("mean_rtt_ms"));
            printer.println("Mean RTT: " + String.format("%.1f", value) + " ms");

            value = Float.parseFloat(values.get("min_rtt_ms"));
            printer.println("Min RTT:  " + String.format("%.1f", value) + " ms");

            value = Float.parseFloat(values.get("max_rtt_ms"));
            printer.println("Max RTT:  " + String.format("%.1f", value) + " ms");

            value = Float.parseFloat(values.get("stddev_rtt_ms"));
            printer.println("Std dev:  " + String.format("%.1f", value) + " ms");
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("Ping paused!");
        } else {
            printer.println("Error: " + values.get("error"));
        }
    }

    private void getHttpResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        HttpTask.HttpDesc desc = (HttpTask.HttpDesc) parameters;
        printer.println("[HTTP]");
        printer.println("URL: " + desc.url);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

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

    private void getDnsResult(StringBuilderPrinter printer, HashMap<String, String> values) throws JSONException {
        DnsLookupTask.DnsLookupDesc desc = (DnsLookupTask.DnsLookupDesc) parameters;
        printer.println("[DNS Lookup]");
        printer.println("Target: " + desc.target);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

        if (taskProgress == TaskProgress.COMPLETED) {
            String ipAddress = removeQuotes(values.get("address"));
            if (ipAddress == null) {
                ipAddress = "Unknown";
            }
            printer.println("\nAddress: " + ipAddress);
            int time = Integer.parseInt(values.get("time_ms"));
            printer.println("Lookup time: " + time + " ms");
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("DNS look up paused!");
        } else {
            printer.println("Error: " + values.get("error"));
        }
    }

    private void getTracerouteResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        TracerouteTask.TracerouteDesc desc = (TracerouteTask.TracerouteDesc) parameters;
        printer.println("[Traceroute]");
        printer.println("Target: " + desc.target);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

        if (taskProgress == TaskProgress.COMPLETED) {
            // Manually inject a new line
            printer.println(" ");

            int hops = Integer.parseInt(values.get("num_hops"));
            int hop_str_len = String.valueOf(hops + 1).length();
            for (int i = 1; i <= hops; i++) {
                String key = "hop_" + i + "_addr_1";
                String ipAddress = removeQuotes(values.get(key));
                if (ipAddress == null) {
                    ipAddress = "Unknown";
                }
                String hop_str = String.valueOf(i);
                String hopInfo = hop_str;
                for (int j = 0; j < hop_str_len + 1 - hop_str.length(); ++j) {
                    hopInfo += " ";
                }
                hopInfo += ipAddress;
                // Maximum IP address length is 15.
                for (int j = 0; j < 16 - ipAddress.length(); ++j) {
                    hopInfo += " ";
                }

                key = "hop_" + i + "_rtt_ms";
                // The first and last character of this string are double quotes.
                String timeStr = removeQuotes(values.get(key));
                if (timeStr == null) {
                    timeStr = "Unknown";
                    printer.println(hopInfo + "-1 ms");
                } else {
                    float time = Float.parseFloat(timeStr);
                    printer.println(hopInfo + String.format("%6.2f", time) + " ms");
                }
            }
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("Traceroute paused!");
        } else {
            printer.println("Error: " + values.get("error"));
        }
    }

    private void getUDPBurstResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        UDPBurstTask.UDPBurstDesc desc = (UDPBurstTask.UDPBurstDesc) parameters;
        if (desc.dirUp) {
            printer.println("[UDPBurstUp]");
        } else {
            printer.println("[UDPBurstDown]");
        }
        printer.println("Target: " + desc.target);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

        if (taskProgress == TaskProgress.COMPLETED) {
            printer.println("IP addr: " + values.get("target_ip"));
            printIPTestResult(printer);
//      printer.println("Packet size: " + desc.packetSizeByte + "B");
//      printer.println("Number of packets to be sent: " + desc.udpBurstCount);
//      printer.println("Interval between packets: " + desc.udpInterval + "ms");

            String lossRatio = String.format("%.2f", Double.parseDouble(values.get("loss_ratio")) * 100);
            String outOfOrderRatio = String.format("%.2f", Double.parseDouble(values.get("out_of_order_ratio")) * 100);
            printer.println("\nLoss ratio: " + lossRatio + "%");
            printer.println("Out of order ratio: " + outOfOrderRatio + "%");
            printer.println("Jitter: " + values.get("jitter") + "ms");
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("UDP Burst paused!");
        } else {
            printer.println("Error: " + values.get("error"));
        }
    }

    private void getTCPThroughputResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        TCPThroughputTask.TCPThroughputDesc desc = (TCPThroughputTask.TCPThroughputDesc) parameters;
        if (desc.dir_up) {
            printer.println("[TCP Uplink]");
        } else {
            printer.println("[TCP Downlink]");
        }
        printer.println("Target: " + desc.target);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

        if (taskProgress == TaskProgress.COMPLETED) {
            printer.println("");
            // Display result with precision up to 2 digit
            String speedInJSON = values.get("tcp_speed_results");
            String dataLimitExceedInJSON = values.get("data_limit_exceeded");
            String displayResult;

            double tp = desc.calMedianSpeedFromTCPThroughputOutput(speedInJSON);
            double KB = Math.pow(2, 10);
            if (tp < 0) {
                displayResult = "No results available.";
            } else if (tp > KB * KB) {
                displayResult = "Speed: " + String.format("%.2f", tp / (KB * KB)) + " Gbps";
            } else if (tp > KB) {
                displayResult = "Speed: " + String.format("%.2f", tp / KB) + " Mbps";
            } else {
                displayResult = "Speed: " + String.format("%.2f", tp) + " Kbps";
            }

            // Append notice for exceeding data limit
            if (dataLimitExceedInJSON.equals("true")) {
                displayResult += "\n* Task finishes earlier due to exceeding " + "maximum number of " + ((desc.dir_up) ? "transmitted" : "received") + " bytes";
            }
            printer.println(displayResult);
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("TCP Throughput paused!");
        } else {
            printer.println("Error: " + values.get("error"));
        }
    }

    private void getRRCResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        printer.println("[RRC Inference]");
        if (taskProgress == TaskProgress.COMPLETED) {
            printer.println("Succeed!");
        } else {
            printer.println("Failed!");
        }
        printer.println("Results uploaded to server");
    }

    private void getVideoQoEResult(StringBuilderPrinter printer, HashMap<String, String> values) {
        VideoQoETask.VideoQoEDesc desc = (VideoQoETask.VideoQoEDesc) parameters;
        printer.println("[Video QoE Measurement]");
        printer.println("Content ID: " + desc.contentId);
        printer.println("Streaming Algorithm: " + desc.contentType);
        printer.println("Timestamp: " + Util.getTimeStringFromMicrosecond(properties.timestamp));
        printIPTestResult(printer);

        if (taskProgress == TaskProgress.COMPLETED) {
            printer.println("");
            //printer.println(UpdateIntent.VIDEO_TASK_PAYLOAD_IS_SUCCEED + ": " + values.get(UpdateIntent.VIDEO_TASK_PAYLOAD_IS_SUCCEED));
            printer.println("Num of frame dropped" + ": " + values.get("video_num_frame_dropped"));
            printer.println("Initial loading time" + ": " + values.get("video_initial_loading_time"));
            //printer.println(UpdateIntent.VIDEO_TASK_PAYLOAD_REBUFFER_TIME + ": " + values.get(UpdateIntent.VIDEO_TASK_PAYLOAD_REBUFFER_TIME));
            //printer.println(UpdateIntent.VIDEO_TASK_PAYLOAD_GOODPUT_TIMESTAMP + ": " + values.get(UpdateIntent.VIDEO_TASK_PAYLOAD_GOODPUT_TIMESTAMP));
            //printer.println(UpdateIntent.VIDEO_TASK_PAYLOAD_GOODPUT_VALUE + ": " + values.get(UpdateIntent.VIDEO_TASK_PAYLOAD_GOODPUT_VALUE));
            //printer.println(UpdateIntent.VIDEO_TASK_PAYLOAD_BITRATE_TIMESTAMP + ": " + values.get(UpdateIntent.VIDEO_TASK_PAYLOAD_BITRATE_TIMESTAMP));
            //printer.println(UpdateIntent.VIDEO_TASK_PAYLOAD_BITRATE_VALUE + ": " + values.get(UpdateIntent.VIDEO_TASK_PAYLOAD_BITRATE_VALUE));
        } else if (taskProgress == TaskProgress.PAUSED) {
            printer.println("Video QoE task paused!");
        } else {
            printer.println("Error: " + values.get("error"));
        }
    }

    /**
     * Removes the quotes surrounding the string. If |str| is null, returns null.
     */
    private String removeQuotes(String str) {
        return str != null ? str.replaceAll("^\"|\"", "") : null;
    }

    /**
     * Print ip connectivity and hostname resolvability result
     */
    private void printIPTestResult(StringBuilderPrinter printer) {
        //printer.println("IPv4/IPv6 Connectivity: " + properties.ipConnectivity);
        //printer.println("IPv4/IPv6 Domain Name Resolvability: " + properties.dnResolvability);
    }

    /**
     * Necessary function for Parcelable
     **/
    private MeasurementResult(Parcel in) {
        //ClassLoader loader = Thread.currentThread().getContextClassLoader();
        deviceId = in.readString();
        properties = in.readParcelable(DeviceProperty.class.getClassLoader());
        timestamp = in.readLong();
        type = in.readString();
        taskProgress = (TaskProgress) in.readSerializable();
        if (this.taskProgress == TaskProgress.COMPLETED) {
            this.success = true;
        } else {
            this.success = false;
        }
        parameters = in.readParcelable(MeasurementDesc.class.getClassLoader());
        //values = in.readHashMap(loader);
        int valuesSize = in.readInt();
        values = new HashMap<>();
        for (int i = 0; i < valuesSize; i++) {
            values.put(in.readString(), in.readString());
        }
        //contextResults = in.readArrayList(loader);
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
        //out.writeMap(values);
        out.writeInt(values.size());
        for (String s : values.keySet()) {
            out.writeString(s);
            out.writeString(values.get(s));
        }
        //out.writeList(contextResults);
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