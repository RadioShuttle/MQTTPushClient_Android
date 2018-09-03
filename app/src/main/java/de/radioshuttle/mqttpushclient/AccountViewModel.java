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

    public MutableLiveData<ArrayList<PushAccount>> accountList;
    public MutableLiveData<Request> request;
    public boolean initialized;
    private int requestCnt;
    private ArrayList<Request> currentRequests;

    public AccountViewModel() {
        accountList = new MutableLiveData<>();
        request = new MutableLiveData<>();
        initialized = false;
        requestCnt = 0;
    }

    public void init(String accountsJson) throws JSONException {
        if (!initialized) {
            setAccountsJSON(accountsJson, true);
            initialized = true;
            //TODO: test date remove
            if (accountList.getValue() == null || accountList.getValue().isEmpty()) {
                ArrayList<PushAccount> pushAccounts = new ArrayList<PushAccount>();
                pushAccounts.add(PushAccount.createAccountFormJSON(
                        new JSONObject(
                                "{\"pushserver\":\"192.168.178.80\",\"pushserverID\":\"stonehenge.helios.de:2033\",\"uri\":\"tcp:\\/\\/mqtt.arduino-hannover.de:1883\",\"user\":\"XXXXXXXX\",\"id\":1,\"password\":\"XXXXXXXX\",\"clientID\":\"MQTTPushClient\",\"topics\":[\"XXXXXXXX\\/HELIOS\"]}"
                        ))
                );
                accountList.setValue(pushAccounts);
            }
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
                Request request = new Request(context, pushAccounts.get(i), this.request);
                currentRequests.add(request);
                request.execute();
            }
        }
    }

    /* add or uodate account */
    public void saveAccount(Context context, PushAccount pushAccount) {

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
        Request request = new Request(context, pushAccount, this.request);
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
            request.getAccount().status == 0 &&
                    currentRequests != null &&
                    currentRequests.size() > 0 &&
                    currentRequests.get(currentRequests.size() - 1) == request;
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

}
