package com.eurobcreative.monroe;

import android.content.Intent;

import com.eurobcreative.monroe.exceptions.MeasurementError;
import com.eurobcreative.monroe.util.Logger;
import com.eurobcreative.monroe.util.PhoneUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Callable;

public class UserMeasurementTask implements Callable<MeasurementResult[]> {
    private MeasurementTask realTask;
    private MeasurementScheduler scheduler;
    private ContextCollector contextCollector;

    public UserMeasurementTask(MeasurementTask task, MeasurementScheduler scheduler) {
        realTask = task;
        this.scheduler = scheduler;
        this.contextCollector = new ContextCollector();
    }

    /**
     * Notify the scheduler that this task is started
     */
    private void broadcastMeasurementStart() {
        Intent intent = new Intent();
        intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);
        intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_STARTED);
        intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.getTaskId());
        intent.putExtra(UpdateIntent.CLIENTKEY_PAYLOAD, realTask.getKey());
        scheduler.sendBroadcast(intent);
    }

    /**
     * Notify the scheduler that this task is finished executing. The result can be completed, paused or failed due to exception
     *
     * @param results Results of the task
     */
    private void broadcastMeasurementEnd(MeasurementResult[] results) {
        Intent intent = new Intent();
        intent.setAction(UpdateIntent.MEASUREMENT_PROGRESS_UPDATE_ACTION);
        //TODO fixed one value priority for all users task?
        intent.putExtra(UpdateIntent.TASK_PRIORITY_PAYLOAD, MeasurementTask.USER_PRIORITY);
        intent.putExtra(UpdateIntent.TASKID_PAYLOAD, realTask.getTaskId());
        intent.putExtra(UpdateIntent.CLIENTKEY_PAYLOAD, realTask.getKey());

        if (results != null) {
            //TODO only single task can be paused
            if (results[0].getTaskProgress() == MeasurementResult.TaskProgress.PAUSED) {
                intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_PAUSED);
            } else {
                intent.putExtra(UpdateIntent.TASK_STATUS_PAYLOAD, Config.TASK_FINISHED);
                intent.putExtra(UpdateIntent.RESULT_PAYLOAD, results);
            }
            scheduler.sendBroadcast(intent);
        }
    }

    /**
     * The call() method that broadcast intents before the measurement starts
     * and after the measurement finishes.
     */
    @Override
    public MeasurementResult[] call() throws MeasurementError {
        MeasurementResult[] results = null;
        PhoneUtils phoneUtils = PhoneUtils.getPhoneUtils();
        try {
            phoneUtils.acquireWakeLock();
            broadcastMeasurementStart();
            contextCollector.setInterval(realTask.getDescription().contextIntervalSec);
            contextCollector.startCollector();
            results = realTask.call();
            ArrayList<HashMap<String, String>> contextResults = contextCollector.stopCollector();
            for (MeasurementResult r : results) {
                r.addContextResults(contextResults);
                r.getDeviceProperty().dnResolvability = contextCollector.dnsConnectivity;
                r.getDeviceProperty().ipConnectivity = contextCollector.ipConnectivity;
            }
        } catch (MeasurementError e) {
            Logger.e("User measurement " + realTask.getDescriptor() + " has failed");
            Logger.e(e.getMessage());
            results = MeasurementResult.getFailureResult(realTask, e);
        } catch (Exception e) {
            Logger.e("User measurement " + realTask.getDescriptor() + " has failed");
            Logger.e("Unexpected Exception: " + e.getMessage());
            results = MeasurementResult.getFailureResult(realTask, e);
        } finally {
            broadcastMeasurementEnd(results);
            MeasurementTask currentTask = scheduler.getCurrentTask();
            if (currentTask != null && currentTask.equals(realTask)) {
                scheduler.setCurrentTask(null);
            }
            phoneUtils.releaseWakeLock();
        }
        return results;
    }
}