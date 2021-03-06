/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.Utils;

public class ActionsViewModel extends ViewModel {

    public ActionsViewModel() {
        initialized = false;
        actionsRequest = new MutableLiveData<>();
        requestCnt = 0;
        selectedActions = new HashSet<>();
    }

    public void init(String accountJson) throws JSONException {
        if (!initialized) {
            initialized = true;
            pushAccount = PushAccount.createAccountFormJSON(new JSONObject(accountJson));
            pushAccount.topics.clear(); // load topics from server
            executor = Utils.newSingleThreadPool();
        }
    }

    public void getActions(Context context, boolean sync, boolean checkHasTopics) {
        requestCnt++;
        currentRequest = new ActionsRequest(context, pushAccount, actionsRequest);
        currentRequest.setSync(true);
        if (executor != null) {
            currentRequest.executeOnExecutor(executor, (Void[]) null);
        } else {
            currentRequest.execute();
        }
    }

    public void deleteActions(Context context, List<String> actionNames) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.deleteActions(actionNames);
        currentRequest = request;
        if (executor != null) {
            currentRequest.executeOnExecutor(executor, (Void[]) null);
        } else {
            currentRequest.execute();
        }
    }

    public void addAction(Context context, ActionsViewModel.Action a) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.addAction(a);
        currentRequest = request;
        if (executor != null) {
            currentRequest.executeOnExecutor(executor, (Void[]) null);
        } else {
            currentRequest.execute();
        }
    }

    public void publish(Context context, ActionsViewModel.Action a) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.publish(a);
        currentRequest = request;
        if (executor != null) {
            currentRequest.executeOnExecutor(executor, (Void[]) null);
        } else {
            currentRequest.execute();
        }
    }

    public void updateAction(Context context, ActionsViewModel.Action a) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.updateAction(a);
        currentRequest = request;
        if (executor != null) {
            currentRequest.executeOnExecutor(executor, (Void[]) null);
        } else {
            currentRequest.execute();
        }
    }

    public boolean isRequestActive() {
        return requestCnt > 0;
    }

    public void confirmResultDelivered() {
        requestCnt = 0;
    }

    public boolean isCurrentRequest(Request request) {
        return currentRequest == request;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (executor != null) {
            executor.shutdown();
        }
    }

    public static class Action {
        public String name;
        public String topic;
        public String content;
        public String prevName;
        public boolean retain;
    }

    public static class ActionComparator implements Comparator<Action> {

        @Override
        public int compare(Action o1, Action o2) {
            String a1 = o1 == null ? "" : o1.name;
            String a2 = o2 == null ? "" : o2.name;

            String s1 = a1 == null ? "" : a1;
            String s2 = a2 == null ? "" : a2;
            return s1.compareToIgnoreCase(s2);
        }
    }


    public MutableLiveData<Request> actionsRequest;
    public HashSet<String> selectedActions;
    public PushAccount pushAccount;
    public boolean initialized;
    public String lastEnteredAction;
    private int requestCnt;
    private Request currentRequest;

    private ThreadPoolExecutor executor;

}
