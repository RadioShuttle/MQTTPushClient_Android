/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
                if (mItems.get(i).getType() != Item.TYPE_HEADER && ((i == 0) || mItems.get(i - 1).groupIdx != mItems.get(i).groupIdx)) {
                    Item header = new GroupItem();
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

    public void removeItems(HashSet<Integer> selectedItems) {
        if (selectedItems != null) {
            Collections.sort(mItems, new Item.Comparator());
            Iterator<Item> it = mItems.iterator();
            HashMap<Integer, Integer> groupItemCnt = new HashMap<>();
            while(it.hasNext()) {
                Item item = it.next();

                if(item.getType() == Item.TYPE_HEADER) { // delete group later
                    groupItemCnt.put(item.groupIdx, 0);
                } else {
                    if (selectedItems.contains(item.id)) {
                        it.remove();
                    } else {
                        Integer cnt = groupItemCnt.get(item.groupIdx);
                        if (cnt != null) {
                            cnt++;
                            groupItemCnt.put(item.groupIdx, cnt);
                        }
                    }
                }
            }

            /* second run: remove groups with no childs */
            if (!selectedItems.isEmpty()) { // remove groups with no childs
                it = mItems.iterator();
                while(it.hasNext()) {
                    Item item = it.next();
                    if (selectedItems.contains(item.id) && item.getType() == Item.TYPE_HEADER) {
                        Integer cnt = groupItemCnt.get(item.groupIdx);
                        if (cnt != null && cnt == 0) {
                            it.remove();
                        }
                    }
                }
            }
            dashBoardItemsLiveData.setValue(new ArrayList<>(mItems));
        }
    }

    private ArrayList<Item> mItems;
    private PushAccount mPushAccount;

    private final static String TAG = DashBoardViewModel.class.getSimpleName();
}
