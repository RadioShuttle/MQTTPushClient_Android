/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.app.Application;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.dash.ImageResource;
import de.radioshuttle.mqttpushclient.dash.ImportFiles;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.DeleteToken;
import de.radioshuttle.utils.FirebaseTokens;

public class AccountViewModel extends ViewModel {

    public MutableLiveData<ArrayList<PushAccount>> accountList;
    public MutableLiveData<Request> request;

    public boolean initialized;
    private HashMap<String, Request> currentRequests;
    private Application app;

    public AccountViewModel() {
        accountList = new MutableLiveData<>();
        request = new MutableLiveData<>();
        initialized = false;
        app = null;

    }

    public void addNotificationUpdateListener(Application app) {
        if (this.app == null) {
            this.app = app;
            IntentFilter intentFilter = new IntentFilter(MqttMessage.MSG_CNT_INTENT);
            intentFilter.addAction(FirebaseTokens.TOKEN_UPDATED);
            LocalBroadcastManager.getInstance(app).registerReceiver(broadcastReceiver, intentFilter);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (app != null && broadcastReceiver != null) {
            LocalBroadcastManager.getInstance(app).unregisterReceiver(broadcastReceiver);
        }
        List<PushAccount> accounts = accountList.getValue();
        if (accounts != null) {
            for(PushAccount a : accounts) {
                if (a.executor != null) {
                    a.executor.shutdown();
                }
            }
        }
        Log.d(TAG, "app == null? " + (app == null));
        if (app != null) {
            ImageResource.removeUnreferencedImageResources(app, accounts);
        }
    }

    public void init(String accountsJson) throws JSONException {
        if (!initialized) {
            setAccountsJSON(accountsJson, true);
            initialized = true;
            /*
            if (accountList.getValue() == null || accountList.getValue().isEmpty()) {
                ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
                pushAccounts.add(PushAccount.createAccountFormJSON(
                        new JSONObject(
                                "{\"pushserver\":\"192.168.178.80\",\"pushserverID\":\"stonehenge.helios.de:2033\",\"uri\":\"tcp:\\/\\/mqtt.arduino-hannover.de:1883\",\"user\":\"XXXXXXXX\",\"id\":1,\"password\":\"XXXXXXXX\",\"clientID\":\"MQTTPushClient\",\"topics\":[\"XXXXXXXX\\/HELIOS\"]}"
                        ))
                );
                accountList.setValue(pushAccounts);
            }
            */
        }
    }

    public void setAccountsJSON(String accountsJson, boolean init) throws JSONException {
        ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
        if (accountsJson != null) {
            JSONArray jarray = new JSONArray(accountsJson);
            for(int i = 0 ; i < jarray.length(); i++) {
                JSONObject b = jarray.getJSONObject(i);
                pushAccounts.add(PushAccount.createAccountFormJSON(b));
            }
        }
        if (init) {
            for(PushAccount b : pushAccounts) {
                b.status = -1;
            }
        }
        accountList.setValue(pushAccounts);

    }

    public String getAccountsJSON() throws JSONException {
        JSONArray accountList = new JSONArray();
        ArrayList<PushAccount> list = this.accountList.getValue();
        for(PushAccount b : list) {
            accountList.put(b.getJSONObject());
        }
        return accountList.toString();
    }

    public PushAccount removeBorker(String key) {
        PushAccount ret = null;
        ArrayList<PushAccount> pushAccounts = accountList.getValue();
        if (key != null && pushAccounts != null && pushAccounts.size() > 0) {
            for(Iterator<PushAccount> it = pushAccounts.iterator(); it.hasNext();) {
                PushAccount b = it.next();
                if (key.equals(b.getKey())) {
                    ret = b;
                    it.remove();
                    accountList.setValue(pushAccounts);
                    break;
                }
            }
        }
        return ret;
    }

    public void checkAccounts(Context context) {
        ArrayList<PushAccount> pushAccounts = accountList.getValue();

        if (currentRequests == null) {
            currentRequests = new HashMap<>();
        } else {
            /* refresh -> cancel current tasks  */
            for(Map.Entry<String, Request> b : currentRequests.entrySet()) {
                b.getValue().cancel();
            }
            currentRequests.clear();
        }

        if (pushAccounts != null) {
            for(int i = 0; i < pushAccounts.size(); i++) {
                Request request = new Request(context, pushAccounts.get(i), this.request);
                request.setSync(true);
                currentRequests.put(pushAccounts.get(i).getKey(), request);
                if (pushAccounts.get(i).executor != null) {
                    request.executeOnExecutor(pushAccounts.get(i).executor, (Void[]) null);
                } else {
                    request.execute();
                }
            }
        }
    }

    /* add or uodate account */
    public void saveAccount(Context context, PushAccount pushAccount, boolean sync) {

        if (currentRequests == null) {
            currentRequests = new HashMap<>();
        } else {
        }
        Request request = new Request(context, pushAccount, this.request);
        request.setSync(sync);
        currentRequests.put(pushAccount.getKey(), request);

        if (pushAccount.executor != null) {
            request.executeOnExecutor(pushAccount.executor, (Void[]) null);
        } else {
            request.execute();
        }
    }

    public void deleteAccount(Context context, boolean deleteToken, PushAccount pushAccount) {
        if (currentRequests == null) {
            currentRequests = new HashMap<>();
        }

        /* cancel current task for account */
        Request task = currentRequests.remove(pushAccount.getKey());
        if (task != null) {
            task.cancel();
        }
        DeleteToken deleteRequest = new DeleteToken(context, deleteToken, pushAccount, this.request);

        currentRequests.put(pushAccount.getKey(), deleteRequest);

        if (pushAccount.executor != null) {
            deleteRequest.executeOnExecutor(pushAccount.executor, (Void[]) null);
        } else {
            deleteRequest.execute();
        }
    }

    public boolean isRequestActive() {
        boolean active = false;
        if (currentRequests != null && currentRequests.size() > 0) {
            for(Iterator<Map.Entry<String, Request>> it = currentRequests.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Request> e = it.next();
                if (e.getValue() != null && !e.getValue().hasCompleted()) {
                    active = true;
                } else {
                    it.remove();
                }
            }
        }
        return active;
    }

    public boolean isDeleteRequestActive() {
        boolean active = false;
        if (isRequestActive()) {
            for(Map.Entry<String, Request> b : currentRequests.entrySet()) {
                if (b.getValue() instanceof DeleteToken) {
                    active = true;
                    break;
                }
            }
        }
        return active;
    }

    public void confirmResultDelivered(Request request) {
        currentRequests.remove(request.getAccount().getKey());
    }

    public boolean isCurrentRequest(Request request) {
        return
            request.getAccount().status == 0 &&
                    currentRequests != null &&
                    currentRequests.get(request.getAccount().getKey()) == request;
    }

    public boolean hasMultiplePushServers() {
        boolean multi = false;
        ArrayList<PushAccount> pushAccounts = accountList.getValue();
        if (pushAccounts != null) {
            HashSet<String> pushServer = new HashSet<>();
            for(PushAccount br : pushAccounts) {
                if (br.pushserver != null && br.pushserver.trim().length() > 0) {
                    pushServer.add(br.pushserver.trim());
                }
            }
            multi = pushServer.size() > 1;
        }
        return multi;
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(MqttMessage.MSG_CNT_INTENT)) {
                String arg = intent.getStringExtra(MqttMessage.ARG_MQTT_ACCOUNT);
                String argID = intent.getStringExtra(MqttMessage.ARG_PUSHSERVER_ADDR);
                Log.d(TAG, "received upd intent: " + arg + " / " + argID);
                ArrayList<PushAccount> pushAccounts = accountList.getValue();
                if (pushAccounts != null) {
                    for(PushAccount pushAccount : pushAccounts) {
                        Log.d(TAG, "check: " + pushAccount.getMqttAccountName() + " " + pushAccount.pushserver);
                        if (arg != null && argID != null && pushAccount != null && pushAccount.getMqttAccountName().equals(arg) &&
                                pushAccount.pushserver != null && pushAccount.pushserver.equals(argID)) {
                            accountList.setValue(pushAccounts); // notify about update change
                            break;
                        }
                    }
                }
            } else if (intent.getAction().equals(FirebaseTokens.TOKEN_UPDATED)) {
                String arg = intent.getStringExtra(FirebaseTokens.TOKEN_UPDATE_ACCOUNT);
                Log.d(TAG, "received token updated intent: " + arg);
                ArrayList<PushAccount> pushAccounts = accountList.getValue();
                if (pushAccounts != null) {
                    for(PushAccount pushAccount : pushAccounts) {
                        if (arg != null && arg.equals(pushAccount.getKey())) {
                            accountList.setValue(pushAccounts); // notify about update change
                            break;
                        }
                    }
                }
            }
        }
    };

    private final static String TAG = AccountViewModel.class.getSimpleName();
}
