package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.Utils;

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

        if (mCmd == Cmd.CMD_UNSUBSCRIBE) {
            try {
                int[] rc = mConnection.deleteTopics(mDelTopics);
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
                HashMap<String, Integer> tmp = new HashMap<>();
                for(PushAccount.Topic t : mTopics) {
                    tmp.put(t.name, t.prio);
                }
                int[] rc = mConnection.addTopics(tmp);
                //TODO: handle rc
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
        }

        LinkedHashMap<String, Integer> result = mConnection.getTopics();
        ArrayList<PushAccount.Topic> tmpRes = new ArrayList<>();

        if (mConnection.lastReturnCode == Cmd.RC_OK) {
            if (result == null)
                result = new LinkedHashMap<>();

            for(Iterator<Map.Entry<String, Integer>> it = result.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Integer> e = it.next();
                PushAccount.Topic t = new PushAccount.Topic();
                t.name = e.getKey();
                t.prio = e.getValue();
                tmpRes.add(t);
            }
            Collections.sort(tmpRes, new Comparator<PushAccount.Topic>() {
                @Override
                public int compare(PushAccount.Topic o1, PushAccount.Topic o2) {
                    String s1 = (o1.name == null ? "" : o1.name);
                    String s2 = (o2.name == null ? "" : o2.name);
                    return s1.compareTo(s2);
                }
            });

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
