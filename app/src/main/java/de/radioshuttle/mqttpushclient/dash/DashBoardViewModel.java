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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.MqttTopic;
import de.radioshuttle.utils.Utils;

public class DashBoardViewModel extends AndroidViewModel {

    public MutableLiveData<List<Item>> mDashBoardItemsLiveData; // new and edited items

    public DashBoardViewModel(PushAccount account, Application app) {
        super(app);
        mPushAccount = account;
        mDashBoardItemsLiveData = new MutableLiveData<>();
        mGroups = new LinkedList<>();
        mItemsPerGroup = new HashMap<>();
        mModificationDate = 0L;
    }

    public void startJavaScriptExecutors() {
        if (mReceivedMsgExecutor == null) {
            mReceivedMsgExecutor = new JavaScriptExcecutor();

            // Test data start
            mTestDataThread = DBUtils.testDataThread(this);
            mTestDataThread.start();
            // Test data end
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mTestDataThread != null) {
            mTestDataThread.interrupt();
        }
        if (mReceivedMsgExecutor != null) {
            mReceivedMsgExecutor.shutdown();
        }
    }

    @MainThread
    public void onMessageReceived(MqttMessage message) {
        if (message != null) {
            boolean updated = false;
            /* iterate over all items and check for subscribed topics */
            for(GroupItem gr : mGroups) {
                LinkedList<Item> items = mItemsPerGroup.get(gr.id);
                if (items != null && items.size() > 0) {
                    for(Item item : items) {
                        try {
                            if (!Utils.isEmpty(item.topic_s) && MqttTopic.isMatched(item.topic_s, message.getTopic())) {
                                if (Utils.isEmpty(item.script_f)) { // no javascript -> update UI
                                    String msg = "";
                                    byte[] payload = (message.getPayload() == null ? new byte[0] : message.getPayload());
                                    try {
                                        msg = new String(payload, "UTF-8");
                                    } catch(Exception e) {}
                                    // Log.d(TAG, "content: " + msg);
                                    item.data.put("msg.received", message.getWhen());
                                    item.data.put("msg.raw", payload);
                                    item.data.put("msg.content", msg);
                                    item.data.put("content", msg);
                                    updated = true;
                                } else {
                                    mReceivedMsgExecutor.executeFilterScript(item, message, new JavaScriptExcecutor.Callback() {
                                        @Override
                                        public void onFinshed(Item item, Map<String, Object> result) {
                                            /* if item reference changed, skip result (item has been replaced/edited) */
                                            ItemContext ic = getItem(item.id);
                                            if (ic != null && ic.item == item) {
                                                if (result != null && result.containsKey("content")) { //TODO: add errorMsg and additional message data to content, ...
                                                    item.data.putAll(result);
                                                    mDashBoardItemsLiveData.setValue(buildDisplayList()); // notifay observers
                                                }
                                            }
                                        }
                                    });
                                }
                            }
                        } catch(Exception e) {
                            Log.d(TAG, "onMessageReceived(): invalid args.", e);
                            // invalid arguments
                        }
                    }
                }
                if (updated) {
                    // List<Item> livedata = mDashBoardItemsLiveData.getValue();
                    mDashBoardItemsLiveData.setValue(buildDisplayList()); // notifay observers
                }
            }

        }
    }


    public void setItems(String json, long modificationDate) {
        mInitialized = true;
        mGroups.clear();
        mItemsPerGroup.clear();
        mModificationDate = modificationDate;
        if (!Utils.isEmpty(json)) {
            try {
                DBUtils.createItemsFromJSONString(json, mGroups, mItemsPerGroup);
            } catch(Exception e) {
                Log.e(TAG, "Load items: Parsing json failed: " + e.getMessage());
            }
        }
        mDashBoardItemsLiveData.setValue(buildDisplayList());
    }

    public void saveItems() {

        long modificationDate = System.currentTimeMillis();
        try {
            JSONArray arr = DBUtils.createJSONStrFromItems(mGroups, mItemsPerGroup, true);
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

        mDashBoardItemsLiveData.setValue(buildDisplayList());
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
                //TODO: subscribe to topic. if last message cached, set last message to item, trigger filter script
                mDashBoardItemsLiveData.setValue(buildDisplayList());
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
                    groupItem.data = currentGr.data;
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
            mDashBoardItemsLiveData.setValue(buildDisplayList());
            saveItems();
        }
    }

    public void setItem(int groupIdx, int itemPos, Item item) {

        if (groupIdx >= 0 && groupIdx < mGroups.size()) {
            GroupItem group = mGroups.get(groupIdx);
            Item replacedItem = null;
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
                            item.data = currentItem.data;
                            replacedItem = items.set(itemPos, item); // replace
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
                                    replacedItem = items.remove(i);
                                    break;
                                }
                            }
                        }
                    }
                    //TODO: topic, javascript might have changed: subscribe and/or unsubscribe, set new content / retrigger JavaScript
                    if (replacedItem != null) {
                    }
                    mDashBoardItemsLiveData.setValue(buildDisplayList());
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
                            //TODO: remove js
                            if (mReceivedMsgExecutor != null) {
                                mReceivedMsgExecutor.remove(item.id);
                            }
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
            mDashBoardItemsLiveData.setValue(buildDisplayList());
            saveItems();
        } else if (selectedItems == null) { // delete all
            mGroups.clear();
            mItemsPerGroup.clear();
            mDashBoardItemsLiveData.setValue(buildDisplayList());
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

    private Thread mTestDataThread; //TODO: remove after test
    private JavaScriptExcecutor mReceivedMsgExecutor;

    private boolean mInitialized;
    private long mModificationDate;
    private PushAccount mPushAccount;

    private LinkedList<GroupItem> mGroups;
    private HashMap<Integer, LinkedList<Item>> mItemsPerGroup;
    // private HashSet<String> mSubscribedTopics;


    public final static String TAG = DashBoardViewModel.class.getSimpleName();
}
