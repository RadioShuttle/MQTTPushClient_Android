/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;

import de.radioshuttle.net.Request;
import de.radioshuttle.net.TopicsRequest;

public class TopicsViewModel extends ViewModel{

    public TopicsViewModel() {
        initialized = false;
        topicsRequest = new MutableLiveData<>();
        requestCnt = 0;
        selectedTopics = new HashSet<>();
    }

    public void init(String accountJson) throws JSONException {
        if (!initialized) {
            initialized = true;
            pushAccount = PushAccount.createAccountFormJSON(new JSONObject(accountJson));
            pushAccount.topics.clear(); // load topics from server
        }
    }

    public void getTopics(Context context) {
        requestCnt++;
        currentRequest = new TopicsRequest(context, pushAccount, topicsRequest);
        currentRequest.execute();
    }

    public void deleteTopics(Context context, List<String> topics) {
        requestCnt++;
        TopicsRequest request = new TopicsRequest(context, pushAccount, topicsRequest);
        request.deleteTopics(topics);
        currentRequest = request;
        currentRequest.execute();
    }

    public void addTopic(Context context, PushAccount.Topic topic) {
        requestCnt++;
        TopicsRequest request = new TopicsRequest(context, pushAccount, topicsRequest);
        request.addTopic(topic);
        currentRequest = request;
        currentRequest.execute();
    }

    public void updateTopic(Context context, PushAccount.Topic topic) {
        requestCnt++;
        TopicsRequest request = new TopicsRequest(context, pushAccount, topicsRequest);
        request.updateTopic(topic);
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

    public MutableLiveData<Request> topicsRequest;
    public HashSet<String> selectedTopics;
    public PushAccount pushAccount;
    public boolean initialized;
    public String lastEnteredTopic;
    private int requestCnt;
    private Request currentRequest;

}
