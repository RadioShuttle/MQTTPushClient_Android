/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.annotation.SuppressLint;
import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import java.util.Date;
import java.util.HashSet;
import java.util.List;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.fcm.Notifications;

public class MessagesViewModel extends AndroidViewModel {
    public LiveData<PagedList<MqttMessage>> messagesPagedList;
    public PushAccount pushAccount;
    public HashSet<Integer> newItems;

    public MessagesViewModel(String pushServer, String account, Application app) {
        super(app);
        MqttMessageDao dao = AppDatabase.getInstance(app).mqttMessageDao();
        newItems = new HashSet<>();
        messagesPagedList = new LivePagedListBuilder<>(
                dao.getReceivedMessages(pushServer, account), 20).build(); //TODO: page size
        IntentFilter intentFilter = new IntentFilter(MqttMessage.UPDATE_INTENT);
        LocalBroadcastManager.getInstance(app).registerReceiver(broadcastReceiver, intentFilter);

    }

    public void refresh() {
        if (messagesPagedList != null && messagesPagedList.getValue() != null) {
            DataSource ds = messagesPagedList.getValue().getDataSource();
            if (ds != null) {
                ds.invalidate();
            }
        }
    }

    public void deleteMessages(final Long since) {
        @SuppressLint("StaticFieldLeak")
        AsyncTask t = new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                AppDatabase db = AppDatabase.getInstance(getApplication());
                MqttMessageDao dao = db.mqttMessageDao();
                long psid = dao.getCode(pushAccount.pushserverID);
                long accountID = dao.getCode(pushAccount.getMqttAccountName());

                List<MqttMessage> msgs = dao.loadReceivedMessages(psid, accountID);
                if (msgs != null && msgs.size() > 0) {
                    MqttMessage msg = msgs.get(0);
                    if (since == null || msg.getWhen() < since) {
                        Notifications.setLastSyncDate(getApplication(), pushAccount.pushserver, pushAccount.getMqttAccountName(),
                                msg.getWhen(), msg.getSeqno());
                        // Log.d(TAG, "last sync date (delete): " + new Date(msg.getWhen()) + " " + (msg.getWhen() / 1000L) + " " + msg.getSeqno());
                    } else {
                        // msg.getWhen() > 0
                        // make sure sync date is not lower than now - 1 day (since)
                        long[] lastSyncDate = Notifications.getLastSyncDate(getApplication(), pushAccount.pushserver, pushAccount.getMqttAccountName());
                        if (lastSyncDate[0] < since) {
                            Notifications.setLastSyncDate(getApplication(), pushAccount.pushserver, pushAccount.getMqttAccountName(),
                                    since, 0);
                            // Log.d(TAG, "last sync date (delete 1 day): " + new Date(since) + " " + (since / 1000L) + " " + 0);
                        }
                    }
                }

                if (since != null) {
                    dao.deleteMessagesForAccountBefore(psid, accountID, since);
                } else {
                    dao.deleteMessagesForAccount(psid, accountID);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                super.onPostExecute(o);
                refresh();
            }
        };
        if (pushAccount != null) {
            t.execute((Object[]) null);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(broadcastReceiver);
    }

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(String pushServer, String account, Application app) {
            this.pushServer = pushServer;
            this.account = account;
            this.app = app;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new MessagesViewModel(pushServer, account, app);
        }

        String pushServer;
        String account;
        Application app;
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String arg = intent.getStringExtra(MqttMessage.ARG_MQTT_ACCOUNT);
            String argID = intent.getStringExtra(MqttMessage.ARG_PUSHSERVER_ADDR);
            Log.d(TAG, "received intent: " + arg + " / " + argID);
            if (arg != null && argID != null && pushAccount != null && pushAccount.getMqttAccountName().equals(arg) &&
                    pushAccount.pushserver != null && pushAccount.pushserver.equals(argID)) {
                Log.d(TAG, "received intent: " + intent.getIntegerArrayListExtra(MqttMessage.ARG_IDS).size());
                newItems.addAll(intent.getIntegerArrayListExtra(MqttMessage.ARG_IDS));
                refresh();
            }
        }
    };

    private final static String TAG = MessagesViewModel.class.getSimpleName();
}
