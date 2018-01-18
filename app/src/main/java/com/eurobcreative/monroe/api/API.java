package com.eurobcreative.monroe.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import com.eurobcreative.monroe.Config;
import com.eurobcreative.monroe.MeasurementScheduler;
import com.eurobcreative.monroe.MeasurementTask;
import com.eurobcreative.monroe.UpdateIntent;
import com.eurobcreative.monroe.exceptions.MeasurementError;
import com.eurobcreative.monroe.measurements.HttpTask;
import com.eurobcreative.monroe.util.Logger;

import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * @author jackjia, Hongyi Yao (hyyao@umich.edu)
 *         The user API for Mobiperf library.
 *         Use singleton design pattern to ensure there only exist one instance of API
 *         User: create and add task => Scheduler: run task, send finish intent =>
 *         User: register BroadcastReceiver for userResultAction and serverResultAction
 */
public final class API {
    public enum TaskType {
        HTTP, VIDEOQOE
    }

    /**
     * Action name of different type of result for broadcast receiver.
     * userResultAction is not a constant value. We append the clientKey to
     * UpdateIntent.USER_RESULT_ACTION so that only the user who submit the task
     * can get the result
     */
    public String userResultAction;
    public String batteryThresholdAction;
    public String checkinIntervalAction;
    public String taskStatusAction;
    public String dataUsageAction;
    public String authAccountAction;

    private Context applicationContext;

    private boolean isBound = false;
    private boolean isBindingToService = false;
    Messenger mSchedulerMessenger = null;

    private String clientKey;

    /**
     * Singleton api object for the entire application
     */
    private static API apiObject;
    private Queue<Message> pendingMsg;

    /**
     * Make constructor private for singleton design
     *
     * @param parent    Context when the object is created
     * @param clientKey User-defined unique key for this application
     */
    private API(Context parent, String clientKey) {
        Logger.d("API: constructor is called...");
        this.applicationContext = parent.getApplicationContext();
        this.clientKey = clientKey;
        this.pendingMsg = new LinkedList<>();

        this.userResultAction = UpdateIntent.USER_RESULT_ACTION + "." + clientKey;

        this.batteryThresholdAction = UpdateIntent.BATTERY_THRESHOLD_ACTION + "." + clientKey;
        this.checkinIntervalAction = UpdateIntent.CHECKIN_INTERVAL_ACTION + "." + clientKey;
        this.taskStatusAction = UpdateIntent.TASK_STATUS_ACTION + "." + clientKey;
        this.dataUsageAction = UpdateIntent.DATA_USAGE_ACTION + "." + clientKey;
        this.authAccountAction = UpdateIntent.AUTH_ACCOUNT_ACTION + "." + clientKey;
        startAndBindService();
    }

    /**
     * Actual method to get the singleton API object
     *
     * @param parent    Context which the object lies in
     * @param clientKey User-defined unique key for this application
     * @return Singleton API object
     */
    public static API getAPI(Context parent, String clientKey) {
        Logger.d("API: Get API Singeton object...");
        if (apiObject == null) {
            Logger.d("API: API object not initialized...");
            apiObject = new API(parent, clientKey);
        } else {
            // Safeguard to avoid using unbound API object
            apiObject.startAndBindService();
        }
        return apiObject;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        // Prevent the singleton object to be copied
        throw new CloneNotSupportedException();
    }

    /**
     * Defines callbacks for binding and unbinding scheduler
     */
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.d("API -> onServiceConnected called");
            // We've bound to the scheduler's messenger and get Messenger instance
            mSchedulerMessenger = new Messenger(service);
            isBound = true;
            isBindingToService = false;

            Logger.i("Register client key");
            Message msg = Message.obtain(null, Config.MSG_REGISTER_CLIENTKEY);
            Bundle data = new Bundle();
            data.putString(UpdateIntent.CLIENTKEY_PAYLOAD, clientKey);
            msg.setData(data);
            try {
                sendMessage(msg);
            } catch (MeasurementError e) {
                Logger.e("Register clientKey failed", e);
            }

            Logger.i("Send pending message");
            while ((msg = pendingMsg.poll()) != null) {
                try {
                    sendMessage(msg);
                } catch (MeasurementError e) {
                    Logger.e("Send pending message failed", e);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // This callback is never called until the active scheduler is uninstalled
            Logger.d("API -> onServiceDisconnected called");
            mSchedulerMessenger = null;
            isBound = false;
            // Start and bind to another scheduler (probably bind to the one in itself)
            Logger.e("API -> startAndBind again");
            startAndBindService();
        }
    };

    /**
     * Bind to scheduler, automatically called when the API is initialized
     */
    public void startAndBindService() {
        Logger.e("API-> startAndBindService() called " + isBindingToService + " " + isBound);
        if (!isBindingToService && !isBound) {
            Logger.e("API-> bind() called 2");
            Intent intent = new Intent(applicationContext, MeasurementScheduler.class);
            intent.putExtra(UpdateIntent.CLIENTKEY_PAYLOAD, clientKey);
            intent.putExtra(UpdateIntent.VERSION_PAYLOAD, Config.version);
            /**
             * Start and bind to service if it is not bounded.
             * Notice that we don't use BIND_AUTO_CREATE flag since it will prevent
             * the scheduler to kill itself when stopSelf is called
             */
            applicationContext.startService(intent);
            applicationContext.bindService(intent, serviceConn, 0);
            isBindingToService = true;
        }
    }

