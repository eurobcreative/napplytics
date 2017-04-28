package com.eurobcreative.monroe.measurements;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;

import com.eurobcreative.monroe.Config;
import com.eurobcreative.monroe.MeasurementDesc;
import com.eurobcreative.monroe.MeasurementResult;
import com.eurobcreative.monroe.MeasurementResult.TaskProgress;
import com.eurobcreative.monroe.MeasurementTask;
import com.eurobcreative.monroe.exceptions.MeasurementError;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.MeasurementJsonConvertor;
import com.eurobcreative.monroe.util.PhoneUtils;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.WireParseException;

import java.io.IOException;
import java.io.InvalidClassException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Measures the DNS lookup time
 */
public class DnsLookupTask extends MeasurementTask {
    // Type name for internal use
    public static final String TYPE = "dns_lookup";
    // Human readable name for the task
    public static final String DESCRIPTOR = "DNS lookup";

    //Since it's very hard to calculate the data consumed by this task
    // directly, we use a fixed value.  This is on the high side.
    public static final int AVG_DATA_USAGE_BYTE = 2000;

    private long duration;

    /**
     * The description of DNS lookup measurement
     */
    public static class DnsLookupDesc extends MeasurementDesc {
        public String target;
        public String server;
        public String[] servers;
        public boolean hasMultiServer = false;
        public String qclass;
        public String qtype;


        public DnsLookupDesc(String key, Date startTime, Date endTime, double intervalSec, long count,
                             long priority, int contextIntervalSec, Map<String, String> params) {
            super(DnsLookupTask.TYPE, key, startTime, endTime, intervalSec, count,
                    priority, contextIntervalSec, params);
            initializeParams(params);
            if (this.target == null || this.target.length() == 0) {
                throw new InvalidParameterException("LookupDnsTask cannot be created due to null target string");
            }
        }

        /*
         * @see com.google.wireless.speed.speedometer.MeasurementDesc#getType()
         */
        @Override
        public String getType() {
            return DnsLookupTask.TYPE;
        }

        @Override
        protected void initializeParams(Map<String, String> params) {
            if (params == null) {
                return;
            }

            if (!params.containsKey("server")) {
                ArrayList<String> servers = new ArrayList<>();
                try {
                    Class<?> SystemProperties = Class.forName("android.os.SystemProperties");
                    Method method = SystemProperties.getMethod("get", new Class[]{String.class});
                    for (String name : new String[]{"net.dns1", "net.dns2", "net.dns3", "net.dns4",}) {
                        String value = (String) method.invoke(null, name);
                        if (value != null && !"".equals(value) && !servers.contains(value))
                            servers.add(value);
                    }
                } catch (java.lang.ClassNotFoundException ex) {
                    Logger.d("dns testing: dns local resolver: unable to get local resolver");
                } catch (java.lang.NoSuchMethodException ex) {
                    Logger.d("dns testing: dns local resolver: unable to get local resolver");
                } catch (java.lang.IllegalAccessException ex) {
                    Logger.d("dns testing: dns local resolver: unable to get local resolver");
                } catch (java.lang.reflect.InvocationTargetException ex) {
                    Logger.d("dns testing: dns local resolver: unable to get local resolver");
                }
                this.hasMultiServer = true;
                this.servers = servers.toArray(new String[0]);
                //this.server = servers.get(0);
            } else {
                this.server = params.get("server");
                if (this.server.contains("|")) {
                    this.servers = this.server.split("\\|");
                    this.hasMultiServer = true;
                }
            }

            this.target = params.get("target");
            // make the lookup absolute if it isn't already
            if (!this.target.endsWith(".")) {
                this.target = this.target + ".";
            }

            /* we are extending the DNS measurement to allow setting arbitrary query classes and types,
             * but we want to maintain backwards compatibility. Therefore, we are going to default
             * to a standard IPv4 query, qclass IN and qtype A
             */
            if (params.containsKey("qclass")) {
                this.qclass = params.get("qclass");
            } else {
                this.qclass = "IN";
            }

            if (params.containsKey("qtype")) {
                this.qtype = params.get("qtype");
            } else {
                this.qtype = "A";
            }
        }

        protected DnsLookupDesc(Parcel in) {
            super(in);
            target = in.readString();
            server = in.readString();
            qclass = in.readString();
            qtype = in.readString();
        }

