/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.fcm;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.messaging.FirebaseMessagingService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Map;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.Code;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.AccountListActivity;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

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

        setAlertOnlyOnce(b, getApplicationContext());

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

        String mqttAccountName = data.get("account");
        if (Utils.isEmpty(mqttAccountName))
            return;

        String pushServerID = data.get("pushserverid");
        Log.d(TAG, "mqtt account: " + mqttAccountName + " " + pushServerID);

        if (Utils.isEmpty(pushServerID))
            return;

        String pushServerLocalAddr = null;
        /* read accpimt */
        SharedPreferences settings = getSharedPreferences(AccountListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String accountsJSON = settings.getString(AccountListActivity.ACCOUNTS, null);
        int mc = 0;
        if (accountsJSON != null) {
            try {
                JSONArray jarray = new JSONArray(accountsJSON);
                for (int i = 0; i < jarray.length(); i++) {
                    JSONObject b = jarray.getJSONObject(i);
                    PushAccount pushAccount = PushAccount.createAccountFormJSON(b);
                    if (mqttAccountName.equals(pushAccount.getMqttAccountName())) {
                        mc++;
                        if (pushAccount.pushserverID.equals(pushServerID)){
                            pushServerLocalAddr = pushAccount.pushserver;
                        }
                    }
                }
            } catch(Exception e) {
                Log.e(TAG, "Error parsing JSON: ", e);
            }
        }
        boolean multiMqttAccounts = mc > 1;

        if (pushServerLocalAddr == null)
            return;

        // Log.d(TAG, "pushServerLocalAddr: " + pushServerLocalAddr);


        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // nm.deleteNotificationChannel(mqttAccountName); //TODO: remove
            // ‚nm.deleteNotificationChannel(mqttAccountName+".a"); //TODO: remove
            if (nm.getNotificationChannel(pushServerLocalAddr + ":" + mqttAccountName) == null) {
                createChannel(pushServerLocalAddr, mqttAccountName, getApplicationContext());
            }

        }

        String msg = data.get("messages");
        Msg latestAlarmMsg = null; // prio high
        Msg latestMsg = null; // prio normal
        int cntAlarm = 0;
        int cntNormal = 0;
        int cnt = 0;
        try {
            JSONArray msgsArray = new JSONArray(msg);
            AppDatabase db = null;
            Long psCode = null;
            Long mqttAccountCode = null;
            if (msgsArray.length() > 0) {
                db = AppDatabase.getInstance(getApplicationContext());
                psCode = db.mqttMessageDao().getCode(pushServerID);
                if (psCode == null) {
                    Code c = new Code();
                    c.setName(pushServerID);
                    try {
                        psCode = db.mqttMessageDao().insertCode(c);
                    } catch(SQLiteConstraintException e) {
                        // rare case: sync messages has just inserted this pushServerID
                        Log.d(TAG,"insert pushserver id into code table: ", e);
                        psCode = db.mqttMessageDao().getCode(pushServerID); // reread
                    }
                    // Log.d(TAG, " (before null) code: " + psCode);
                }
                mqttAccountCode = db.mqttMessageDao().getCode(mqttAccountName);
                if (mqttAccountCode == null) {
                    Code c = new Code();
                    c.setName(mqttAccountName);
                    try {
                        mqttAccountCode = db.mqttMessageDao().insertCode(c);
                    } catch(SQLiteConstraintException e) {
                        // rare case: sync messages has just inserted this accountname
                        Log.d(TAG,"insert accountname into code table: ", e);
                        mqttAccountCode = db.mqttMessageDao().getCode(mqttAccountName);
                    }
                    // Log.d(TAG, " (before null) mqttAccountCode: " + mqttAccountCode);
                }
            }
            ArrayList<Integer> ids = new ArrayList<>();
            for(int i = 0; i < msgsArray.length(); i++) {
                JSONObject topic = msgsArray.getJSONObject(i);
                Iterator<String> it = topic.keys();
                long d;
                String base64;
                int seqNo = 0;
                while(it.hasNext()) {
                    String t = it.next();
                    JSONObject dataPerTopic = topic.getJSONObject(t);
                    int prio = dataPerTopic.getInt("prio");
                    JSONArray msgsArrayPerTopic = dataPerTopic.getJSONArray("mdata");

                    // JSONArray msgsArrayPerTopic = topic.getJSONArray(t);
                    for(int j = 0; j < msgsArrayPerTopic.length(); j++) {
                        JSONArray entryArray = msgsArrayPerTopic.getJSONArray(j);
                        d = entryArray.getLong(0) * 1000L;
                        base64 = entryArray.getString(1);
                        if (entryArray.length() >= 3) {
                            seqNo = entryArray.getInt(2);
                        } else {
                            seqNo = 0;
                        }
                        Msg m = new Msg();
                        m.when = d;
                        m.msg = Base64.decode(base64, Base64.DEFAULT);
                        m.topic = t;
                        m.seqNo = seqNo;
                        m.isSystemMsg = false;
                        if (entryArray.length() >= 4) {
                            if (!entryArray.isNull(3)) {
                                m.isSystemMsg = true;
                                /*
                                try {
                                    JSONObject sysObjec = entryArray.getJSONObject(3);
                                    Log.d(TAG, "sys cmd: " + sysObjec.getInt("id"));
                                } catch(JSONException e) {
                                    Log.d(TAG, "Error parsing sysObject: " + e.getMessage() );
                                }
                                */
                            }
                        }

                        //TODO: consider removing Msg class and replace it with MqttMessage below
                        MqttMessage mqttMessage = new MqttMessage();
                        mqttMessage.setPushServerID(psCode.intValue());
                        mqttMessage.setMqttAccountID(mqttAccountCode.intValue());
                        mqttMessage.setWhen(m.when);
                        mqttMessage.setTopic(m.topic);
                        mqttMessage.setSeqno(m.seqNo);
                        try {
                            mqttMessage.setMsg(new String(m.msg));
                        } catch(Exception e) {
                            // decoding error or payload not utf-8
                            mqttMessage.setMsg(base64); //TODO: error should be rare but consider using hex
                        }
                        try {
                            Long k = db.mqttMessageDao().insertMqttMessage(mqttMessage);
                            if (k != null && k >= 0) {
                                ids.add(k.intValue());
                            }
                        } catch(SQLiteConstraintException e) {
                            /* this error may occur, if the message has already been added by sync operation */
                            Log.d(TAG, "constraint error: " + e.getMessage());
                            continue;
                        }
                        if (prio == PushAccount.Topic.NOTIFICATION_MEDIUM && !m.isSystemMsg) {
                            if (latestMsg == null) {
                                latestMsg = m;
                            } else if (latestMsg.when < (m.when)) {
                                latestMsg = m;
                            }
                            cntNormal++;
                        } else if (prio == PushAccount.Topic.NOTIFICATION_HIGH || m.isSystemMsg) {
                            if (latestAlarmMsg == null) {
                                latestAlarmMsg = m;
                            } else if (latestAlarmMsg.when < (m.when)) {
                                latestAlarmMsg = m;
                            }
                            cntAlarm++;
                        } else {
                            cnt++;
                        }

                        Log.d(TAG, t + ": " + prio + " " + m.when + " " + m.seqNo + " " + new String(m.msg));
                    }
                }
            }

            /* delete all messages older than 30 days */
            GregorianCalendar cal = new GregorianCalendar();
            cal.add(Calendar.DAY_OF_MONTH, -MqttMessage.MESSAGE_EXPIRE_DAYS);
            long before = cal.getTimeInMillis();
            db.mqttMessageDao().deleteMessagesBefore(before);

            /*
            long l = System.currentTimeMillis() - (1000L * 60L * 60L * 24 * 365);
            for(int i = 200; i > 0; i--) {
                MqttMessage m = new MqttMessage();
                m.setPushServerID(psCode.intValue());
                m.setMqttAccountID(mqttAccountCode.intValue());
                l += 1000L;
                m.setWhen(l);
                m.setTopic("autogerneated test data");
                m.setMsg("Test text: " + i);
                db.mqttMessageDao().insertMqttMessage(m);
            }
            */


            if (cntAlarm > 0 && latestAlarmMsg != null) {
                showNotification(pushServerLocalAddr, mqttAccountName, pushServerID, multiMqttAccounts, latestAlarmMsg, PushAccount.Topic.NOTIFICATION_HIGH, cntAlarm);
            }
            if (cntNormal > 0 && latestMsg != null) {
                showNotification(pushServerLocalAddr, mqttAccountName, pushServerID, multiMqttAccounts, latestMsg, PushAccount.Topic.NOTIFICATION_MEDIUM, cntNormal);
            }

            int total = cntAlarm + cntNormal + cnt;
            if (total > 0) {
                Notifications.addToNewMessageCounter(getApplicationContext(), pushServerLocalAddr, mqttAccountName, total);
            }

            /* inform about database changes, if app is running it can update its views */
            if (ids.size() > 0) {
                Intent intent = new Intent(MqttMessage.UPDATE_INTENT);
                intent.putExtra(MqttMessage.ARG_PUSHSERVER_ADDR, pushServerLocalAddr);
                intent.putExtra(MqttMessage.ARG_MQTT_ACCOUNT, mqttAccountName);
                intent.putExtra(MqttMessage.ARG_IDS, ids);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
            }

            if (cntAlarm > 0 || cntNormal > 0) {
                Log.d(TAG, "Messaging notify called.");
            }

        } catch(Exception e) {
            //TODO: error handling for db errors
            Log.d(TAG, "error parsing messages", e);
        }

    }

    protected void showNotification(String pushServerAddr, String mqttAccount, String pushServerID, boolean multiMqttAccounts , Msg m, int prio, int cnt) {
        String account = pushServerAddr + ":" + mqttAccount;

        String group = account;
        if (prio == PushAccount.Topic.NOTIFICATION_HIGH) {
            group += ".a";
        }

        Notifications.MessageInfo messageInfo = Notifications.getMessageInfo(this, group);
        if (messageInfo.groupId == 0) {
            messageInfo.groupId = messageInfo.noOfGroups + 1;
        }
        messageInfo.messageId += cnt;
        Notifications.setMessageInfo(this, messageInfo);

        if (m == null) {
            return;
        }

        Intent intent = new Intent(this, AccountListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(ARG_MQTT_ACCOUNT, mqttAccount);
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
            b = new NotificationCompat.Builder(this, group);
        } else {
            b = new NotificationCompat.Builder(this);
        }

        String accountDisplayName = (multiMqttAccounts ? pushServerAddr + ": " + mqttAccount : mqttAccount);
        if (prio == PushAccount.Topic.NOTIFICATION_HIGH)
            b.setContentTitle(getString(R.string.notificaion_alarm) + " " + accountDisplayName);
        else
            b.setContentTitle(accountDisplayName);
        if (m.isSystemMsg) {
            b.setContentText(new String(m.msg));
        } else {
            b.setContentText(m.topic + ": " + new String(m.msg));
        }

        b.setWhen(m.when);
        if (messageInfo.messageId > 1) {
            String more = String.format("+%d", (messageInfo.messageId - 1));
            b.setSubText(more);
        }
        if (Build.VERSION.SDK_INT >= 25) {
            b.setGroup(account);
        }

        b.setAutoCancel(false);
        b.setContentIntent(pendingIntent);
        b.setDeleteIntent(delPendingIntent);

        if (Build.VERSION.SDK_INT < 26) {
            if (prio == PushAccount.Topic.NOTIFICATION_HIGH) {
                b.setPriority(Notification.PRIORITY_HIGH);
                b.setDefaults(Notification.DEFAULT_ALL);
            } else { // PushAccount.Topic.NOTIFICATION_LOW
                b.setPriority(Notification.PRIORITY_LOW);
                b.setDefaults(0);
            }
        }

        if (Build.VERSION.SDK_INT >= 21) {
            b.setSmallIcon(R.drawable.ic_notification_devices_other_vec);
        } else {
            // vector drawables not work here for versions pror lolipop
            b.setSmallIcon(R.drawable.ic_notification_devices_other_img);
        }

        setAlertOnlyOnce(b, getApplicationContext());

        Notification notification = b.build();

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(messageInfo.group, messageInfo.groupId, notification);

    }

    protected static void setAlertOnlyOnce(NotificationCompat.Builder b, Context context) {
        long lastAlert = Notifications.getLastNofificationProcessed(context);
        long now = System.currentTimeMillis();
        // Log.d(TAG, "last alert: " + lastAlert);

        b.setOnlyAlertOnce(lastAlert != 0 && now - lastAlert < 10000L);
        Notifications.setLastNofificationProcessed(context, now);
    }

    @TargetApi(26)
    public static void createChannel(String pushServerAddr, String mqttAccount, Context context) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String account = pushServerAddr + ":" + mqttAccount;

        String channelID = account + ".a";
        String groupID = "g." + account;

        NotificationChannel ch = nm.getNotificationChannel(channelID);
        if (ch == null) {
            String groupDisplayName = pushServerAddr + ": " + mqttAccount;
            nm.createNotificationChannelGroup(new NotificationChannelGroup(groupID, groupDisplayName));
            //TODO: localize user visible channel names
            NotificationChannel nc = new NotificationChannel(channelID, context.getString(R.string.notificaion_channel_alarm), NotificationManager.IMPORTANCE_DEFAULT);
            nc.setGroup(groupID);
            nm.createNotificationChannel(nc);
        }

        channelID = account;
        ch = nm.getNotificationChannel(channelID);
        if (ch == null) {
            NotificationChannel nc = new NotificationChannel(channelID, context.getString(R.string.notificaion_channel_reg), NotificationManager.IMPORTANCE_LOW);
            nc.setGroup(groupID);
            nm.createNotificationChannel(nc);
        }
        Log.d(TAG, "notification channel created.");
    }

    private static class Msg {
        long when;
        String topic;
        byte[] msg;
        int seqNo;
        boolean isSystemMsg;
    }

    public final static String FCM_ON_DELETE = "FCM_ON_DELETE";

    private final static String TAG = MessagingService.class.getSimpleName();
}
