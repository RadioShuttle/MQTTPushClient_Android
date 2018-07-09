package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.radioshuttle.mqttpushclient.Broker;

public class TopicsRequest extends BrokerRequest {

    public TopicsRequest(Context context, Broker broker, MutableLiveData<BrokerRequest> brokerLiveData) {
        super(context, broker, brokerLiveData);
    }

    public void addTopic(String topic) {
        ArrayList<String> topics = new ArrayList<>();
        topics.add(topic);
        addTopics(topics);
    }

    public void addTopics(List<String> topics) {
        mCmd = Cmd.CMD_ADD_TOPICS;
        mTopics = topics;
    }

    public void deleteTopics(List<String> topics) {
        mCmd = Cmd.CMD_DEL_TOPICS;
        mTopics = topics;
    }

    @Override
    public boolean perform() throws Exception {
        if (mCmd == Cmd.CMD_DEL_TOPICS) {
            try {
                mConnection.deleteTopics(mTopics);
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        } else if (mCmd == Cmd.CMD_ADD_TOPICS) {
            try {
                mConnection.addTopics(mTopics);
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
            mBroker.topics = result;
        } else {
            if (mCmd == Cmd.CMD_DEL_TOPICS && requestStatus == Cmd.RC_OK) {
                ArrayList<String> tmp = new ArrayList<>(mBroker.topics);
                tmp.removeAll(mTopics);
                mBroker.topics = tmp;
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