        public static final Parcelable.Creator<DnsLookupDesc> CREATOR = new Parcelable.Creator<DnsLookupDesc>() {
            public DnsLookupDesc createFromParcel(Parcel in) {
                return new DnsLookupDesc(in);
            }

            public DnsLookupDesc[] newArray(int size) {
                return new DnsLookupDesc[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(target);
            dest.writeString(server);
            dest.writeString(qclass);
            dest.writeString(qtype);
        }
    }

    private class DNSWrapper {
        public boolean isValid;
        public String rawOutput;
        public Message response;
        public int qid;
        public int id;
        public long respTime;
        public String server;

        public DNSWrapper(boolean isValid, byte[] rawOutput, Message response, int qid, int id,
                          long respTime, String server) {
            this.isValid = isValid;
            this.rawOutput = Base64.encodeToString(rawOutput, Base64.DEFAULT);
//            this.rawOutput = rawOutput.toString();
            this.response = response;
            this.qid = qid;
            this.id = id;
            this.respTime = respTime;
            this.server = server;
        }
    }


    public DnsLookupTask(MeasurementDesc desc) {
        super(new DnsLookupDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec, desc.count,
                desc.priority, desc.contextIntervalSec, desc.parameters));
        this.duration = Config.DEFAULT_DNS_TASK_DURATION;
    }

    protected DnsLookupTask(Parcel in) {
        super(in);
        duration = in.readLong();
    }

    public static final Parcelable.Creator<DnsLookupTask> CREATOR =
            new Parcelable.Creator<DnsLookupTask>() {
                public DnsLookupTask createFromParcel(Parcel in) {
                    return new DnsLookupTask(in);
                }

                public DnsLookupTask[] newArray(int size) {
                    return new DnsLookupTask[size];
                }
            };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(duration);
    }

