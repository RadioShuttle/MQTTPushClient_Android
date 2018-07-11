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

import de.radioshuttle.net.Request;
import de.radioshuttle.net.DeleteToken;

public class AccountViewModel extends ViewModel {

    public MutableLiveData<ArrayList<PushAccount>> brokerList;
    public MutableLiveData<Request> requestBroker;
    public boolean initialized;
    private int requestCnt;
    private ArrayList<Request> currentRequests;

    public AccountViewModel() {
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
                ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
                pushAccounts.add(PushAccount.createBrokerFormJSON(
                        new JSONObject(
                                "{\"pushserver\":\"192.168.178.80\",\"uri\":\"tcp:\\/\\/mqtt.arduino-hannover.de:1883\",\"user\":\"XXXXXXXX\",\"id\":1,\"password\":\"XXXXXXXX\",\"clientID\":\"MQTTPushClient\",\"topics\":[\"XXXXXXXX\\/HELIOS\"]}"
                        ))
                );
                brokerList.setValue(pushAccounts);
            }
        }
    }

    public void setBrokersJSON(String brokersJson, boolean init) throws JSONException {
        ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
        if (brokersJson != null) {
            JSONArray jarray = new JSONArray(brokersJson);
            for(int i = 0 ; i < jarray.length(); i++) {
                JSONObject b = jarray.getJSONObject(i);
                pushAccounts.add(PushAccount.createBrokerFormJSON(b));
            }
        }
        if (init) {
            for(PushAccount b : pushAccounts) {
                b.status = -1;
            }
        }
        brokerList.setValue(pushAccounts);
    }

    public String getBrokersJSON() throws JSONException {
        JSONArray brokerList = new JSONArray();
        ArrayList<PushAccount> list = this.brokerList.getValue();
        for(PushAccount b : list) {
            brokerList.put(b.getJSONObject());
        }
        return brokerList.toString();
    }

    public PushAccount removeBorker(String key) {
        PushAccount ret = null;
        ArrayList<PushAccount> pushAccounts = brokerList.getValue();
        if (key != null && pushAccounts != null && pushAccounts.size() > 0) {
            for(Iterator<PushAccount> it = pushAccounts.iterator(); it.hasNext();) {
                PushAccount b = it.next();
                if (key.equals(b.getKey())) {
                    ret = b;
                    it.remove();
                    brokerList.setValue(pushAccounts);
                    break;
                }
            }
        }
        return ret;
    }

    public void checkBrokers(Context context) {
        ArrayList<PushAccount> pushAccounts = brokerList.getValue();

        if (currentRequests == null) {
            currentRequests = new ArrayList<>();
        } else {
            /* cancel current tasks */
            for(Request b : currentRequests) {
                b.cancel();
            }
            currentRequests.clear();
        }

        if (pushAccounts != null) {
            requestCnt++;
            for(int i = 0; i < pushAccounts.size(); i++) {
                Request request = new Request(context, pushAccounts.get(i), requestBroker);
                currentRequests.add(request);
                request.execute();
            }
        }
    }

    /* add or uodate broker */
    public void saveBroker(Context context, PushAccount pushAccount) {

        if (currentRequests == null) {
            currentRequests = new ArrayList<>();
        } else {
            /* cancel current tasks */
            for(Request b : currentRequests) {
                b.cancel();
            }
            currentRequests.clear();
        }
        requestCnt++;
        Request request = new Request(context, pushAccount, requestBroker);
        currentRequests.add(request);
        request.execute();
    }

    public void deleteToken(Context context, PushAccount pushAccount) {
        DeleteToken deleteRequest = new DeleteToken(context, pushAccount, null);
        deleteRequest.execute();
    }

    public boolean isRequestActive() {
        return requestCnt > 0;
    }

    public void confirmResultDelivered() {
        requestCnt = 0;
    }

    public boolean isCurrentRequest(Request request) {
        return
            request.getBroker().status == 0 &&
                    currentRequests != null &&
                    currentRequests.size() > 0 &&
                    currentRequests.get(currentRequests.size() - 1) == request;
    }

    public boolean hasMultiplePushServers() {
        boolean multi = false;
        ArrayList<PushAccount> pushAccounts = brokerList.getValue();
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

}
