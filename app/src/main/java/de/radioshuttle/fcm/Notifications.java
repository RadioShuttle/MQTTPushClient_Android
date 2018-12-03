/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.fcm;

import android.content.BroadcastReceiver;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.Utils;


public class Notifications extends BroadcastReceiver {

    public static MessageInfo getMessageInfo(Context context, String group) {

        MessageInfo info = new MessageInfo();
        info.group = group;
        info.groupId = 0;
        info.messageId = 0;
        info.noOfGroups = 0;

        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        String messageInfoJson = settings.getString(MESSAGE_INFO, null);

        JSONObject messageInfo;

        if (!Utils.isEmpty(messageInfoJson)) {
            // Log.d(TAG, messageInfoJson);
            try {
                messageInfo = new JSONObject(messageInfoJson);
                info.noOfGroups = messageInfo.length();
                if (messageInfo.has(group)) {
                    JSONObject data = messageInfo.getJSONObject(group);
                    if (data.has(GROUP_ID)) { // should always be the case
                        info.groupId = data.getInt(GROUP_ID);
                    }
                    if (data.has(MESSAGE_ID)) {
                        info.messageId = data.getInt(MESSAGE_ID);
                    }
                }
            } catch (JSONException e) {
                Log.d(TAG, "json error: ", e);
            }
        }

        return info;
    };

    // returns message info of first found alarm channel (channel name ends with ".a") in preferences (e.g. to display non mqtt related info)
    // if no alarm channel found, use first found channel
    public static MessageInfo getMessageInfo(Context context) {
        SharedPreferences settings = context.getSharedPreferences(Notifications.PREFS_NAME, Activity.MODE_PRIVATE);
        String messageInfoJson = settings.getString(Notifications.MESSAGE_INFO, null);
        String channelName = null;
        if (messageInfoJson != null) {
            try {
                JSONObject gr = new JSONObject(messageInfoJson);
                Iterator<String> it = gr.keys();
                if (it.hasNext()) {
                    channelName = it.next();
                    Log.d(TAG, "channel name: " + channelName);
                    if (channelName != null && !channelName.endsWith(".a")) {
                        while (it.hasNext()) {
                            String tmpName = it.next();
                            if (tmpName != null && tmpName.endsWith(".a")) {
                                channelName = tmpName;
                                break;
                            }
                        }
                    }
                }

            } catch (JSONException e) {
                Log.d(TAG, "json error: ", e);
            }
        }
        if (channelName == null) {
            channelName = "?";
        }
        return getMessageInfo(context, channelName);
    }

    public static void setMessageInfo(Context context, MessageInfo mi) {
        if (mi != null) {
            SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

            String messageInfoJson = settings.getString(MESSAGE_INFO, null);
            JSONObject messageInfo;

            if (Utils.isEmpty(messageInfoJson)) {
                messageInfo = new JSONObject();
            } else {
                try {
                    messageInfo = new JSONObject(messageInfoJson);
                } catch (JSONException e) {
                    messageInfo = new JSONObject();
                }
            }

            if(mi.group != null) {
                try {
                    JSONObject data = new JSONObject();
                    data.put(MESSAGE_ID, mi.messageId);
                    data.put(GROUP_ID, mi.groupId);

                    messageInfo.put(mi.group, data);
                } catch(JSONException e) {
                    Log.d(TAG, "json error: ", e);
                }
            }

            /* save message info */
            SharedPreferences.Editor editor = settings.edit();
            // Log.d(TAG, messageInfo.toString());
            editor.putString(MESSAGE_INFO, messageInfo.toString());
            editor.commit();
        }
    }

    public static void cancelAll(Context context, String group) {
        if (group != null) {
            MessageInfo mi = getMessageInfo(context, group);
            if (mi.messageId > 0) {

                NotificationManagerCompat notificationManager =
                        NotificationManagerCompat.from(context);

                notificationManager.cancel(mi.group, mi.groupId);
                mi.messageId = 0;
                setMessageInfo(context, mi);

                Intent intent = new Intent(MqttMessage.DELETE_INTENT);
                if (group.endsWith(".a")) {
                    group = group.substring(0, group.length() - 2);
                }
                intent.putExtra(MqttMessage.ARG_CHANNELNAME, group);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

            }
        }
    }

    public static void cancelOnDeleteWarning(Context context) {

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);