    /**
     * Returns a copy of the DnsLookupTask
     */
    @Override
    public MeasurementTask clone() {
        MeasurementDesc desc = this.measurementDesc;
        DnsLookupDesc newDesc = new DnsLookupDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
                desc.count, desc.priority, desc.contextIntervalSec, desc.parameters);
        return new DnsLookupTask(newDesc);
    }

    public ArrayList<DNSWrapper> measureDNS(String domain, String qtype, String qclass, String server) {
        Record question = null;
        try {
            question = Record.newRecord(Name.fromString(domain),
                    Type.value(qtype),
                    DClass.value(qclass));
        } catch (TextParseException e) {
            Logger.d("dns testing: Error constructing packet");
        }
        Logger.d("dns testing: constructed question");

        Message query = Message.newQuery(question);
        // wait for at most 5 seconds for a response
        //long endTime = System.currentTimeMillis() + 5;
        Logger.d("dns testing: constructed query");
        ArrayList<DNSWrapper> responses = sendMeasurement(query, server, false);
        return responses;
    }

    private ArrayList<DNSWrapper> sendMeasurement(Message query, String server, boolean useTCP) {
        int qid = query.getHeader().getID();
        Resolver resolver = null;
        try {
            resolver = new SimpleResolver(server);
            resolver.setTCP(useTCP);
            resolver.setTimeout(300); // seconds
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        Logger.d("dns testing: initialized client");

        Message response = null;
        DNSWrapper wrap;
        ArrayList<DNSWrapper> responses = new ArrayList<>();
        long endTime = System.currentTimeMillis() + 300000; //ms
        Logger.d("dns testing: about to start loop current time " + System.currentTimeMillis() + " end time: " + endTime);

        long startTime = System.currentTimeMillis(), respTime;
        try {
            response = resolver.send(query);
            Logger.d("dns testing: sent and received");

            respTime = System.currentTimeMillis() - startTime; // Includes send() and recv() times
            wrap = new DNSWrapper(true, response.toWire(), response, qid, qid, respTime, server);
            responses.add(wrap);
            Logger.d("dns testing: successfully parsed response");
        } catch (WireParseException e) {
            respTime = System.currentTimeMillis() - startTime; // Includes send() and recv() times
            wrap = new DNSWrapper(false, response.toWire(), null, qid, -1, respTime, server);
            responses.add(wrap);
            Logger.e("dns testing: problem trying to parse");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return responses;
    }

    @Override
    public MeasurementResult[] call() throws MeasurementError {

        long t1, t2;
        long totalTime = 0;
        int successCnt = 0;
        InetAddress resultInet = null;

        ArrayList<DNSWrapper> responses = new ArrayList<>();
        DnsLookupDesc desc = (DnsLookupDesc) this.measurementDesc;
        for (int i = 0; i < Config.DEFAULT_DNS_COUNT_PER_MEASUREMENT; i++) {
            DnsLookupDesc taskDesc = (DnsLookupDesc) this.measurementDesc;
            Logger.i("Running DNS Lookup for target " + taskDesc.target);
            if (taskDesc.hasMultiServer) {
                for (String server : taskDesc.servers) {
                    Logger.i("dns test starting to measure against server " + server);
                    ArrayList<DNSWrapper> resps = measureDNS(taskDesc.target, taskDesc.qtype, taskDesc.qclass, server);
                    Logger.i("dns test received " + resps.size() + " responses");
                    responses.addAll(resps);
                    Logger.i("dns test added resps to overall responses");
                }
            } else {
                responses = measureDNS(taskDesc.target, taskDesc.qtype, taskDesc.qclass, taskDesc.server);
            }

            //AITOR
            try {
                t1 = System.currentTimeMillis();
                InetAddress inet = InetAddress.getByName(taskDesc.target);
                t2 = System.currentTimeMillis();
                if (inet != null) {
                    totalTime += (t2 - t1);
                    resultInet = inet;
                    successCnt++;
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        if ((responses == null) || (responses.size() == 0)) {
            throw new MeasurementError("Problems conducting DNS measurement");
        } else {
            Logger.i("Successfully resolved target address");
            PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
            ArrayList<MeasurementResult> results = new ArrayList<>();
            MeasurementResult result;
//            for (DNSWrapper wrap : responses) {
            result = new MeasurementResult(
                    phoneUtils.getDeviceInfo().deviceId,
                    phoneUtils.getDeviceProperty(this.getKey()),
                    DnsLookupTask.TYPE,
                    System.currentTimeMillis() * 1000,
                    TaskProgress.COMPLETED, this.measurementDesc);

            // now turn the result into an array of hashmaps with the data we care about
            List<HashMap<String, Object>> data = extractResults(responses);
            result.addResult("results", data);
            result.addResult("target", desc.target);
            result.addResult("qtype", desc.qtype);
            result.addResult("qclass", desc.qclass);

            //AITOR
            if (resultInet != null) {
                result.addResult("address", resultInet.getHostAddress());
                result.addResult("real_hostname", resultInet.getCanonicalHostName());
                result.addResult("time_ms", totalTime / successCnt);
            }

            Logger.i(MeasurementJsonConvertor.toJsonString(result));
            results.add(result);
//            }

            // create the result array to return
            MeasurementResult resultsFinal[] = new MeasurementResult[results.size()];
            for (int i = 0; i < resultsFinal.length; i++) {
                resultsFinal[i] = results.get(i);
            }

            return resultsFinal;
        }
    }

    public List<HashMap<String, Object>> extractResults(ArrayList<DNSWrapper> responses) {
        ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        for (DNSWrapper wrap : responses) {
            Message resp = null;
            if (wrap.isValid) {
                resp = wrap.response;
            }
            HashMap<String, Object> item = new HashMap<>();
            item.put("server", wrap.server);
            item.put("qryId", wrap.qid);
            item.put("respId", wrap.id);
            item.put("payload", wrap.rawOutput);
            item.put("respTime", wrap.respTime);
            item.put("isValid", wrap.isValid);
            item.put("rcode", Rcode.string(resp.getHeader().getRcode()));
            item.put("tc", resp.getHeader().getFlag(Flags.TC));

            // process the question
            Record[] questionRecs = resp.getSectionArray(0);
            if (questionRecs.length == 0) {
                item.put("domain", null);
                item.put("qtype", null);
                item.put("qclass", null);
            } else {
                Record rec = questionRecs[0];
                item.put("domain", rec.getName().toString());
                item.put("qtype", Type.string(rec.getType()));
                item.put("qclass", DClass.string(rec.getDClass()));
            }

            // now process the answers
            List<HashMap<String, String>> answers = new ArrayList<>();
            questionRecs = resp.getSectionArray(1);
            for (Record recd : questionRecs) {
                HashMap<String, String> entry = new HashMap<>();
                entry.put("name", recd.getName().toString());
                entry.put("rtype", Type.string(recd.getType()));
                entry.put("rdata", String.valueOf(recd.getRRsetType()));
                answers.add(entry);
            }
            item.put("answers", answers.toArray());
            data.add(item);
        }
        return data;
    }

    @SuppressWarnings("rawtypes")
    public static Class getDescClass() throws InvalidClassException {
        return DnsLookupDesc.class;
    }

    @Override
    public String getType() {
        return DnsLookupTask.TYPE;
    }

    @Override
    public String getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public String toString() {
        DnsLookupDesc desc = (DnsLookupDesc) measurementDesc;
        return "[DNS Lookup]\n  Target: " + desc.target + "\n  Interval (sec): " + desc.intervalSec + "\n  Next run: " + desc.startTime;
    }

    @Override
    public boolean stop() {
        //There is nothing we need to do to stop the DNS measurement
        return false;
    }

    @Override
    public long getDuration() {
        return this.duration;
    }

    @Override
    public void setDuration(long newDuration) {
        if (newDuration < 0) {
            this.duration = 0;
        } else {
            this.duration = newDuration;
        }
    }

    /**
     * Since it is hard to get the amount of data sent directly,
     * use a fixed value.  The data consumed is usually small, and the fixed
     * value is a conservative estimate.
     * <p/>
     * TODO find a better way to get this value
     */
    @Override
    public long getDataConsumed() {
        return AVG_DATA_USAGE_BYTE;
    }
}