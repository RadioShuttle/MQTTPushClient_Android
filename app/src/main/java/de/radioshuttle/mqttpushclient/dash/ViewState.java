/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.AccountListActivity.PREFS_NAME;

public class ViewState {

    public static ViewState getInstance(Application app) {
        if (mViewState == null) {
            mViewState = new ViewState(app);
        }
        return mViewState;
    }

    public int getLastState(String account) {
        int state = -1;
        if (account != null && mState.containsKey(account)) {
            state = mState.get(account);
        }
        return state;
    }

    public void setLastState(String account, int newState) {
        Log.d(VIEW_STATE, "set state: " + account + " " + newState);
        if (account != null) {
            int last = getLastState(account);
            mState.put(account, newState);
            if (last != newState) {
                writeState();
            }
        }
    }

    public void removeAccount(String account) {
        if (mState.containsKey(account)) {
            mState.remove(account);
            writeState();
        }
    }

    private ViewState(Application app) {
        mApp = app;
        readStates();
    }

    private void readStates() {
        mState = new HashMap<>();
        SharedPreferences settings = mApp.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String json = settings.getString(VIEW_STATE, null);
        if (!Utils.isEmpty(json)) {
            try {
                JSONObject vs = new JSONObject(json);
                Iterator<String> it = vs.keys();
                int state;
                String account;
                while(it.hasNext()) {
                    account = it.next();
                    state = vs.optInt(account, -1);
                    if (state != -1) {
                        mState.put(account, state);
                    }
                }
            } catch (JSONException e) {
                Log.e(VIEW_STATE, "Parsing view states failed." , e);
            }
        }
    }

    private void writeState() {
        JSONObject vs = new JSONObject();
        String out = null;
        Iterator<Map.Entry<String, Integer>> it;
        Map.Entry<String, Integer> entry;
        for(it = mState.entrySet().iterator(); it.hasNext();) {
            entry = it.next();
            try {
                vs.put(entry.getKey(), entry.getValue());
            } catch (JSONException e) {
                Log.e(VIEW_STATE, "Setting view states failed." , e);
            }
        }
        if (vs.length() > 0) {
            out = vs.toString();
        }
        SharedPreferences settings = mApp.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        SharedPreferences.Editor editor = settings.edit();
        editor.putString(VIEW_STATE, out);
        editor.commit();

    }

    private HashMap<String, Integer> mState;
    private Application mApp;
    static ViewState mViewState;

    public final static int VIEWSTATE_MESSAGES = 1;
    public final static int VIEWSTATE_DASHBOARD = 2;
    public final static String VIEW_STATE = "VIEW_STATE";
}
