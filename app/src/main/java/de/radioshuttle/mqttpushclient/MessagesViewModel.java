/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.arch.paging.LivePagedListBuilder;
import android.arch.paging.PagedList;
import android.support.annotation.NonNull;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.db.MqttMessageDao;

public class MessagesViewModel extends ViewModel {
    public LiveData<PagedList<MqttMessage>> messagesPagedList;
    public PushAccount pushAccount;

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(String pushServer, String account, Application app) {
            this.pushServer = pushServer;
            this.account = account;
            this.app = app;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            MessagesViewModel mv = new MessagesViewModel();
            final MqttMessageDao dao = AppDatabase.getInstance(app).mqttMessageDao();

            mv.messagesPagedList = new LivePagedListBuilder<>(
                    dao.getReceivedMessages(pushServer, account), 20).build(); //TODO: page size

            return (T) mv;
        }

        String pushServer;
        String account;
        Application app;
    }

}
