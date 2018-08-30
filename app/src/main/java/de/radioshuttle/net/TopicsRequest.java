package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import de.radioshuttle.mqttpushclient.PushAccount;

public class TopicsRequest extends Request {

    public TopicsRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
    }

    public void addTopic(PushAccount.Topic topic) {
        ArrayList<PushAccount.Topic> topics = new ArrayList<>();
        topics.add(topic);
        addTopics(topics);
    }

    public void addTopics(List<PushAccount.Topic> topics) {
        mCmd = Cmd.CMD_SUBSCRIBE;
        mTopics = topics;
    }

    public void deleteTopics(List<String> topics) {
        mCmd = Cmd.CMD_UNSUBSCRIBE;
        mDelTopics = topics;
    }

    @Override
    public boolean perform() throws Exception {
        //TODO:
        List<String> tmp;
        if (mCmd == Cmd.CMD_UNSUBSCRIBE) {
            tmp = mDelTopics;
            try {
                int[] rc = mConnection.deleteTopics(tmp);
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
                tmp = new ArrayList<>();
                for(PushAccount.Topic t : mTopics) {
                    tmp.add(t.name);
                }
                int[] rc = mConnection.addTopics(tmp); //TODO: add prios
                //TODO: handle rc
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
        }

        ArrayList<String> result = mConnection.getTopics(); //TODO: get prios
        ArrayList<PushAccount.Topic> tmpRes = new ArrayList<>();

        if (mConnection.lastReturnCode == Cmd.RC_OK) {
            if (result == null)
                result = new ArrayList<>();
            Collections.sort(result);
            for(String s : result) {
                PushAccount.Topic t = new PushAccount.Topic();
                t.name = s;
                t.prio = 0; //TODO
                tmpRes.add(t);
            }

            mPushAccount.topics = tmpRes;
        } else {
            if (mCmd == Cmd.CMD_UNSUBSCRIBE && requestStatus == Cmd.RC_OK && mPushAccount.topics != null) {
                ArrayList<PushAccount.Topic> tmp2 = new ArrayList<>(mPushAccount.topics);
                HashSet<String> deleted = new HashSet<>(mDelTopics);
                Iterator<PushAccount.Topic> t = tmp2.iterator();
                while(t.hasNext()) {
                    PushAccount.Topic e = t.next();
                    if (deleted.contains(e.name)) {
                        t.remove();
                    }
                }
                mPushAccount.topics = tmp2;
            }
        }

        return true;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    public int mCmd;
    public List<PushAccount.Topic> mTopics;
    protected List<String> mDelTopics;
}
