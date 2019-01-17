/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;

import de.radioshuttle.net.ActionsRequest;
import de.radioshuttle.net.Request;

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
        }
    }

    public void getActions(Context context, boolean sync, boolean checkHasTopics) {
        requestCnt++;
        currentRequest = new ActionsRequest(context, pushAccount, actionsRequest);
        currentRequest.setSync(true);
        currentRequest.execute();
    }

    public void deleteActions(Context context, List<String> actionNames) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.deleteActions(actionNames);
        currentRequest = request;
        currentRequest.execute();
    }

    public void addAction(Context context, ActionsViewModel.Action a) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.addAction(a);
        currentRequest = request;
        currentRequest.execute();
    }

    public void publish(Context context, ActionsViewModel.Action a) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.publish(a);
        currentRequest = request;
        currentRequest.execute();
    }

    public void updateAction(Context context, ActionsViewModel.Action a) {
        requestCnt++;
        ActionsRequest request = new ActionsRequest(context, pushAccount, actionsRequest);
        request.updateAction(a);
        currentRequest = request;
        currentRequest.execute();
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

    public static class Action {
        public String name;
        public String topic;
        public String content;
        public String prevName;
        public boolean retain;
    }


    public MutableLiveData<Request> actionsRequest;
    public HashSet<String> selectedActions;
    public PushAccount pushAccount;
    public boolean initialized;
    public String lastEnteredAction;
    private int requestCnt;
    private Request currentRequest;

}
