package com.eurobcreative.monroe.gcm;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.eurobcreative.monroe.UpdateIntent;
import com.eurobcreative.monroe.util.Logger;
import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a partial wake lock for
 * this service while the service does its work. When the service is finished, it calls
 * {@code completeWakefulIntent()} to release the wake lock.
 */
public class GcmIntentService extends IntentService {

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) { // has effect of unparcelling Bundle
            /*
            * Filter messages based on message type. Since it is likely that GCM will be extended in the
            * future with new message types, just ignore any message types you're not interested in, or
            * that you don't recognize.
            */
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                Logger.d("GCMManager -> GcmIntentService -> MESSAGE_TYPE_SEND_ERROR");
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                Logger.d("GCMManager -> GcmIntentService -> MESSAGE_TYPE_DELETED");
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                Logger.d("GCMManager -> MESSAGE_TYPE_MESSAGE -> " + extras.toString());
                if (extras.containsKey("task")) {
                    Logger.d("GCMManager -> " + extras.getString("task"));

                    Intent newintent = new Intent();
                    newintent.setAction(UpdateIntent.GCM_MEASUREMENT_ACTION);
                    newintent.putExtra(UpdateIntent.MEASUREMENT_TASK_PAYLOAD, extras.getString("task"));
                    sendBroadcast(newintent);

                }
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }
}