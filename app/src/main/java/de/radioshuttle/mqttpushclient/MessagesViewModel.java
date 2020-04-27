/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;

import androidx.arch.core.util.Function;
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
import android.content.SharedPreferences;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.fcm.Notifications;
import de.radioshuttle.utils.JavaScript;
import de.radioshuttle.utils.ModifyPagedList;
import de.radioshuttle.utils.Utils;

public class MessagesViewModel extends AndroidViewModel {
    public LiveData<PagedList<MqttMessage>> messagesPagedList;
    public PushAccount pushAccount;
    public HashSet<Integer> newItems;

    protected volatile HashMap<String, JSContext> currJSContextMap;
    protected HashMap<String, JSContext> prevJSContextMap;
    protected volatile boolean jsDisabled;
    protected volatile String jsErrorTxt;

    public static class JSContext {
        public JavaScript.Context context;
        public HashMap<String, Object> viewProps;
    }

    public MessagesViewModel(final PushAccount account, final Application app) {
        super(app);
        pushAccount = account;

        MqttMessageDao dao = AppDatabase.getInstance(app).mqttMessageDao();
        newItems = new HashSet<>();

        jsDisabled = false;
        jsErrorTxt = null;
        currJSContextMap = null;
        prevJSContextMap = null;
        initJavaScript();

        /* function executed for every page */
        Function<List<MqttMessage>, List<MqttMessage>> f = new Function<List<MqttMessage>, List<MqttMessage>>() {
            @Override
            public List<MqttMessage> apply(List<MqttMessage> input) {
                // long start = System.currentTimeMillis();

                HashMap<String, JSContext> jsContextMap = currJSContextMap;

                String pl;
                if (jsDisabled) {
                    /* javascript was disabled in a previous run, add error msg to all entries */
                    for(int i = 0; i < input.size(); i++) {
                        pl = jsErrorTxt + "\n" + new String(input.get(i).getPayload(), Utils.UTF_8);
                        input.get(i).setPayload((pl.getBytes(Utils.UTF_8)));
                    }
                } else if (jsContextMap != null && !jsContextMap.isEmpty()) {
                    /* */
                    ModifyPagedList jsModify = new ModifyPagedList(getApplication(), input, jsContextMap, app.getString(R.string.filterscript_err));
                    Future future = Utils.executor.submit(jsModify);
                    int itemsProcessed = 0;

                    try {
                        future.get(JavaScript.TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        jsDisabled = true; // disable java script
                        jsModify.stop();
                        itemsProcessed = jsModify.itemsProcessed();
                        if (e instanceof TimeoutException) {
                            jsErrorTxt = app.getString(R.string.filterscript_err) + " " + app.getString(R.string.filterscript_err_timeout);
                        } else {
                            jsErrorTxt = app.getString(R.string.filterscript_err) + " " + e.getMessage();
                        }
                        for(int i = itemsProcessed; i < input.size(); i++) {
                            pl = jsErrorTxt + "\n" + new String(input.get(i).getPayload(), Utils.UTF_8);
                            input.get(i).setPayload((pl.getBytes(Utils.UTF_8)));
                        }
                    }
                }
                // long exeTime = (System.currentTimeMillis() - start);
                // Log.d(TAG, "execution time: " + exeTime + "ms " + input.size());

                return input;
            }
        };

        DataSource.Factory<Integer, MqttMessage> ds = dao.getReceivedMessages(account.pushserverID, account.getMqttAccountName());
        ds = ds.mapByPage(f);

        messagesPagedList = new LivePagedListBuilder<>(
                ds, 40).build();

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


    protected void initJavaScript() {
        // long start = System.currentTimeMillis();
        if (prevJSContextMap != null) {
            /* release JS resrouces */
            Iterator<JSContext> it = prevJSContextMap.values().iterator();
            while(it.hasNext()) {
                it.next().context.close();
            }
        }
        prevJSContextMap = currJSContextMap;

        SharedPreferences settings = getApplication().getSharedPreferences(AccountListActivity.PREFS_NAME, Activity.MODE_PRIVATE);
        String accountsJson = settings.getString(AccountListActivity.ACCOUNTS, null);

        JavaScript interpreter = JavaScript.getInstance(getApplication());
        HashMap<String, JSContext> jsContextMap = new HashMap<>();
        try {
            if (accountsJson != null) {
                JSONArray jarray = new JSONArray(accountsJson);
                PushAccount found = null;
                for (int i = 0; i < jarray.length(); i++) {
                    JSONObject b = jarray.getJSONObject(i);
                    PushAccount acc = PushAccount.createAccountFormJSON(b);
                    if (pushAccount.getKey().equals(acc.getKey())) {
                        found = acc;
                        break;
                    }
                }
                if (found != null) {
                    final String mqttServer = new URI(pushAccount.uri).getAuthority();
                    for(PushAccount.Topic t : found.topicJavaScript) {
                        if (!Utils.isEmpty(t.jsSrc)) {
                            /* init javascript function for every topic which has java script code */
                            try {
                                JavaScript.Context context = interpreter.initFormatter(t.jsSrc, pushAccount.user, mqttServer, pushAccount.pushserver);
                                JSContext jsc = new JSContext();
                                jsc.context = context;
                                jsc.viewProps =  new HashMap<String, Object>();
                                interpreter.initMessageViewProperties(context, jsc.viewProps);
                                jsContextMap.put(t.name, jsc);
                            } catch(Exception e) {
                                /* init failed */
                                ModifyPagedList.JSInitError je = new ModifyPagedList.JSInitError();
                                je.errorText = "" + e.getMessage();
                                JSContext jsc = new JSContext();
                                jsc.context = je;
                                jsContextMap.put(t.name, jsc);
                            }
                        }
                    }
                }

            }
            // Log.d(TAG, "init javasc: " + (System.currentTimeMillis() - start) + "ms");

        } catch(Exception e) {
            Log.e(TAG, "Error loading accounts (javascript code): " + e.getMessage(), e );
        } finally {
            currJSContextMap = jsContextMap;
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
                        long[] lastSyncDate = Notifications.getLastSyncDate(getApplication(), pushAccount.pushserver, pushAccount.getMqttAccountName());
                        if (lastSyncDate[0] < since) {
                            msgs = dao.loadReceivedMessagesBefore(psid, accountID, since);
                            if (msgs != null && msgs.size() > 0) {
                                msg = msgs.get(0);
                                if (msg.getWhen() > lastSyncDate[0] || (msg.getWhen() == lastSyncDate[0] && msg.getSeqno() > lastSyncDate[1])) {
                                    Notifications.setLastSyncDate(getApplication(), pushAccount.pushserver, pushAccount.getMqttAccountName(),
                                            msg.getWhen(), msg.getSeqno());
                                }
                            }
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
            t.executeOnExecutor(Utils.executor, (Object[]) null); //
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (prevJSContextMap != null) {
            /* release JS resrouces */
            Iterator<JSContext> it = prevJSContextMap.values().iterator();
            while(it.hasNext()) {
                it.next().context.close();
            }
        }
        if (currJSContextMap != null) {
            /* release JS resrouces */
            Iterator<JSContext> it = currJSContextMap.values().iterator();
            while(it.hasNext()) {
                it.next().context.close();
            }
        }

        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(broadcastReceiver);
    }

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(PushAccount pushAccount, Application app) {
            this.app = app;
            this.pushAccount = pushAccount;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new MessagesViewModel(pushAccount, app);
        }

        PushAccount pushAccount;
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
