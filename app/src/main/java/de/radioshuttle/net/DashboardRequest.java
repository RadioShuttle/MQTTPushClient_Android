/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.lifecycle.MutableLiveData;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.mqttpushclient.dash.Message;
import de.radioshuttle.mqttpushclient.dash.ViewState;

public class DashboardRequest extends Request {

    public DashboardRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData, long localVersion) {
        super(context, pushAccount, accountLiveData);
        mGetTopicFilterScripts = false; // disable getTopics in super class
        mLocalVersion = localVersion;
        invalidVersion = false;
        mStoreDashboardLocally = false;
        mReceivedDashboard = null;
    }

    public void saveDashboard(JSONObject dashboard, int itemID) { // TODO: pass json str
        mCmd = Cmd.CMD_SET_DASHBOARD;
        mDashboardPara = dashboard;
        mItemIDPara = itemID;
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
                    mStoreDashboardLocally = true;
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
            if (requestStatus == Cmd.RC_INVALID_ARGS) {
                requestErrorTxt = mAppContext.getString(R.string.err_invalid_topic_format);
            }

        } else {
            /* get last messages of subcribed topics and dashboard version timestamp */
            List<Object[]> result = new ArrayList<>();
            try {
                //TODO: set since
                mServerVersion = mConnection.getCachedMessagesDash(0, 0, result);
                mReceivedMessages = new ArrayList<>();

                Message mqttMessage;
                for(int i = 0; i < result.size(); i++) {
                    mqttMessage = new Message();
                    mqttMessage.setWhen((Long) result.get(i)[0] * 1000L);
                    mqttMessage.setTopic((String) result.get(i)[1]);
                    mqttMessage.setPayload((byte[]) result.get(i)[2]);
                    mqttMessage.setSeqno((Integer) result.get(i)[3]);
                    mqttMessage.status = (Short) result.get(i)[4];
                    mqttMessage.filter = (String) result.get(i)[5];
                    mReceivedMessages.add(mqttMessage);
                }
                //TODO save cached messages locally

            } catch (ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        }


        if (requestStatus == Cmd.RC_OK) {
            /* server version != local version: update required */
            invalidVersion = invalidVersion || (mCmd != Cmd.CMD_SET_DASHBOARD && mServerVersion > 0 && mLocalVersion != mServerVersion);

            if (invalidVersion) {
                try {
                    Object[] dash = mConnection.getDashboard();
                    if (dash != null) {
                        mServerVersion = (long) dash[0];
                        mReceivedDashboard = (String) dash[1];
                        mStoreDashboardLocally = true;
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

    @Override
    protected void onPostExecute(PushAccount pushAccount) {
        mCompleted = true;
        super.onPostExecute(pushAccount);
        if (mStoreDashboardLocally) {
            ViewState.getInstance(mAppContext).saveDashboard(mPushAccount.getKey(), mServerVersion, mReceivedDashboard);
            Log.d("DashboradRequest",  "local version: " + mLocalVersion + ", server version: " + mServerVersion);
        }
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
    public List<Message> getReceivedMessages() {
        return mReceivedMessages == null ? new ArrayList<Message>() : mReceivedMessages;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    List<Message> mReceivedMessages;

    boolean mSaved;
    boolean invalidVersion;
    int mItemIDPara;
    JSONObject mDashboardPara;
    String mReceivedDashboard;
    long mLocalVersion;
    long mServerVersion;

    public int mCmd;

    protected boolean mStoreDashboardLocally; // indicates if mReceivedDashboard must be stored in onPostExecute()

}
