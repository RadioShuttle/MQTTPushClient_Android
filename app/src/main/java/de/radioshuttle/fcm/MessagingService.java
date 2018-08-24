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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.FirebaseMessagingService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.Code;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.AccountListActivity;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.mqttpushclient.Utils;

import static de.radioshuttle.mqttpushclient.AccountListActivity.ARG_MQTT_ACCOUNT;
import static de.radioshuttle.mqttpushclient.AccountListActivity.ARG_PUSHSERVER_ID;

public class MessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // [START_EXCLUDE]
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only when here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
        // [END_EXCLUDE]

        if (remoteMessage.getData().size() > 0) {
            // Log.d(TAG, "Message data payload: " + remoteMessage.getData());

            processMessage(remoteMessage.getData());
        }
    }

    @Override
    public void onDeletedMessages() {
        super.onDeletedMessages();

        Notifications.MessageInfo m = Notifications.getMessageInfo(this);
        // Log.d(TAG, m.group +" " + m.groupId);
        if (m.groupId == 0) {
            return; // no accounts anymore
        }

        String title = getString(R.string.notification_deleted);
        String message = getString(R.string.notification_show_messages);

        Intent intent = new Intent(this, AccountListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(FCM_ON_DELETE, true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = null;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new NotificationCompat.Builder(this, m.group);
        } else {
            b = new NotificationCompat.Builder(this);
            //TODO: consider using an own unique ringtone
            // Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        b.setContentTitle(title);
        b.setContentText(message);

        if (Build.VERSION.SDK_INT >= 21) {
            b.setSmallIcon(R.drawable.ic_notification_devices_other_vec);
        } else {
            // vector drawables not work here for versions pror lolipop
            b.setSmallIcon(R.drawable.ic_notification_devices_other_img);
        }

        b.setOnlyAlertOnce(true);

        if (Build.VERSION.SDK_INT >= 25)
            b.setGroup(FCM_ON_DELETE);
        b.setAutoCancel(false);
        // b.setSound(defaultSoundUri);
        b.setContentIntent(pendingIntent);
        b.setShowWhen(true);

        if (Build.VERSION.SDK_INT < 26) {
            b.setPriority(Notification.PRIORITY_MAX);
            b.setDefaults(Notification.DEFAULT_ALL);
        }

        Notification notification = b.build();
        // TODO: consider to loop notification sound like an alarm message
        // notification.flags |= Notification.FLAG_INSISTENT;

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(FCM_ON_DELETE, 0, notification);
    }


    protected void processMessage(Map<String, String> data) {
        Log.d(TAG, "Messaging service called.");

        String channelID = data.get("account");
        if (Utils.isEmpty(channelID))
            return;

        String pushServerID = data.get("pushserverid");

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(channelID) == null) {
                createChannel(channelID, getApplicationContext());
            }
        }

        String msg = data.get("messages");
        Msg latestMsg = null;
        int cnt = 0;
        try {
            JSONArray msgsArray = new JSONArray(msg);
            AppDatabase db = null;
            Long psCode = null;
            Long accountCode = null;
            if (msgsArray.length() > 0) {
                db = AppDatabase.getInstance(getApplicationContext());
                if (Utils.isEmpty(pushServerID)) {
                    pushServerID = ""; //TODO: remove assignment if pushserverid will be transmitted
                }
                psCode = db.mqttMessageDao().getCode(pushServerID);
                if (psCode == null) {
                    Code c = new Code();
                    c.setName(pushServerID);
                    psCode = db.mqttMessageDao().insertCode(c);
                    // Log.d(TAG, " (before null) code: " + psCode);
                }
                accountCode = db.mqttMessageDao().getCode(channelID);
                if (accountCode == null) {
                    Code c = new Code();
                    c.setName(channelID);
                    accountCode = db.mqttMessageDao().insertCode(c);
                    // Log.d(TAG, " (before null) accountCode: " + accountCode);
                }
            }
            ArrayList<Integer> ids = new ArrayList<>();
            for(int i = 0; i < msgsArray.length(); i++) {
                JSONObject topic = msgsArray.getJSONObject(i);
                Iterator<String> it = topic.keys();
                long d;
                String base64;
                while(it.hasNext()) {
                    String t = it.next();
                    JSONArray msgsArrayPerTopic = topic.getJSONArray(t);
                    for(int j = 0; j < msgsArrayPerTopic.length(); j++) {
                        JSONArray entryArray = msgsArrayPerTopic.getJSONArray(j);
                        d = entryArray.getLong(0) * 1000L;
                        base64 = entryArray.getString(1);
                        Msg m = new Msg();
                        m.when = d;
                        m.msg = Base64.decode(base64, Base64.DEFAULT);
                        m.topic = t;
                        if (latestMsg == null) {
                            latestMsg = m;
                        } else if (latestMsg.when < (m.when)) {
                            latestMsg = m;
                        }
                        cnt++;
                        //TODO: consider removing Msg class and replace it with MqttMessage below
                        MqttMessage mqttMessage = new MqttMessage();
                        mqttMessage.setPushServerID(psCode.intValue());
                        mqttMessage.setMqttAccountID(accountCode.intValue());
                        mqttMessage.setWhen(m.when);
                        mqttMessage.setTopic(m.topic);
                        try {
                            mqttMessage.setMsg(new String(m.msg));
                        } catch(Exception e) {
                            // decoding error or payload not utf-8
                            mqttMessage.setMsg(base64); //TODO: error should be rare but consider using hex
                        }
                        Long k = db.mqttMessageDao().insertMqttMessage(mqttMessage);
                        if (k != null && k >= 0) {
                            ids.add(k.intValue());
                        }
                        Log.d(TAG, t + ": " + m.when + " " + new String(m.msg));
                    }
                }
            }

            /* delete all messages older than 30 days */
            long before = new Date().getTime() - (30L * 24L * 1000L * 3600L);
            db.mqttMessageDao().deleteMessagesBefore(before);

            /* inform about database changes, if app is running it can update its views */
            Intent intent = new Intent(MqttMessage.UPDATE_INTENT);
            intent.putExtra(MqttMessage.ARG_ACCOUNT, channelID);
            intent.putExtra(MqttMessage.ARG_IDS, ids);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

            /*
            long l = System.currentTimeMillis() - (1000L * 60L * 60L * 24 * 365);
            for(int i = 200; i > 0; i--) {
                MqttMessage m = new MqttMessage();
                m.setPushServerID(psCode.intValue());
                m.setMqttAccountID(accountCode.intValue());
                l += 1000L;
                m.setWhen(l);
                m.setTopic("autogerneated test data");
                m.setMsg("Test text: " + i);
                db.mqttMessageDao().insertMqttMessage(m);
            }
            */


        } catch(Exception e) {
            //TODO: error handling for db errors
            Log.d(TAG, "error parsing messages", e);
        }

        if (latestMsg != null) {
            Notifications.MessageInfo messageInfo = Notifications.getMessageInfo(this, channelID);
            if (messageInfo.groupId == 0) {
                messageInfo.groupId = messageInfo.noOfGroups + 1;
            }
            messageInfo.messageId++;
            Notifications.setMessageInfo(this, messageInfo);
            messageInfo.messageId += cnt;

            Intent intent = new Intent(this, AccountListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(ARG_MQTT_ACCOUNT, channelID);
            intent.putExtra(ARG_PUSHSERVER_ID, pushServerID);

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    messageInfo.groupId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);


            Intent delItent = new Intent(this, Notifications.class);
            delItent.setAction(Notifications.ACTION_CANCELLED);
            delItent.putExtra(Notifications.DELETE_GROUP, messageInfo.group);

            PendingIntent delPendingIntent = PendingIntent.getBroadcast(
                    this,
                    messageInfo.groupId, delItent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new NotificationCompat.Builder(this, channelID);
            } else {
                b = new NotificationCompat.Builder(this);
                //TODO: consider using an own unique ringtone
                // Uri defaultSoundUri= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }

            b.setContentTitle(channelID);
            b.setContentText(latestMsg.topic + ": " + new String(latestMsg.msg));
            b.setWhen(latestMsg.when);
            if (messageInfo.messageId > 1) {
                String more = String.format("+%d", (messageInfo.messageId - 1));
                b.setSubText(more);
            }
            if (Build.VERSION.SDK_INT >= 25) {
                b.setGroup(channelID);
            }

            b.setAutoCancel(false);
            b.setContentIntent(pendingIntent);
            b.setDeleteIntent(delPendingIntent);

            if (Build.VERSION.SDK_INT < 26) {
                //TODO: check settings (ringtone, vibration, ....) see also createChannel for higher android versions
                b.setDefaults(Notification.DEFAULT_ALL);
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

            notificationManager.notify(messageInfo.group, messageInfo.groupId, notification);
        }

        Log.d(TAG, "Messaging notify called.");

    }

    @TargetApi(26)
    public static void createChannel(String channelId, Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel nc = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_DEFAULT);
        if (nc == null) {
            //TODO: check settings (ringtone, vibration, ....)
            /*
            nc.setDescription("Non alarm events");
            nc.enableLights(false);
            nc.enableVibration(false);
            nc.setBypassDnd(false);
            */
            nm.createNotificationChannel(nc);
            Log.d(TAG, "notification channel created.");

        }
    }

    private static class Msg {
        long when;
        String topic;
        byte[] msg;
    }

    public final static String FCM_ON_DELETE = "FCM_ON_DELETE";

    private final static String TAG = MessagingService.class.getSimpleName();
}
