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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.radioshuttle.net.BrokerRequest;
import de.radioshuttle.net.TopicsRequest;

public class TopicsViewModel extends ViewModel{

    public TopicsViewModel() {
        initialized = false;
        topicsRequest = new MutableLiveData<>();
        requestCnt = 0;
        selectedTopics = new HashSet<>();
    }

    public void init(String brokerJson) throws JSONException {
        if (!initialized) {
            initialized = true;
            broker = Broker.createBrokerFormJSON(new JSONObject(brokerJson));
            broker.topics.clear(); // load topics from server
        }
    }

    public void getTopics(Context context) {
        requestCnt++;
        currentRequest = new TopicsRequest(context, broker, topicsRequest);
        currentRequest.execute();
    }

    public void deleteTopics(Context context, List<String> topics) {
        requestCnt++;
        TopicsRequest request = new TopicsRequest(context, broker, topicsRequest);
        request.deleteTopics(topics);
        currentRequest = request;
        currentRequest.execute();
    }

    public void addTopic(Context context, String topic) {
        requestCnt++;
        TopicsRequest request = new TopicsRequest(context, broker, topicsRequest);
        request.addTopic(topic);
        currentRequest = request;
        currentRequest.execute();
    }

    public boolean isRequestActive() {
        return requestCnt > 0;
    }

    public void confirmResultDelivered() {
        requestCnt = 0;
    }

    public boolean isCurrentRequest(BrokerRequest request) {
        return currentRequest == request;
    }

    public MutableLiveData<BrokerRequest> topicsRequest;
    public HashSet<String> selectedTopics;
    public Broker broker;
    public boolean initialized;
    public String lastEnteredTopic;
    private int requestCnt;
    private BrokerRequest currentRequest;

}
