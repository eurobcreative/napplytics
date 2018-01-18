package com.eurobcreative.monroe.measurements;

import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;
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
import com.eurobcreative.monroe.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Map;

import static com.eurobcreative.monroe.activities.MainActivity.NAPPLYTICS_ACTION;
import static com.eurobcreative.monroe.activities.MainActivity.THROUGHPUT_RESULT;

/**
 * A Callable class that performs download throughput test using HTTP get
 */
public class HttpTask extends MeasurementTask {
    Context context;

    // Type name for internal use
    public static final String TYPE = "http";
    /* TODO(Wenjie): Depending on state machine configuration of cell tower's radio,
     * the size to find the 'real' bandwidth of the phone may be network dependent.
     */
    // The maximum number of bytes we will read from requested URL. Set to 1Mb.
    public static final long MAX_HTTP_RESPONSE_SIZE = 1024 * 1024;
    // The size of the response body we will report to the service.
    // If the response is larger than MAX_BODY_SIZE_TO_UPLOAD bytes, we will
    // only report the first MAX_BODY_SIZE_TO_UPLOAD bytes of the body.
    public static final int MAX_BODY_SIZE_TO_UPLOAD = 1024;
    // The buffer size we use to read from the HTTP response stream
    public static final int READ_BUFFER_SIZE = 1024;
    // Not used by the HTTP protocol. Just in case we do not receive a status line from the response
    public static final int DEFAULT_STATUS_CODE = 0;

    //Track data consumption for this task to avoid exceeding user's limit
    private long dataConsumed;

    private long duration;

    public HttpTask(MeasurementDesc desc, Context context) {
        super(new HttpDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
                desc.count, desc.priority, desc.contextIntervalSec, desc.parameters));
        this.duration = Config.DEFAULT_HTTP_TASK_DURATION;
        this.dataConsumed = 0;

