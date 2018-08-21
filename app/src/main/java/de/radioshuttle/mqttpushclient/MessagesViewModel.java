/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.paging.DataSource;
import android.arch.paging.LivePagedListBuilder;
import android.arch.paging.PagedList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.db.MqttMessageDao;

public class MessagesViewModel extends AndroidViewModel {
    public LiveData<PagedList<MqttMessage>> messagesPagedList;
    public PushAccount pushAccount;

    public MessagesViewModel(String pushServer, String account, Application app) {
        super(app);
        MqttMessageDao dao = AppDatabase.getInstance(app).mqttMessageDao();
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
            String arg = intent.getStringExtra(MqttMessage.ARG_ACCOUNT);
            if (arg != null && pushAccount != null && pushAccount.getMqttAccountName().equals(arg)) {
                refresh();
            }
        }
    };

}
