package com.eurobcreative.monroe;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.PhoneUtils;
import com.eurobcreative.monroe.MeasurementScheduler.DataUsageProfile;
import com.eurobcreative.monroe.MeasurementScheduler.TaskStatus;

/**
 * @author Hongyi Yao (hyyao@umich.edu)
 *         Define message handler to process message request from API
 */
public class APIRequestHandler extends Handler {
    MeasurementScheduler scheduler;

    /**
     * Constructor for APIRequestHandler
     *
     * @param scheduler Parent context for this object
     */
    public APIRequestHandler(MeasurementScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void handleMessage(Message msg) {
        Bundle data = msg.getData();
        data.setClassLoader(scheduler.getApplicationContext().getClassLoader());
        String clientKey = data.getString(UpdateIntent.CLIENTKEY_PAYLOAD);

        MeasurementTask task;
        String taskId = null;
        int batteryThreshold;
        long interval;
        Intent intent = new Intent();
        DataUsageProfile profile;
        boolean isForced = (PhoneUtils.clientKeySet.size() == 1);
        String account;
        switch (msg.what) {
            case Config.MSG_REGISTER_CLIENTKEY:
                Logger.i("App " + clientKey + " registered");
                synchronized (PhoneUtils.clientKeySet) {
                    PhoneUtils.clientKeySet.add(clientKey);
                }
                break;

            case Config.MSG_UNREGISTER_CLIENTKEY:
                Logger.i("App " + clientKey + " unregistered");
                synchronized (PhoneUtils.clientKeySet) {
                    PhoneUtils.clientKeySet.remove(clientKey);
                }
                break;

            case Config.MSG_SUBMIT_TASK:
                task = data.getParcelable(UpdateIntent.MEASUREMENT_TASK_PAYLOAD);
                if (task != null) {
                    Logger.i("Request Handler: " + clientKey + " submit task " + task.getTaskId());
                    scheduler.submitTask(task);
                }
                break;

            case Config.MSG_CANCEL_TASK:
                taskId = data.getString(UpdateIntent.TASKID_PAYLOAD);
                if (taskId != null && clientKey != null) {
                    Logger.i("Request Handler: " + clientKey + " cancel task " + taskId);
                    scheduler.cancelTask(taskId, clientKey);
                }
                break;

            case Config.MSG_SET_BATTERY_THRESHOLD:
                batteryThreshold = data.getInt(UpdateIntent.BATTERY_THRESHOLD_PAYLOAD);
                if (batteryThreshold != -1) {
                    Logger.i("Request Handler: " + clientKey + " set battery threshold to " + batteryThreshold);
                    scheduler.setBatteryThresh(isForced, batteryThreshold);
                } else {
                    Logger.e("Request Handler:  didn't find battery threshold's value");
                }
                break;

            case Config.MSG_GET_BATTERY_THRESHOLD:
                batteryThreshold = scheduler.getBatteryThresh();
                Logger.i("Request Handler: " + clientKey + " get battery threshold " + batteryThreshold);
                intent.setAction(UpdateIntent.BATTERY_THRESHOLD_ACTION + "." + clientKey);
                intent.putExtra(UpdateIntent.BATTERY_THRESHOLD_PAYLOAD, batteryThreshold);
                sendToClient(intent, null);
                break;

            case Config.MSG_SET_CHECKIN_INTERVAL:
                interval = data.getLong(UpdateIntent.CHECKIN_INTERVAL_PAYLOAD);
                if (interval != -1) {
                    Logger.i("Request Handler: " + clientKey + " set checkin interval to " + interval);
                    scheduler.setCheckinInterval(isForced, interval);
                } else {
                    Logger.e("Request Handler:  didn't find checkin interval's value");
                }
                break;

            case Config.MSG_GET_CHECKIN_INTERVAL:
                interval = scheduler.getCheckinInterval();
                Logger.i("Request Handler: " + clientKey + " get checkin interval " + interval);
                intent.setAction(UpdateIntent.CHECKIN_INTERVAL_ACTION + "." + clientKey);
                intent.putExtra(UpdateIntent.CHECKIN_INTERVAL_PAYLOAD, interval);
                sendToClient(intent, null);
                break;

            case Config.MSG_GET_TASK_STATUS:
                taskId = data.getString(UpdateIntent.TASKID_PAYLOAD);
                TaskStatus taskStatus = scheduler.getTaskStatus(taskId);
                Logger.i("Request Handler: " + clientKey + " get task status for taskId " + taskId + " " + taskStatus);
                intent.setAction(UpdateIntent.TASK_STATUS_ACTION + "." + clientKey);
                intent.putExtra(UpdateIntent.TASKID_PAYLOAD, taskId);
                intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, taskStatus);
                sendToClient(intent, taskId);
                break;

            case Config.MSG_SET_DATA_USAGE:
                profile = (DataUsageProfile) data.getSerializable(UpdateIntent.DATA_USAGE_PAYLOAD);
                if (profile != null) {
                    Logger.i("Request Handler: " + clientKey + " set data usage to " + profile);
                    scheduler.setDataUsageLimit(isForced, profile);
                } else {
                    Logger.e("Scheduler: didn't found data usage profile's value");
                }
                break;

            case Config.MSG_GET_DATA_USAGE:
                profile = scheduler.getDataUsageProfile();
                Logger.i("Request Handler: " + clientKey + " get data usage " + profile);
                intent.setAction(UpdateIntent.DATA_USAGE_ACTION + "." + clientKey);
                intent.putExtra(UpdateIntent.DATA_USAGE_PAYLOAD, profile);
                sendToClient(intent, taskId);
                break;

            case Config.MSG_SET_AUTH_ACCOUNT:
                account = data.getString(UpdateIntent.AUTH_ACCOUNT_PAYLOAD);
                if (account != null) {
                    Logger.i("Request Handler: " + clientKey + " set authenticate account" + " to " + account);
                    scheduler.setAuthenticateAccount(account);
                }
                break;

            case Config.MSG_GET_AUTH_ACCOUNT:
                account = scheduler.getAuthenticateAccount();
                Logger.i("Request Handler: " + clientKey + " get authenticate account " + account);
                intent.setAction(UpdateIntent.AUTH_ACCOUNT_ACTION + "." + clientKey);
                intent.putExtra(UpdateIntent.AUTH_ACCOUNT_PAYLOAD, account);
                sendToClient(intent, taskId);
                break;

            default:
                break;
        }
    }

    private void sendToClient(Intent intent, String taskId) {
        if (taskId != null) {
            intent.putExtra(UpdateIntent.TASKID_PAYLOAD, taskId);
        }
        scheduler.sendBroadcast(intent);
    }
}