/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
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

    public static ViewState getInstance(Context app) {
        if (mViewState == null) {
            mViewState = new ViewState(app);
        }
        return mViewState;
    }

    public int getLastState(String account) {
        ViewInfo vi = getViewInfo(account);
        return vi == null ? VIEW_MESSAGES : vi.lastView;
    }

    public int getLastZoomLevel(String account) {
        ViewInfo vi = getViewInfo(account);
        return vi == null ? 1 : vi.lastZoomLevel;
    }

    protected ViewInfo getViewInfo(String account) {
        ViewInfo vi;
        if (account != null && mState.containsKey(account)) {
            vi = mState.get(account);
        } else {
            vi = null;
        }
        return vi;
    }

    public void setLastState(String account, int newState) {
        Log.d(VIEW_STATE, "set state: " + account + " " + newState);
        if (account != null) {
            ViewInfo vi = getViewInfo(account);
            if (vi == null) {
                vi = new ViewInfo();
            }
            if (vi.lastView != newState) {
                vi.lastView = newState;
                mState.put(account, vi);
                writeState();
            }
        }
    }

    public void saveDashboard(String account, long timeStamp, String json) {
        Log.d(VIEW_STATE, "save dashboard: " + account + " " + json); //TODO: remove after rest
        if (account != null) {
            ViewInfo vi = getViewInfo(account);
            if (vi == null) {
                vi = new ViewInfo();
            }
            if (vi.dashboard_mdate != timeStamp) {
                vi.dashboard_mdate = timeStamp;
                vi.dashboard_content = (json == null ? "" : json);
                mState.put(account, vi);
                writeState();
            }
        }
    }

    public String getDashBoardContent(String account) {
        ViewInfo vi = getViewInfo(account);
        return vi == null ? "" : vi.dashboard_content;
    }

    public long getDashBoardModificationDate(String account) {
        ViewInfo vi = getViewInfo(account);
        return vi == null ? 0 : vi.dashboard_mdate;
    }

    public void setLastZoomLevel(String account, int newZoomLevel) {
        if (account != null) {
            ViewInfo vi = getViewInfo(account);
            if (vi == null) {
                vi = new ViewInfo();
            }
            if (vi.lastZoomLevel != newZoomLevel) {
                vi.lastZoomLevel = newZoomLevel;
                mState.put(account, vi);
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

    private ViewState(Context app) {
        mApp = app;
        readStates();
    }

    private void readStates() {
        mState = new HashMap<>();
        SharedPreferences settings = mApp.getSharedPreferences(DASHBOARD_PREFS, Activity.MODE_PRIVATE);
        String json = settings.getString(VIEW_STATE, null);
        if (!Utils.isEmpty(json)) {
            try {
                JSONObject vs = new JSONObject(json);
                Iterator<String> it = vs.keys();
                JSONObject viewInfo;
                String account;
                while(it.hasNext()) {
                    account = it.next();
                    vs.opt(account);
                    viewInfo = vs.optJSONObject(account);
                    if (viewInfo != null) {
                        ViewInfo vi = new ViewInfo();
                        vi.lastView = viewInfo.optInt("last_view", VIEW_MESSAGES);
                        vi.lastZoomLevel = viewInfo.optInt("last_zoom_level", 1);
                        vi.dashboard_mdate = viewInfo.optLong("dashboar_mdate", -1L);
                        vi.dashboard_content = viewInfo.optString("dashboard_content", "");
                        mState.put(account, vi);
                    }
                }
            } catch (JSONException e) {
                Log.e(VIEW_STATE, "Parsing view states failed." , e);
            }
        }
    }

    private void writeState() {
        JSONObject vs = new JSONObject();
        JSONObject val;
        String out = null;
        Iterator<Map.Entry<String, ViewInfo>> it;
        Map.Entry<String, ViewInfo> entry;
        for(it = mState.entrySet().iterator(); it.hasNext();) {
            entry = it.next();
            try {
                val = new JSONObject();
                val.put("last_view", entry.getValue().lastView);
                val.put("last_zoom_level", entry.getValue().lastZoomLevel);
                val.put("dashboar_mdate", entry.getValue().dashboard_mdate);
                val.put("dashboard_content", entry.getValue().dashboard_content);
                vs.put(entry.getKey(), val);
            } catch (JSONException e) {
                Log.e(VIEW_STATE, "Setting view states failed." , e);
            }
        }
        if (vs.length() > 0) {
            out = vs.toString();
        }
        SharedPreferences settings = mApp.getSharedPreferences(DASHBOARD_PREFS, Activity.MODE_PRIVATE);

        SharedPreferences.Editor editor = settings.edit();
        editor.putString(VIEW_STATE, out);
        editor.commit();

    }

    private HashMap<String, ViewInfo> mState;
    private Context mApp;
    static ViewState mViewState;

    private static class ViewInfo {
        public ViewInfo() {
            lastView = 0;
            lastZoomLevel = 0;
            dashboard_mdate = 0;
            dashboard_content = "";
        }

        int lastView;
        int lastZoomLevel;
        long dashboard_mdate;
        String dashboard_content;
    }

    public final static int VIEW_MESSAGES = 1;
    public final static int VIEW_DASHBOARD = 2;
    public final static String VIEW_STATE = "VIEW_STATE";

    public final static String DASHBOARD_PREFS = "dashboard_prefs";
}
