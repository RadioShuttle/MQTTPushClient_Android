/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.util.Log;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.Utils;

public class DashBoardViewModel extends AndroidViewModel {

    public MutableLiveData<List<Item>> dashBoardItemsLiveData;

    public DashBoardViewModel(PushAccount account, Application app) {
        super(app);
        mPushAccount = account;
        dashBoardItemsLiveData = new MutableLiveData<>();
        mGroups = new LinkedList<>();
        mItemsPerGroup = new HashMap<>();
        mModificationDate = 0L;
    }

    public void setItems(String json, long modificationDate) {
        mInitialized = true;
        mGroups.clear();
        mItemsPerGroup.clear();
        mModificationDate = modificationDate;
        if (!Utils.isEmpty(json)) {
            try {
                Item.createItemsFromJSONString(json, mGroups, mItemsPerGroup);
            } catch(Exception e) {
                Log.e(TAG, "Load items: Parsing json failed: " + e.getMessage());
            }
        }
        dashBoardItemsLiveData.setValue(buildDisplayList());
    }

    public void saveItems() {

        long modificationDate = System.currentTimeMillis();
        try {
            JSONArray arr = Item.createJSONStrFromItems(mGroups, mItemsPerGroup, true);
            String localJSON = arr.toString();
            String serverJSON = null;

            if (arr != null) {
                for(int i = 0; i < arr.length(); i++) {
                    arr.getJSONObject(i).remove("id");
                    JSONArray jsonArray = arr.getJSONObject(i).optJSONArray("items");
                    if (jsonArray != null) {
                        for(int j = 0; j < jsonArray.length(); j++) {
                            jsonArray.getJSONObject(j).remove("id");
                        }
                    }
                }
                serverJSON = arr.toString();
            }
            mModificationDate = modificationDate;
            ViewState.getInstance(getApplication()).saveDashboard(mPushAccount.getKey(), mModificationDate, localJSON);

            //TODO: send to push server

        } catch(Exception e) {
            Log.e(TAG, "Save items: Parsing json failed: " + e.getMessage());
        }

    }

    public ItemContext getItem(int itemID) {
        ItemContext itemContext = null;
        Item item = null;
        GroupItem groupItem = null;
        int groupPos = 0;
        int itemPos = 0;

        Iterator<GroupItem> itGroups = mGroups.iterator();
        Item f = null;
        while(itGroups.hasNext()) {
            f = itGroups.next();
            if (f.id == itemID) {
                item = f;
                break;
            }
            itemPos++;
        }
        Iterator<Map.Entry<Integer, LinkedList<Item>>> it = mItemsPerGroup.entrySet().iterator();
        while (it.hasNext() && item == null) {
            Map.Entry<Integer, LinkedList<Item>> k = it.next();
            if (k.getValue() != null) {
                for(int j = 0; j < mGroups.size(); j++) {
                    groupPos = j;
                    if (mGroups.get(j).id == k.getKey()) {
                        groupItem = mGroups.get(j);
                        break;
                    }
                }
                itemPos = 0;
                Iterator<Item> it2 = k.getValue().iterator();
                while (it2.hasNext()) {
                    f = it2.next();
                    if (f.id == itemID) {
                        item = f;
                        break;
                    }
                    itemPos++;
                }
            }
        }
        if (item != null) {
            itemContext = new ItemContext();
            itemContext.item = item;
            itemContext.group = groupItem;
            itemContext.groupPos = groupPos;
            itemContext.itemPos = itemPos;
        }

        return itemContext;
    }

    public void addGroup(int pos, GroupItem item) {

        if (pos >= 0 && pos < mGroups.size()) {
            mGroups.add(pos, item);
            mItemsPerGroup.put(item.id, new LinkedList<Item>());
        } else {
            mGroups.add(item);
            mItemsPerGroup.put(item.id, new LinkedList<Item>());
        }

        dashBoardItemsLiveData.setValue(buildDisplayList());
        saveItems();
    }

    public void addItem(int groupIdx, int itemPos, Item item) {

        if (groupIdx >= 0 && groupIdx < mGroups.size()) {

            GroupItem group = mGroups.get(groupIdx);
            if (group != null) {
                LinkedList<Item> groupItems = mItemsPerGroup.get(group.id);
                if (groupItems == null) {
                    groupItems = new LinkedList<>();
                    mItemsPerGroup.put(group.id, groupItems);
                }
                if (itemPos >= 0 && itemPos < mItemsPerGroup.size()) {
                    groupItems.add(itemPos, item);
                } else {
                    groupItems.add(item);
                }
                dashBoardItemsLiveData.setValue(buildDisplayList());
                saveItems();
            }
        }
    }

