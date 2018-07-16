package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.radioshuttle.mqttpushclient.PushAccount;

public class TopicsRequest extends Request {

    public TopicsRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
    }

    public void addTopic(String topic) {
        ArrayList<String> topics = new ArrayList<>();
        topics.add(topic);
        addTopics(topics);
    }

    public void addTopics(List<String> topics) {
        mCmd = Cmd.CMD_SUBSCRIBE;
        mTopics = topics;
    }

    public void deleteTopics(List<String> topics) {
        mCmd = Cmd.CMD_UNSUBSCRIBE;
        mTopics = topics;
    }

    @Override
    public boolean perform() throws Exception {
        if (mCmd == Cmd.CMD_UNSUBSCRIBE) {
            try {
                int[] rc = mConnection.deleteTopics(mTopics);
                //TODO: handle rc
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        } else if (mCmd == Cmd.CMD_SUBSCRIBE) {
            try {
                int[] rc = mConnection.addTopics(mTopics);
                //TODO: handle rc
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
        }

        ArrayList<String> result = mConnection.getTopics();
        if (mConnection.lastReturnCode == Cmd.RC_OK) {
            if (result == null)
                result = new ArrayList<>();
            Collections.sort(result);
            mPushAccount.topics = result;
        } else {
            if (mCmd == Cmd.CMD_UNSUBSCRIBE && requestStatus == Cmd.RC_OK) {
                ArrayList<String> tmp = new ArrayList<>(mPushAccount.topics);
                tmp.removeAll(mTopics);
                mPushAccount.topics = tmp;
            }
        }

        return true;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    public int mCmd;
    public List<String> mTopics;
}