        notificationManager.cancel(MessagingService.FCM_ON_DELETE, 0);
    }

    public static void addToNewMessageCounter(Context context, String pushServerAddr, String mqttAccount, int cnt) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String account = pushServerAddr + ":" + mqttAccount;
        int storedCounter = settings.getInt(MSG_CNT_PREFIX + account, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(MSG_CNT_PREFIX + account, storedCounter + cnt);
        editor.commit();
        Intent intent = new Intent(MqttMessage.MSG_CNT_INTENT);
        intent.putExtra(MqttMessage.ARG_PUSHSERVER_ADDR, pushServerAddr);
        intent.putExtra(MqttMessage.ARG_MQTT_ACCOUNT, mqttAccount);
        intent.putExtra(MqttMessage.ARG_CNT, storedCounter + cnt);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void setLastSyncDate(Context context, String pushServerAddr, String mqttAccount, long date, int seqNo) {
        if (pushServerAddr != null && mqttAccount != null) {
            String account = pushServerAddr + ":" + mqttAccount;
            SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();
            editor.putLong(SYNC_DATE_PREFIX + account, date);
            editor.putInt(SYNC_SEQ_NO_PREFIX + account, seqNo);
            editor.commit();
        }
    }

    public static void resetNewMessageCounter(Context context, String pushServerAddr, String mqttAccount) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String account = pushServerAddr + ":" + mqttAccount;
        int cnt = settings.getInt(MSG_CNT_PREFIX + account, -1);
        if (cnt > 0) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(MSG_CNT_PREFIX + account, 0);
            editor.commit();
            Intent intent = new Intent(MqttMessage.MSG_CNT_INTENT);
            intent.putExtra(MqttMessage.ARG_PUSHSERVER_ADDR, pushServerAddr);
            intent.putExtra(MqttMessage.ARG_MQTT_ACCOUNT, mqttAccount);
            intent.putExtra(MqttMessage.ARG_CNT, 0);
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
        }
    }

    public static void deleteMessageCounter(Context context, String pushServerAddr, String mqttAccount) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String account = pushServerAddr + ":" + mqttAccount;

        /* save message info */
        SharedPreferences.Editor editor = settings.edit();
        editor.remove(MSG_CNT_PREFIX + account);
        editor.remove(SYNC_DATE_PREFIX + account);
        editor.remove(SYNC_SEQ_NO_PREFIX + account);
        editor.commit();
    }

    public static int getNoOfNewMessages(Context context, String pushServerAddr, String mqttAccount) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String account = pushServerAddr + ":" + mqttAccount;
        return settings.getInt(MSG_CNT_PREFIX + account, 0);
    }

    public static long[] getLastSyncDate(Context context, String pushServerAddr, String mqttAccount) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String account = pushServerAddr + ":" + mqttAccount;
        long[] re = new long[] {
                settings.getLong(SYNC_DATE_PREFIX + account, 0L),
                (long) settings.getInt(SYNC_SEQ_NO_PREFIX + account, 0)
        };
        return re;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if(ACTION_CANCELLED.equals(action))
        {
            // Log.d(TAG, "notification cancelled: " + intent.getStringExtra(DELETE_GROUP));
            cancelAll(context, intent.getStringExtra(DELETE_GROUP));
        }
    }

    public static void setLastNofificationProcessed(Context context, long l) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        /* save message info */
        SharedPreferences.Editor editor = settings.edit();
        // Log.d(TAG, messageInfo.toString());
        editor.putLong(LAST_NOTIFICATION, l);
        editor.commit();
    }

    public static long getLastNofificationProcessed(Context context) {
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        return settings.getLong(LAST_NOTIFICATION, 0L);
    }

    public Notifications() {}

    public static class MessageInfo {
        public String group;
        public int groupId;
        public int messageId;
        public int noOfGroups;
    }

    public final static String ACTION_CANCELLED = "notification_cancelled";
    public final static String DELETE_GROUP = "DELETE_GROUP";

    public final static String PREFS_NAME = "messages";
    public final static String MESSAGE_INFO = "messageinfo";
    public final static String LAST_NOTIFICATION = "last_notification";
    private final static String MESSAGE_ID = "message_id";
    private final static String GROUP_ID = "group_id";

    private final static String MSG_CNT_PREFIX = "MSG_CNT_";
    private final static String SYNC_DATE_PREFIX = "MSG_RECEIVED_";
    private final static String SYNC_SEQ_NO_PREFIX = "MSG_SEQ_NO_";


    private final static String TAG = Notifications.class.getSimpleName();

}
