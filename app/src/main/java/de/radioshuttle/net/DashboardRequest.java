/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.content.Context;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.MutableLiveData;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.dash.ViewState;

public class DashboardRequest extends Request {

    public DashboardRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData, long localVersion) {
        super(context, pushAccount, accountLiveData);
        mGetTopicFilterScripts = false; // disable getTopics in super class
        mLocalVersion = localVersion;
        invalidVersion = false;
        mReceivedDashboard = null;
    }

    public void saveDashboard(JSONObject dashboard, int itemID) { // TODO: pass json str
        mCmd = Cmd.CMD_SET_DASHBOARD;
        mDashboardPara = dashboard;
        mItemIDPara = itemID;
    }

    public void loadDashboard() {
        mCmd = Cmd.CMD_GET_DASHBOARD;
    }

    @Override
    public boolean perform() throws Exception {

        if (mCmd == Cmd.CMD_SET_DASHBOARD) {
            try {
                String jsonStr = mDashboardPara.toString();

                long result = mConnection.setDashboardRequest(mLocalVersion, mItemIDPara, jsonStr);
                if (result != 0) { //Cmd.RC_OK
                    mServerVersion = result;
                    mReceivedDashboard = jsonStr;
                    ViewState.getInstance(mAppContext).saveDashboard(mPushAccount.getKey(), mServerVersion, mReceivedDashboard);
                    mSaved = true;
                } else {
                    invalidVersion = true;
                }
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;

        }

        /* always get last messages of subcribed topics and dashboard version timestamp */
        if (requestStatus == Cmd.RC_OK) {
            List<Object[]> result = new ArrayList<>();
            try {
                mServerVersion = mConnection.getCachedMessagesDash(result);
                mReceivedMessages = new ArrayList<>();

                MqttMessage mqttMessage;
                for(int i = 0; i < result.size(); i++) {
                    mqttMessage = new MqttMessage();
                    mqttMessage.setWhen((Long) result.get(i)[0] * 1000L);
                    mqttMessage.setTopic((String) result.get(i)[1]);
                    mqttMessage.setPayload((byte[]) result.get(i)[2]);
                    mqttMessage.setSeqno((Integer) result.get(i)[3]);
                    mReceivedMessages.add(mqttMessage);
                }
                //TODO: sort descending by date

                //TODO save cached messages locally
            } catch (ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        }

        if (requestStatus == Cmd.RC_OK) {
            /* server version != local version: we need an update */
            invalidVersion = invalidVersion || (mCmd != Cmd.CMD_SET_DASHBOARD && mServerVersion > 0 && mLocalVersion != mServerVersion);

            if (invalidVersion || mCmd == Cmd.CMD_GET_DASHBOARD) {
                try {
                    Object[] dash = mConnection.getDashboard();
                    if (dash != null) {
                        mServerVersion = (long) dash[0];
                        mReceivedDashboard = (String) dash[1];
                        ViewState.getInstance(mAppContext).saveDashboard(mPushAccount.getKey(), mServerVersion, mReceivedDashboard);
                    }
                } catch(ServerError e) {
                    requestErrorCode = e.errorCode;
                    requestErrorTxt = e.getMessage();
                }
                requestStatus = mConnection.lastReturnCode;
            }
        }

        return true;
    }

    // save might have success, but afterwards getCachedDashMessages() could be fail
    public boolean saveSuccesful() {
        return mSaved;
    }

    public boolean isVersionError() {
        return invalidVersion;
    }

    /** not defined if requestStatus != Cmd.RC_OK*/
    public long getServerVersion() {
        return mServerVersion;
    }

    public String getReceivedDashboard() {
        return mReceivedDashboard;
    }
    public List<MqttMessage> getReceivedMessages() {
        return mReceivedMessages == null ? new ArrayList<MqttMessage>() : mReceivedMessages;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;


    List<MqttMessage> mReceivedMessages;

    boolean mSaved;
    boolean invalidVersion;
    int mItemIDPara;
    JSONObject mDashboardPara;
    String mReceivedDashboard;
    long mLocalVersion;
    long mServerVersion;

    public int mCmd;

}
