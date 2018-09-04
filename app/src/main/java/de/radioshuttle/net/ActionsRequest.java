/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.arch.lifecycle.MutableLiveData;
import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import de.radioshuttle.mqttpushclient.ActionsViewModel;
import de.radioshuttle.mqttpushclient.PushAccount;

public class ActionsRequest extends Request {
    public ActionsRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
        mActions = new ArrayList<>();
    }

    public void addAction(ActionsViewModel.Action a) {

    }

    public void deleteActions(List<String> actionNames) {

    }

    public void updateAction(ActionsViewModel.Action a, String oldName) {

    }

    @Override
    public boolean perform() throws Exception {

        requestStatus = 0; //TODO

        ActionsViewModel.Action a = new ActionsViewModel.Action();
        a.name = "Alarm On";
        a.topic = "test";
        a.content = "on";
        mActions.add(a);

        Comparator c = new Comparator<ActionsViewModel.Action>() {
            @Override
            public int compare(ActionsViewModel.Action o1, ActionsViewModel.Action o2) {
                String s1 = o1.name == null ? "" : o1.name;
                String s2 = o2.name == null ? "" : o2.name;
                return s1.compareTo(s2);
            }
        };
        Collections.sort(mActions, c);

        return true;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    public int mCmd;

    public ArrayList<ActionsViewModel.Action> mActions;

}
