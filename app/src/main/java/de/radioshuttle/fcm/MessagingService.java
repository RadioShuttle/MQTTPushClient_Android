/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.fcm;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.FirebaseMessagingService;

import java.util.List;
import java.util.Map;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;

public class MessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        if (remoteMessage.getData().size() > 0) {
            // Log.d(TAG, "Message data payload: " + remoteMessage.getData());

            sendNotification(remoteMessage.getData());
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();
        //TODO
    }


    protected void sendNotification(Map<String, String> data) {
        Log.d(TAG, "Messaging service called.");

        String channelID = "MQTT notification test";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(channelID) == null) {
                createChannel(channelID, getApplicationContext());
            }
        }

        NotificationCompat.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new NotificationCompat.Builder(this, channelID);
        } else {
            b = new NotificationCompat.Builder(this);
            //TODO: consider using an own unique ringtone
            // Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        String title = data.get("title");
        String text = data.get("text");
        if (title == null) {
            title = "Test " + (System.currentTimeMillis() & 0xFFL);
        }
        if (text == null) {
            text = "moin " + (System.currentTimeMillis() & 0xFFL);
        }

        b.setContentTitle(title);
        b.setContentText(text);
        b.setSubText("abc");

        b.setAutoCancel(false);

        if (Build.VERSION.SDK_INT < 26) {
            b.setDefaults(0);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            b.setSmallIcon(R.drawable.ic_notification_devices_other_vec);
        } else {
            // vector drawables not work here for versions pror lolipop
            b.setSmallIcon(R.drawable.ic_notification_devices_other_img);
        }

        Notification notification = b.build();

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(channelID, 0, notification);
        Log.d(TAG, "Messaging notify called.");

    }

    @TargetApi(26)
    public static void createChannel(String channelId, Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel nc = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_LOW);
        // nc.setDescription("Non alarm events");
        nc.enableLights(false);
        nc.enableVibration(false);
        nc.setBypassDnd(false);
        nm.createNotificationChannel(nc);
        Log.d(TAG, "notification channel created.");


    }

    @TargetApi(26)
    public static void removeUnusedChannels(List<PushAccount> notAllowedUsers, Context context) {
        //TODO:
    }

    private final static String TAG = MessagingService.class.getSimpleName();
}
