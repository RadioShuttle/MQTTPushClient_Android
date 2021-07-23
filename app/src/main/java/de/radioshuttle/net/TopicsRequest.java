/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

import androidx.lifecycle.MutableLiveData;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.radioshuttle.mqttpushclient.PushAccount;

public class TopicsRequest extends Request {

    public TopicsRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
        mGetTopicFilterScripts = false; // disable getTopics in super class
    }

    public void addTopic(PushAccount.Topic topic) {
        LinkedHashMap<String, Cmd.Topic> topics = new LinkedHashMap<>();
        Cmd.Topic t = new Cmd.Topic();
        t.script = topic.jsSrc;
        t.type = topic.prio;
        topics.put(topic.name, t);
        addTopics(topics);
    }

    public void addTopics(LinkedHashMap<String, Cmd.Topic> topics) {
        mCmd = Cmd.CMD_ADD_TOPICS;
        mTopics = topics;
    }

    public void deleteTopics(List<String> topics) {
        mCmd = Cmd.CMD_DEL_TOPICS;
        mDelTopics = topics;
    }

    public void updateTopic(PushAccount.Topic topic) {
        LinkedHashMap<String, Cmd.Topic> topics = new LinkedHashMap<>();
        Cmd.Topic t = new Cmd.Topic();
        t.script = topic.jsSrc;
        t.type = topic.prio;
        topics.put(topic.name, t);
        updateTopics(topics);
    }

    public void updateTopics(LinkedHashMap<String, Cmd.Topic> topics) {
        mCmd = Cmd.CMD_UPD_TOPICS;
        mTopics = topics;
    }

    @Override
    public boolean perform() throws Exception {

        if (mCmd == Cmd.CMD_DEL_TOPICS) {
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
        } else if (mCmd == Cmd.CMD_ADD_TOPICS || mCmd == Cmd.CMD_UPD_TOPICS) {
            try {
                int[] rc;
                if (mCmd == Cmd.CMD_ADD_TOPICS) {
                     rc = mConnection.addTopics(mTopics);
                } else {
                    rc = mConnection.updateTopics(mTopics);
                }
                //TODO: handle rc
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        }

        LinkedHashMap<String, Cmd.Topic> result = mConnection.getTopics();
        updateLocalStoredScripts(result);
        ArrayList<PushAccount.Topic> tmpRes = new ArrayList<>();

        if (mConnection.lastReturnCode == Cmd.RC_OK) {
            if (result == null)
                result = new LinkedHashMap<>();

            for(Iterator<Map.Entry<String, Cmd.Topic>> it = result.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Cmd.Topic> e = it.next();
                PushAccount.Topic t = new PushAccount.Topic();
                t.name = e.getKey();
                Cmd.Topic val = e.getValue();
                t.prio = val.type;
                t.jsSrc = val.script;
                tmpRes.add(t);
            }
            Collections.sort(tmpRes, new PushAccount.TopicComparator());

            mPushAccount.topics = tmpRes;
        } else {
            if (mCmd == Cmd.CMD_DEL_TOPICS && requestStatus == Cmd.RC_OK && mPushAccount.topics != null) {
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
    public LinkedHashMap<String, Cmd.Topic> mTopics;
    public List<String> mDelTopics;
}
