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
        if (!de.radioshuttle.utils.Utils.isEmpty(json)) {
            try {
                Item.createItemsFromJSONString(json, mGroups, mItemsPerGroup);
            } catch(Exception e) {
                Log.e(TAG, "Load items: Parsing json failed: " + e.getMessage());
            }
        }
        dashBoardItemsLiveData.setValue(buildDisplayList());
    }

    public void saveItems() {

        long modificationDate = System.currentTimeMillis(); //TODO: remove
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
            }
        }
        saveItems();

    }

    public List<GroupItem> getGroups() {
        return mGroups;
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
        }
    }

    public boolean isInitialized() {
        return mInitialized;
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
