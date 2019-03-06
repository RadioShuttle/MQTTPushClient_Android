/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.radioshuttle.mqttpushclient.PushAccount;

public class DashBoardViewModel extends ViewModel {

    public MutableLiveData<List<Item>> dashBoardItemsLiveData;

    public DashBoardViewModel() {
        dashBoardItemsLiveData = new MutableLiveData<>();
        mItems = new ArrayList<>();
    }

    public void setPushAccount(PushAccount pushAccount) {
        mPushAccount = pushAccount;
    }

    public PushAccount getPushAccount() {
        return mPushAccount;
    }

    public void addItem(Item item) {
        if (item != null) {
            mItems.add(item);
            Collections.sort(mItems, new Item.Comparator());
            ArrayList<Item> tmp = new ArrayList<>();


            ArrayList<Item> uiList = new ArrayList<>(); // build a new list for UI
            for(int i = 0; i < mItems.size(); i++) {
                // if a header does not exists, add group
                if (mItems.get(i).getType() != Item.TYPE_HEADER && ((i == 0) || mItems.get(i-1).groupIdx != mItems.get(i).groupIdx)) {
                    Item header = new HeaderItem();
                    header.groupIdx = mItems.get(i).groupIdx;
                    header.orderInGroup = Integer.MIN_VALUE; // always first pos
                    header.label = "Header " + (header.groupIdx + 0); //TODO 1
                    uiList.add(header);
                    tmp.add(header);
                }
                uiList.add(mItems.get(i));
            }
            mItems.addAll(tmp);
            dashBoardItemsLiveData.setValue(uiList);
        }
    }

    private ArrayList<Item> mItems;
    private PushAccount mPushAccount;

    private final static String TAG = DashBoardViewModel.class.getSimpleName();
}
