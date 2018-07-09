/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

import de.radioshuttle.net.BrokerRequest;
import de.radioshuttle.net.DeleteToken;

public class BrokerViewModel extends ViewModel {

    public MutableLiveData<ArrayList<Broker>> brokerList;
    public MutableLiveData<BrokerRequest> requestBroker;
    public boolean initialized;
    private int requestCnt;
    private ArrayList<BrokerRequest> currentBrokerRequests;

    public BrokerViewModel() {
        brokerList = new MutableLiveData<>();
        requestBroker = new MutableLiveData<>();
        initialized = false;
        requestCnt = 0;
    }

    public void init(String brokersJson) throws JSONException {
        if (!initialized) {
            setBrokersJSON(brokersJson, true);
            initialized = true;
            //TODO: test date remove
            if (brokerList.getValue() == null || brokerList.getValue().isEmpty()) {
                ArrayList<Broker> brokers = new ArrayList<Broker>();
                brokers.add(Broker.createBrokerFormJSON(
                        new JSONObject(
                                "{\"pushserver\":\"192.168.178.80\",\"uri\":\"tcp:\\/\\/mqtt.arduino-hannover.de:1883\",\"user\":\"XXXXXXXX\",\"id\":1,\"password\":\"XXXXXXXX\",\"clientID\":\"MQTTPushClient\",\"topics\":[\"XXXXXXXX\\/HELIOS\"]}"
                        ))
                );
                brokerList.setValue(brokers);
            }
        }
    }

    public void setBrokersJSON(String brokersJson, boolean init) throws JSONException {
        ArrayList<Broker> brokers = new ArrayList<Broker>();
        if (brokersJson != null) {
            JSONArray jarray = new JSONArray(brokersJson);
            for(int i = 0 ; i < jarray.length(); i++) {
                JSONObject b = jarray.getJSONObject(i);
                brokers.add(Broker.createBrokerFormJSON(b));
            }
        }
        if (init) {
            for(Broker b : brokers) {
                b.status = -1;
            }
        }
        brokerList.setValue(brokers);
    }

    public String getBrokersJSON() throws JSONException {
        JSONArray brokerList = new JSONArray();
        ArrayList<Broker> list = this.brokerList.getValue();
        for(Broker b : list) {
            brokerList.put(b.getJSONObject());
        }
        return brokerList.toString();
    }

    public Broker removeBorker(String key) {
        Broker ret = null;
        ArrayList<Broker> brokers = brokerList.getValue();
        if (key != null && brokers != null && brokers.size() > 0) {
            for(Iterator<Broker> it = brokers.iterator(); it.hasNext();) {
                Broker b = it.next();
                if (key.equals(b.getKey())) {
                    ret = b;
                    it.remove();
                    brokerList.setValue(brokers);
                    break;
                }
            }
        }
        return ret;
    }

    public void checkBrokers(Context context) {
        ArrayList<Broker> brokers = brokerList.getValue();

        if (currentBrokerRequests == null) {
            currentBrokerRequests = new ArrayList<>();
        } else {
            /* cancel current tasks */
            for(BrokerRequest b : currentBrokerRequests) {
                b.cancel();
            }
            currentBrokerRequests.clear();
        }

        if (brokers != null) {
            requestCnt++;
            for(int i = 0; i < brokers.size(); i++) {
                BrokerRequest request = new BrokerRequest(context, brokers.get(i), requestBroker);
                currentBrokerRequests.add(request);
                request.execute();
            }
        }
    }

    /* add or uodate broker */
    public void saveBroker(Context context, Broker broker) {

        if (currentBrokerRequests == null) {
            currentBrokerRequests = new ArrayList<>();
        } else {
            /* cancel current tasks */
            for(BrokerRequest b : currentBrokerRequests) {
                b.cancel();
            }
            currentBrokerRequests.clear();
        }
        requestCnt++;
        BrokerRequest request = new BrokerRequest(context, broker, requestBroker);
        currentBrokerRequests.add(request);
        request.execute();
    }

    public void deleteToken(Context context, Broker broker) {
        DeleteToken deleteRequest = new DeleteToken(context, broker, null);
        deleteRequest.execute();
    }

    public boolean isRequestActive() {
        return requestCnt > 0;
    }

    public void confirmResultDelivered() {
        requestCnt = 0;
    }

    public boolean isCurrentRequest(BrokerRequest request) {
        return
            request.getBroker().status == 0 &&
                    currentBrokerRequests != null &&
                    currentBrokerRequests.size() > 0 &&
                    currentBrokerRequests.get(currentBrokerRequests.size() - 1) == request;
    }

    public boolean hasMultiplePushServers() {
        boolean multi = false;
        ArrayList<Broker> brokers = brokerList.getValue();
        if (brokers != null) {
            HashSet<String> pushServer = new HashSet<>();
            for(Broker br : brokers) {
                if (br.pushserver != null && br.pushserver.trim().length() > 0) {
                    pushServer.add(br.pushserver.trim());
                }
            }
            multi = pushServer.size() > 1;
        }
        return multi;
    }

}