        this.context = context;
    }

    protected HttpTask(Parcel in) {
        super(in);
        duration = in.readLong();
        dataConsumed = in.readLong();
    }

    public static final Parcelable.Creator<HttpTask> CREATOR = new Parcelable.Creator<HttpTask>() {
        public HttpTask createFromParcel(Parcel in) {
            return new HttpTask(in);
        }

        public HttpTask[] newArray(int size) {
            return new HttpTask[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(duration);
        dest.writeLong(dataConsumed);
    }

    /**
     * The description of a HTTP measurement
     */
    public static class HttpDesc extends MeasurementDesc {
        public String url;
        private String headers;
        private String body;

        public HttpDesc(String key, Date startTime, Date endTime, double intervalSec, long count,
                        long priority, int contextIntervalSec, Map<String, String> params) throws InvalidParameterException {
            super(HttpTask.TYPE, key, startTime, endTime, intervalSec, count, priority, contextIntervalSec, params);
            initializeParams(params);
            if (this.url == null || this.url.length() == 0) {
                throw new InvalidParameterException("URL for http task is null");
            }
        }

        @Override
        protected void initializeParams(Map<String, String> params) {
            if (params == null) {
                return;
            }

            this.url = params.get("url");
            if (!this.url.startsWith("http://") && !this.url.startsWith("https://")) {
                this.url = "http://" + this.url;
            }

            this.headers = params.get("headers");
            this.body = params.get("body");
        }

        @Override
        public String getType() {
            return HttpTask.TYPE;
        }


        protected HttpDesc(Parcel in) {
            super(in);
            url = in.readString();
            headers = in.readString();
            body = in.readString();
        }

        public static final Parcelable.Creator<HttpDesc> CREATOR = new Parcelable.Creator<HttpDesc>() {
            public HttpDesc createFromParcel(Parcel in) {
                return new HttpDesc(in);
            }

            public HttpDesc[] newArray(int size) {
                return new HttpDesc[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(url);
            dest.writeString(headers);
            dest.writeString(body);
        }
    }

    /**
     * Returns a copy of the HttpTask
     */
    @Override
    public MeasurementTask clone() {
        MeasurementDesc desc = this.measurementDesc;
        HttpDesc newDesc = new HttpDesc(desc.key, desc.startTime, desc.endTime, desc.intervalSec,
                desc.count, desc.priority, desc.contextIntervalSec, desc.parameters);
        return new HttpTask(newDesc, context);
    }

    /**
     * Runs the HTTP measurement task. Will acquire power lock to ensure wifi
     * is not turned off
     */
    @Override
    public MeasurementResult[] call() throws MeasurementError {

        int statusCode = HttpTask.DEFAULT_STATUS_CODE;
        long duration = 0;
        long originalHeadersLen = 0;
        String headers;
        ByteBuffer body = ByteBuffer.allocate(HttpTask.MAX_BODY_SIZE_TO_UPLOAD);
        TaskProgress taskProgress = TaskProgress.FAILED;
        String errorMsg = "";
        InputStream inputStream = null;

        long currentRxTx = Util.getCurrentRxTxBytes();

        try {
            // set the download URL, a URL that points to a file on the Internet this is the file to be downloaded
            HttpDesc task = (HttpDesc) this.measurementDesc;
            String urlStr = task.url;

            URL urlObj = new URL(urlStr);
            HttpURLConnection httpURLConnection = (HttpURLConnection) urlObj.openConnection();
            httpURLConnection.setRequestMethod("GET");

            if (task.headers != null && task.headers.trim().length() > 0) {
                for (String headerLine : task.headers.split("\r\n")) {
                    String tokens[] = headerLine.split(":");
                    if (tokens.length == 2) {
                        httpURLConnection.setRequestProperty(tokens[0], tokens[1]);
                    } else {
                        throw new MeasurementError("Incorrect header line: " + headerLine);
                    }
                }
            }

            byte[] readBuffer = new byte[HttpTask.READ_BUFFER_SIZE];
            int readLen;
            int totalBodyLen = 0;

            long startTime = System.currentTimeMillis();

            /* TODO(Wenjie): HttpClient does not automatically handle the following codes
            * 301 Moved Permanently. HttpStatus.SC_MOVED_PERMANENTLY
            * 302 Moved Temporarily. HttpStatus.SC_MOVED_TEMPORARILY
            * 303 See Other. HttpStatus.SC_SEE_OTHER
            * 307 Temporary Redirect. HttpStatus.SC_TEMPORARY_REDIRECT
            *
            * We may want to fetch instead from the redirected page.
            */
            if (httpURLConnection != null) {
                statusCode = httpURLConnection.getResponseCode();
                if (statusCode == 200) {
                    taskProgress = TaskProgress.COMPLETED;
                } else {
                    taskProgress = TaskProgress.FAILED;
                }
            }

            try {
                if (httpURLConnection != null) {
                    inputStream = httpURLConnection.getInputStream();
                    while ((readLen = inputStream.read(readBuffer)) > 0 && totalBodyLen <= HttpTask.MAX_HTTP_RESPONSE_SIZE) {
                        totalBodyLen += readLen;
                        // Fill in the body to report up to MAX_BODY_SIZE
                        if (body.remaining() > 0) {
                            int putLen = body.remaining() < readLen ? body.remaining() : readLen;
                            body.put(readBuffer, 0, putLen);
                        }
                    }
                    duration = System.currentTimeMillis() - startTime;//TODO check this
                }
            } finally {
                httpURLConnection.disconnect();
            }

            headers = "";
            for (int i = 0; ; i++) {
                String headerName = httpURLConnection.getHeaderFieldKey(i);
                String headerValue = httpURLConnection.getHeaderField(i);

                if (headerName == null && headerValue == null) {
                    break; //No more headers
                } else {
                    /*
                    * TODO(Wenjie): There can be preceding and trailing white spaces in
                    * each header field. I cannot find internal methods that return the
                    * number of bytes in a header. The solution here assumes the encoding
                    * is one byte per character.
                    */
                    originalHeadersLen += headerValue.length();
                    headers += headerValue + "\r\n";
                }
            }

            PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();

            MeasurementResult result = new MeasurementResult(
                    phoneUtils.getDeviceInfo().deviceId,
                    phoneUtils.getDeviceProperty(this.getKey()),
                    HttpTask.TYPE, System.currentTimeMillis() * 1000,
                    taskProgress, this.measurementDesc);

            result.addResult("code", statusCode);

            dataConsumed += (Util.getCurrentRxTxBytes() - currentRxTx);

            if (taskProgress == TaskProgress.COMPLETED) {
                result.addResult("time_ms", duration);
                result.addResult("headers_len", originalHeadersLen);
                result.addResult("body_len", totalBodyLen);
                result.addResult("headers", headers);
                result.addResult("body", Base64.encodeToString(body.array(), Base64.DEFAULT));
            }

            Intent intent = new Intent();
            float throughput = (((float)(originalHeadersLen + totalBodyLen) * 8.0f) / 1000.0f) / ((float) duration / 1000.0f); // throughput -> Kbps
            intent.putExtra(THROUGHPUT_RESULT, (long) Math.round(throughput));
            intent.setAction(NAPPLYTICS_ACTION);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

            Logger.i(MeasurementJsonConvertor.toJsonString(result));
            MeasurementResult[] mrArray = new MeasurementResult[1];
            mrArray[0] = result;
            return mrArray;
        } catch (MalformedURLException e) {
            errorMsg += e.getMessage() + "\n";
            Logger.e(e.getMessage());
        } catch (IOException e) {
            errorMsg += e.getMessage() + "\n";
            Logger.e(e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    Logger.e("Fails to close the input stream from the HTTP response");
                }
            }
        }
        throw new MeasurementError("Cannot get result from HTTP measurement because " + errorMsg);
    }

    @Override
    public String getType() {
        return HttpTask.TYPE;
    }

    @Override
    public String getDescriptor() {
        return Util.HTTP_DESCRIPTOR;
    }

    @Override
    public String toString() {
        HttpDesc desc = (HttpDesc) measurementDesc;
        return "[HTTP Target: " + desc.url + "\n  Interval (sec): " + desc.intervalSec + "\n  Next run: " + desc.startTime;
    }

    @Override
    public boolean stop() {
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
     * Data used so far by the task.
     */
    @Override
    public long getDataConsumed() {
        return dataConsumed;
    }
}