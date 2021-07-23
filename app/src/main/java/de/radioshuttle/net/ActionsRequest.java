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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.radioshuttle.mqttpushclient.ActionsViewModel;
import de.radioshuttle.mqttpushclient.PushAccount;

public class ActionsRequest extends Request {
    public ActionsRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
        mActions = new ArrayList<>();
        tmpRes = new ArrayList<>();
        mHasTopics = null;
    }

    public void addAction(ActionsViewModel.Action a) {
        mCmd = Cmd.CMD_ADD_ACTION;
        mActionArg = a;
    }

    public void deleteActions(List<String> actionNames) {
        mCmd = Cmd.CMD_DEL_ACTIONS;
        mActionListArg = actionNames;
    }

    public void updateAction(ActionsViewModel.Action a) {
        mCmd = Cmd.CMD_UPD_ACTION;
        mActionArg = a;
    }

    public void publish(ActionsViewModel.Action a) {
        mCmd = Cmd.CMD_MQTT_PUBLISH;
        mActionArg = a;
    }

    @Override
    public boolean perform() throws Exception {

        if (mCmd == Cmd.CMD_DEL_ACTIONS) {
            try {
                int[] rc = mConnection.deleteActions(mActionListArg);
                //TODO: handle rc
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        } else if (mCmd == Cmd.CMD_ADD_ACTION || mCmd == Cmd.CMD_UPD_ACTION || mCmd == Cmd.CMD_MQTT_PUBLISH) {
            try {
                int[] rc;
                Cmd.Action arg = new Cmd.Action();
                arg.topic = mActionArg.topic;
                arg.content = mActionArg.content;
                arg.retain = mActionArg.retain;

                if (mCmd == Cmd.CMD_ADD_ACTION) {
                    rc = mConnection.addAction(mActionArg.name, arg);
                } else if (mCmd == Cmd.CMD_UPD_ACTION) {
                    rc = mConnection.updateAction(mActionArg.prevName, mActionArg.name, arg);
                } else {
                    rc = mConnection.publish(arg.topic, arg.content.getBytes("UTF-8"), arg.retain);
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

        LinkedHashMap<String, Cmd.Action> result = mConnection.getActions();
        if (mConnection.lastReturnCode == Cmd.RC_OK) {
            tmpRes.clear();
            for(Iterator<Map.Entry<String, Cmd.Action>> it = result.entrySet().iterator(); it.hasNext();) {
                Map.Entry<String, Cmd.Action> e = it.next();
                ActionsViewModel.Action a = new ActionsViewModel.Action();
                a.name = e.getKey();
                a.topic = e.getValue().topic;
                a.content = e.getValue().content;
                a.retain = e.getValue().retain;
                tmpRes.add(a);
            }
            Collections.sort(tmpRes, new ActionsViewModel.ActionComparator());
            mActions = tmpRes;
        } else {
            //TODO: rare case: removeActions ok, but getActions() failed. see TopicsRequest
        }

        return true;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    public int mCmd;
    public List<String> mActionListArg;
    public ActionsViewModel.Action mActionArg;

    private ArrayList<ActionsViewModel.Action> tmpRes;
    // public Boolean mHasTopics;

    /** contains the reuslt if request was successful  */
    public volatile ArrayList<ActionsViewModel.Action> mActions;

    private final static String TAG = ActionsRequest.class.getSimpleName();
}