    /**
     * Unbind from scheduler, called in activity's onDestroy callback function
     */
    public void unbind() {
        Logger.e("API-> unbind called");
        if (isBound) {
            Logger.e("API-> unbind called 2");
            // Register client key
            Message msg = Message.obtain(null, Config.MSG_UNREGISTER_CLIENTKEY);
            Bundle data = new Bundle();
            data.putString(UpdateIntent.CLIENTKEY_PAYLOAD, clientKey);
            msg.setData(data);
            try {
                sendMessage(msg);
            } catch (MeasurementError e) {
                Logger.e("Unregister clientKey failed", e);
            }
            // Unbind service
            applicationContext.unbindService(serviceConn);
            isBound = false;
        }
    }

    /**
     * Create a new MeasurementTask based on those parameters. Then submit it to
     * scheduler by addTask or put into task list of parallel or sequential task
     *
     * @param taskType           Type of measurement (ping, dns, traceroute, etc.) for this
     *                           measurement task.
     * @param startTime          Earliest time that measurements can be taken using this
     *                           Task descriptor. The current time will be used in place of a null
     *                           startTime parameter. Measurements with a startTime more than 24
     *                           hours from now will NOT be run.
     * @param endTime            Latest time that measurements can be taken using this Task
     *                           descriptor. Tasks with an endTime before startTime will be canceled.
     *                           Corresponding to the 24-hour rule in startTime, tasks with endTime
     *                           later than 24 hours from now will be assigned a new endTime that
     *                           ends 24 hours from now.
     * @param intervalSec        Minimum number of seconds to elapse between consecutive
     *                           measurements taken with this description.
     * @param count              Maximum number of times that a measurement should be taken
     *                           with this description. A count of 0 means to continue the
     *                           measurement indefinitely (until end_time).
     * @param priority           Two level of priority: USER_PRIORITY for user task and
     *                           INVALID_PRIORITY for server task
     * @param contextIntervalSec interval between the context collection (in sec)
     * @param params             Measurement parameters.
     * @return Measurement task filled with those parameters
     * @throws MeasurementError taskType is not valid
     */
    public MeasurementTask createTask(TaskType taskType, Date startTime, Date endTime, double intervalSec,
                                      long count, long priority, int contextIntervalSec, Map<String, String> params,
                                      Context context) throws MeasurementError {
        MeasurementTask task;
        switch (taskType) {
            case HTTP:
                task = new HttpTask(new HttpTask.HttpDesc(clientKey, startTime, endTime,
                        intervalSec, count, priority, contextIntervalSec, params), context);
                break;

            default:
                throw new MeasurementError("Undefined measurement type. Candidate: DNSLOOKUP, HTTP, PING, TRACEROUTE, TCPTHROUGHPUT, UDPBURST");
        }
        return task;
    }

    /**
     * Get available messenger after binding to scheduler
     *
     * @return the messenger if bound, null otherwise
     */
    private Messenger getScheduler() {
        if (isBound) {
            Logger.e("API -> get available messenger");
            return mSchedulerMessenger;
        } else {
            Logger.e("API -> have not bound to a scheduler!");
            return null;
        }
    }

    /**
     * Helper method for sending messages to the scheduler
     *
     * @param msg message to be sent
     * @throws MeasurementError
     */
    private void sendMessage(Message msg) throws MeasurementError {
        Messenger messenger = getScheduler();
        if (messenger != null) {
            // Append client key to every msg sent from API
            Bundle data = msg.getData();
            if (data == null) {
                data = new Bundle();
                msg.setData(data);
            }
            data.putString(UpdateIntent.CLIENTKEY_PAYLOAD, clientKey);

            try {
                messenger.send(msg);
            } catch (RemoteException e) {
                String err = "remote scheduler failed!";
                Logger.e(err);
                throw new MeasurementError(err);
            }
        } else {
            Logger.e("API didn't bind to a scheduler. Message will be temporarily queued and sent after scheduler bound");
            this.pendingMsg.offer(msg);
        }
    }

    /**
     * Submit task to the scheduler. Works in async way. The result will be returned in a intent
     * whose action is USER_RESULT_ACTION + clientKey or SERVER_RESULT_ACTION
     *
     * @param task the task to be executed, created by createTask(..) or composeTask(..)
     * @throws MeasurementError
     */
    public void submitTask(MeasurementTask task) throws MeasurementError {
        Logger.d("API->submitTask called");
        if (task != null) {
            Logger.i("API: Adding new " + task.getType() + " task " + task.getTaskId());
            Message msg = Message.obtain(null, Config.MSG_SUBMIT_TASK);
            Bundle data = new Bundle();
            data.putParcelable(UpdateIntent.MEASUREMENT_TASK_PAYLOAD, task);
            msg.setData(data);
            sendMessage(msg);

        } else {
            String err = "submitTask: task is null";
            Logger.e(err);
            throw new MeasurementError(err);
        }
    }
}