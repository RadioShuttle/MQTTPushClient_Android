/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;

import de.radioshuttle.net.Request;

public class ActionsViewModel extends ViewModel {

    public ActionsViewModel() {
        initialized = false;
        topicsRequest = new MutableLiveData<>();
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

    public MutableLiveData<Request> topicsRequest;
    public HashSet<String> selectedActions;
    public PushAccount pushAccount;
    public boolean initialized;
    public String lastEnteredAction;
    private int requestCnt;
    private Request currentRequest;

}