    public void setGroup(int pos, GroupItem groupItem) {
        if (pos >= 0) {
            boolean removeOld = false;
            if (pos >= mGroups.size()) {
                pos = mGroups.size();
                mGroups.add(groupItem);
                removeOld = true;
            } else {
                GroupItem currentGr = mGroups.get(pos);
                if (currentGr.id == groupItem.id) {
                    mGroups.set(pos, groupItem); // replace
                } else {
                    mGroups.add(pos, groupItem); // insert
                    removeOld = true;
                }
            }
            if (removeOld) {
                for(int i = 0; i < mGroups.size(); i++) {
                    if (pos != i && mGroups.get(i).id == groupItem.id) {
                        mGroups.remove(i); // remove from old pos
                        break;
                    }
                }
            }
            dashBoardItemsLiveData.setValue(buildDisplayList());
            saveItems();
        }
    }

    public void setItem(int groupIdx, int itemPos, Item item) {

        if (groupIdx >= 0 && groupIdx < mGroups.size()) {
            GroupItem group = mGroups.get(groupIdx);
            if (group != null) {
                LinkedList<Item> items = mItemsPerGroup.get(group.id);
                if (items != null) {
                    ItemContext ic = null;
                    if (itemPos >= items.size()) {
                        itemPos = items.size();
                        ic = getItem(item.id);
                        items.add(item);
                    } else {
                        Item currentItem = items.get(itemPos);
                        if (currentItem.id == item.id) {
                            items.set(itemPos, item); // replace
                        } else {
                            ic = getItem(item.id);
                            items.add(itemPos, item); // insert
                        }
                    }
                    if (ic != null ) { // remove from old pos
                        items = getItems(ic.group.id);
                        if (items != null) {
                            for(int i = 0; i < items.size(); i++) {
                                if ((itemPos != i || ic.groupPos != groupIdx) && items.get(i).id == item.id) {
                                    items.remove(i);
                                    break;
                                }
                            }
                        }
                    }
                    dashBoardItemsLiveData.setValue(buildDisplayList());
                    saveItems();
                }
            }
        }
    }

    public LinkedList<GroupItem> getGroups() {
        return mGroups;
    }

    public LinkedList<Item> getItems(int groupID) {
        return mItemsPerGroup.get(groupID);
    }

    protected List<Item> buildDisplayList() {
        ArrayList<Item> list = new ArrayList<>();

        for(GroupItem gr : mGroups) {
            list.add(gr);
            LinkedList<Item> items = mItemsPerGroup.get(gr.id);
            if (items != null && items.size() > 0) {
                list.addAll(items);
            }
        }
        //TODO: raus
        /*
        Log.d(TAG, "----------------------------");
        for(Item i : list) {
            Log.d(TAG, i.label + ", ID:  " + i.id);
        }
        Log.d(TAG, "----------------------------");
        */

        return list;
    }

    public void removeItems(HashSet<Integer> selectedItems) {

        if (selectedItems != null && selectedItems.size() > 0) {
            Item item;
            boolean deleteGroup;
            Iterator<Map.Entry<Integer, LinkedList<Item>>> it = mItemsPerGroup.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, LinkedList<Item>> k = it.next();
                 deleteGroup = false;
                if (selectedItems.contains(k.getKey())) {
                    deleteGroup = true;
                }
                if (k.getValue() != null) {
                    Iterator<Item> it2 = k.getValue().iterator();
                    while (it2.hasNext()) {
                        item = it2.next();
                        if (selectedItems.contains(item.id)) {
                            it2.remove();
                        }
                    }
                    if (deleteGroup) { // delete group, if it has no entries
                        LinkedList<Item> e = mItemsPerGroup.get(k.getKey());
                        if (e == null || e.size() == 0) {
                            Iterator<GroupItem> it3 = mGroups.iterator();
                            while(it3.hasNext()) {
                                item = it3.next();
                                if (item.id == k.getKey()) {
                                    it3.remove();
                                }
                            }
                        }
                    }
                }
            }
            dashBoardItemsLiveData.setValue(buildDisplayList());
            saveItems();
        } else if (selectedItems == null) { // delete all
            mGroups.clear();
            mItemsPerGroup.clear();
            dashBoardItemsLiveData.setValue(buildDisplayList());
            saveItems();
        }
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public static class ItemContext {
        public Item item;
        public GroupItem group;
        public int itemPos;
        public int groupPos;
    }


    public PushAccount getPushAccount() {
        return mPushAccount;
    }

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(PushAccount pushAccount, Application app) {
            this.app = app;
            this.pushAccount = pushAccount;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new DashBoardViewModel(pushAccount, app);
        }

        PushAccount pushAccount;
        Application app;
    }

    private boolean mInitialized;
    private long mModificationDate;
    private PushAccount mPushAccount;

    private LinkedList<GroupItem> mGroups;
    private HashMap<Integer, LinkedList<Item>> mItemsPerGroup;


    private final static String TAG = DashBoardViewModel.class.getSimpleName();
}
