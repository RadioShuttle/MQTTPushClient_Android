/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.Request;
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
        mApplication = app;
        mModificationDate = 0L;
        mSaveRequest = new MutableLiveData<>();
        mSyncRequest = new MutableLiveData<>();
        requestCnt = 0;
        syncRequestCnt = 0;
        currentSyncRequest = null;
        currentRequest = null;
        mMaxID = 0;
    }

    public void startJavaScriptExecutors() {
        if (mReceivedMsgExecutor == null) {
            mReceivedMsgExecutor = new JavaScriptExcecutor(mPushAccount, mApplication);

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

        if (mGetMessagesTask != null) {
            mGetMessagesTask.cancel(true);
        }
        if (mTimer != null) {
            mTimer.shutdown();
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
                                                if (result != null) {
                                                    item.data.clear(); // clear old content, erros
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
        mItemsRaw = json;
        mModificationDate = modificationDate;
        mMaxID = 0;
        if (!Utils.isEmpty(json)) {
            try {
                mMaxID = DBUtils.createItemsFromJSONString(json, mGroups, mItemsPerGroup);
            } catch(Exception e) {
                Log.e(TAG, "Load items: Parsing json failed: " + e.getMessage());
            }
        }
        mDashBoardItemsLiveData.setValue(buildDisplayList());
    }

    public int incrementAndGetID() {
        return ++mMaxID;
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

    public static void addGroup(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> itemsPerGroup, int pos, GroupItem item) {

        if (pos >= 0 && pos < groups.size()) {
            groups.add(pos, item);
            itemsPerGroup.put(item.id, new LinkedList<Item>());
        } else {
            groups.add(item);
            itemsPerGroup.put(item.id, new LinkedList<Item>());
        }
    }

    public static void addItem(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> itemsPerGroup, int groupIdx, int itemPos, Item item) {

        if (groupIdx >= 0 && groupIdx < groups.size()) {

            GroupItem group = groups.get(groupIdx);
            if (group != null) {
                LinkedList<Item> groupItems = itemsPerGroup.get(group.id);
                if (groupItems == null) {
                    groupItems = new LinkedList<>();
                    itemsPerGroup.put(group.id, groupItems);
                }
                if (itemPos >= 0 && itemPos < itemsPerGroup.size()) {
                    groupItems.add(itemPos, item);
                } else {
                    groupItems.add(item);
                }
            }
        }
    }

    public static void setGroup(LinkedList<GroupItem> mGroups, int pos, GroupItem groupItem) {
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
        }
    }

    public static void setItem(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> itemsPerGroup, int groupIdx, int itemPos, Item item) {

        if (groupIdx >= 0 && groupIdx < groups.size()) {
            GroupItem group = groups.get(groupIdx);
            Item replacedItem = null;
            if (group != null) {
                LinkedList<Item> items = itemsPerGroup.get(group.id);
                if (items != null) {
                    ItemContext ic = null;
                    if (itemPos >= items.size()) {
                        itemPos = items.size();
                        ic = getItem(groups, itemsPerGroup, item.id);
                        items.add(item);
                    } else {
                        Item currentItem = items.get(itemPos);
                        if (currentItem.id == item.id) {
                            item.data = currentItem.data;
                            replacedItem = items.set(itemPos, item); // replace
                        } else {
                            ic = getItem(groups, itemsPerGroup, item.id);
                            items.add(itemPos, item); // insert
                        }
                    }
                    if (ic != null ) { // remove from old pos
                        items = itemsPerGroup.get(ic.group.id);
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
                }
            }
        }
    }

    public static ItemContext getItem(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> itemsPerGroup, int itemID) {
        ItemContext itemContext = null;
        Item item = null;
        GroupItem groupItem = null;
        int groupPos = 0;
        int itemPos = 0;

        Iterator<GroupItem> itGroups = groups.iterator();
        Item f = null;
        while(itGroups.hasNext()) {
            f = itGroups.next();
            if (f.id == itemID) {
                item = f;
                break;
            }
            itemPos++;
        }
        Iterator<Map.Entry<Integer, LinkedList<Item>>> it = itemsPerGroup.entrySet().iterator();
        while (it.hasNext() && item == null) {
            Map.Entry<Integer, LinkedList<Item>> k = it.next();
            if (k.getValue() != null) {
                for(int j = 0; j < groups.size(); j++) {
                    groupPos = j;
                    if (groups.get(j).id == k.getKey()) {
                        groupItem = groups.get(j);
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

    public LinkedList<GroupItem> getGroups() {
        return mGroups;
    }

    public LinkedList<Item> getItems(int groupID) {
        return mItemsPerGroup.get(groupID);
    }

    public void copyItems(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> items) {
        if (groups != null && mGroups != null && items != null && mItemsPerGroup != null) {
            groups.addAll(mGroups);

            for(Map.Entry<Integer, LinkedList<Item>> e : mItemsPerGroup.entrySet()) {
                items.put(e.getKey(), new LinkedList<>(e.getValue()));
            }
        }
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

    public static void removeItems(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> itemsPerGroup, HashSet<Integer> selectedItems, JavaScriptExcecutor javaScriptExcecutor) {
        if (selectedItems != null && selectedItems.size() > 0) {
            Item item;
            boolean deleteGroup;
            Iterator<Map.Entry<Integer, LinkedList<Item>>> it = itemsPerGroup.entrySet().iterator();
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
                            if (javaScriptExcecutor != null) {
                                javaScriptExcecutor.remove(item.id);
                            }
                            it2.remove();
                        }
                    }
                    if (deleteGroup) { // delete group, if it has no entries
                        LinkedList<Item> e = itemsPerGroup.get(k.getKey());
                        if (e == null || e.size() == 0) {
                            Iterator<GroupItem> it3 = groups.iterator();
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
        } else if (selectedItems == null) { // delete all
            groups.clear();
            itemsPerGroup.clear();
        }
    }

    public void saveDashboard(JSONObject data, int itemID) {
        requestCnt++;
        DashboardRequest request = new DashboardRequest(mApplication, mPushAccount, mSaveRequest, mModificationDate);
        request.saveDashboard(data, itemID);
        currentRequest = request;
        request.executeOnExecutor(Utils.executor);
    }

    public void loadMessages() {
        syncRequestCnt++;
        Log.d(TAG, "loadMessages: " + mModificationDate);
        DashboardRequest request = new DashboardRequest(mApplication, mPushAccount, mSyncRequest, mModificationDate);
        currentSyncRequest = request;
        request.executeOnExecutor(Utils.executor);
    }

    public void startGetMessagesTimer() {
        mTimer = Executors.newScheduledThreadPool(1); //TODO: move
        final Handler uiHandler = new Handler(Looper.getMainLooper());

        //TODO: pause get messages task when in background
        mGetMessagesTask = mTimer.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mInitialized && !DashBoardViewModel.this.isSyncRequestActive()) {
                            loadMessages();
                        }
                    }
                });
            }
        }, 0, 5000L, TimeUnit.MILLISECONDS);
    }

    public String getItemsRaw() {
        return mItemsRaw;
    }

    public long getItemsVersion() {
        return mModificationDate;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isRequestActive() {
        return requestCnt > 0;
    }

    public void confirmResultDelivered() {
        requestCnt = 0;
    }

    public boolean isCurrentRequest(Request request) {
        return currentRequest == request;
    }

    public boolean isCurrentSyncRequest(Request request) {
        return currentSyncRequest == request;
    }

    public boolean isSyncRequestActive() {
        return syncRequestCnt > 0;
    }

    public void confirmResultDeliveredSyncRequest() {
        syncRequestCnt = 0;
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

    private int mMaxID;
    private Application mApplication;
    private Thread mTestDataThread; //TODO: remove after test
    protected JavaScriptExcecutor mReceivedMsgExecutor;
    private ScheduledExecutorService mTimer;
    private ScheduledFuture<?> mGetMessagesTask;

    private boolean mInitialized;
    private String mItemsRaw;
    private long mModificationDate;
    private PushAccount mPushAccount;

    private LinkedList<GroupItem> mGroups;
    private HashMap<Integer, LinkedList<Item>> mItemsPerGroup;
    // private HashSet<String> mSubscribedTopics;

    public MutableLiveData<Request> mSaveRequest;
    public MutableLiveData<Request> mSyncRequest;
    private int requestCnt;
    private int syncRequestCnt;
    private Request currentRequest;
    private Request currentSyncRequest;

    public final static String TAG = DashBoardViewModel.class.getSimpleName();
}
