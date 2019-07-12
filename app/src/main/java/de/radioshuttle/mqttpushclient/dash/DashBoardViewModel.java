/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.Code;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.PublishRequest;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.MqttUtils;
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
        mPublishRequest = new MutableLiveData<>();
        mCachedMessages = new MutableLiveData<>();
        saveRequestCnt = 0;
        syncRequestCnt = 0;
        currentSyncRequest = null;
        currentSaveRequest = null;
        mMaxID = 0;
        mTimer = Executors.newScheduledThreadPool(1);
        mRequestExecutor = Utils.newSingleThreadPool();
    }

    public void startJavaScriptExecutors() {
        if (mJavaScriptExecutor == null) {
            mJavaScriptExecutor = new JavaScriptExcecutor(mPushAccount, mApplication);

            // Test data start
            /*
            mTestDataThread = DBUtils.testDataThread(this);
            mTestDataThread.start();
            */
            // Test data end
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mTestDataThread != null) {
            mTestDataThread.interrupt();
        }
        if (mJavaScriptExecutor != null) {
            mJavaScriptExecutor.shutdown();
        }

        if (mGetMessagesTask != null) {
            mGetMessagesTask.cancel(true);
        }
        if (mTimer != null) {
            mTimer.shutdown();
        }
        if (mRequestExecutor != null) {
            mRequestExecutor.shutdown();
        }

    }

    @MainThread
    public void onMessageReceived(Message message) {
        if (message != null) {
            boolean updated = false;
            /* iterate over all items and check for subscribed topics */
            for(GroupItem gr : mGroups) {
                LinkedList<Item> items = mItemsPerGroup.get(gr.id);
                if (items != null && items.size() > 0) {
                    for(Item item : items) {
                        try {
                            if (!Utils.isEmpty(item.topic_s) && message.filter.equals(item.topic_s)) {
                                item.data.put("sub_topic_stat", message.status);
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
                                    item.data.remove("error"); // remove previous set error
                                    item.updateUIContent(getApplication());
                                    updated = true;
                                } else {
                                    // Log.d(TAG, "onMessageReceived: " + item.label + " " + message.getTopic() + " " + new Date(message.getWhen()) + " " + new String(message.getPayload()));
                                    mJavaScriptExecutor.executeFilterScript(item, message, new JavaScriptExcecutor.Callback() {
                                        @Override
                                        public void onFinshed(Item item, Map<String, Object> result) {
                                            /* if item reference changed, skip result (item has been replaced/edited) */
                                            ItemContext ic = getItem(item.id);
                                            if (ic != null && ic.item == item) {
                                                if (result != null) {
                                                    String publishError = (String) item.data.get("error2");
                                                    item.data.clear(); // clear old content, erros except publish error
                                                    if (!Utils.isEmpty(publishError)) {
                                                        item.data.put("error2", publishError);
                                                    }
                                                    item.data.putAll(result);
                                                    item.updateUIContent(getApplication());
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

    // set published message
    public void onMessagePublished(Message pm) {

        if (Utils.isEmpty(pm.getTopic())) {
            return;
        }
        /* iterate over all items and check for subscribed topics */
        for(GroupItem gr : mGroups) {
            LinkedList<Item> items = mItemsPerGroup.get(gr.id);
            if (items != null && items.size() > 0) {
                for (Item item : items) {
                    try {
                        if (!Utils.isEmpty(item.topic_s) && MqttUtils.topicIsMatched(item.topic_s, pm.getTopic())) {
                            Message m = new Message();
                            m.filter = item.topic_s;
                            m.status = 0; //TODO: ?
                            m.setTopic(pm.getTopic());
                            m.setWhen(pm.getWhen());
                            m.setPayload(pm.getPayload());
                            onMessageReceived(m);
                        }
                    } catch(Exception e) {
                        Log.e(TAG, "onMessagePublished(): error validating topic");
                    }
                }
            }
        }
    }

    // public void save


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

    public void notifyDataChanged() {
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
        saveRequestCnt++;
        DashboardRequest request = new DashboardRequest(mApplication, mPushAccount, mSaveRequest, mModificationDate);
        request.saveDashboard(data, itemID);
        currentSaveRequest = request;
        request.executeOnExecutor(mRequestExecutor);
    }

    public void loadMessages() {
        syncRequestCnt++;
        // Log.d(TAG, "loadMessages: " + mModificationDate);
        DashboardRequest request = new DashboardRequest(mApplication, mPushAccount, mSyncRequest, mModificationDate);
        currentSyncRequest = request;
        request.executeOnExecutor(mRequestExecutor);
    }

    public long publish(final String topic, final byte[] payload, final boolean retain, final Item originator) {
        final PublishRequest publish = new PublishRequest(mApplication, mPushAccount, mPublishRequest);
        publish.setMessage(topic, payload, retain, originator.id);

        if (originator instanceof ProgressItem) {
            mJavaScriptExecutor.setThrottleOutputScriptExecution(true);
        }

        mJavaScriptExecutor.executeOutputScript(originator, topic, payload, retain,
                new JavaScriptExcecutor.Callback() {
                    @Override
                    public void onFinshed(Item item, Map<String, Object> result) {
                        if(result == null || result.get("error") instanceof String) {
                            String errMsg = (String) result.get("error");
                            originator.outputScriptError = errMsg;
                            publish.outputScriptError = errMsg;
                            publish.setCompleted(true);
                            // mDashBoardItemsLiveData.setValue(buildDisplayList()); // notifay observers (to show dashboard item error image)
                            mPublishRequest.setValue(publish); // notify observers (to hide dialog progress bar)
                        } else {
                            if (!Utils.isEmpty(topic)) {
                                /* overrited payload with msg.raw (modified payload by javascript) */
                                publish.setMessage(topic, (byte[]) result.get("msg.raw"), retain, originator.id);
                                publish.executeOnExecutor(mRequestExecutor, (Void[]) null);
                            } else {
                                /* if topic was empty, only Javascript has been executed */
                                publish.setCompleted(true);
                                mPublishRequest.setValue(publish); // notify observers (e. g. dialog progress bar)
                            }
                        }
                    }
                });
        return publish.getmPublishID();
    }

    public void startGetMessagesTimer() {

        if (mGetMessagesTask == null || mGetMessagesTask.isCancelled()) {
            final Handler uiHandler = new Handler(Looper.getMainLooper());
            Log.d(TAG, "startGetMessagesTimer() started." );

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
    }

    public void stopGetMessagesTimer() {
        if (mGetMessagesTask != null) {
            mGetMessagesTask.cancel(false);
        }
    }

    public void loadLastReceivedMessages() {
        final String pushServerID = mPushAccount.pushserverID;
        final String mqttAccount = mPushAccount.getMqttAccountName();
        final Application app = getApplication();
        Log.d(TAG, "Load messages: " + pushServerID + ", " + mqttAccount);
        if (!Utils.isEmpty(pushServerID) && !Utils.isEmpty(mqttAccount)) {
            Utils.executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ArrayList<Message> result = new ArrayList<>();
                        AppDatabase db = AppDatabase.getInstance(app);
                        MqttMessageDao dao = db.mqttMessageDao();
                        Long psid = dao.getCode(pushServerID);
                        Long accountID = dao.getCode(mqttAccount);
                        if (psid != null && accountID != null) {
                            File msgsD = new File(app.getFilesDir(), "mc_" + psid + "_" + accountID + ".json");
                            synchronized (mFileLock) {
                                BufferedReader bufferedReader = null;
                                JsonReader jsonReader = null;
                                Message m;
                                try {
                                    if (msgsD.exists()) {
                                        bufferedReader  = new BufferedReader(new InputStreamReader(new FileInputStream(msgsD), "UTF-8"));
                                        jsonReader = new JsonReader(bufferedReader);
                                        String name;
                                        jsonReader.beginArray();
                                        while(jsonReader.hasNext()) {
                                            m = new Message();
                                            jsonReader.beginObject();
                                            while(jsonReader.hasNext()) {
                                                name = jsonReader.nextName();
                                                if (name.equals("filter")) {
                                                    m.filter = jsonReader.nextString();
                                                } else if (name.equals("status")) {
                                                    m.status = jsonReader.nextInt();
                                                } else if (name.equals("received")) {
                                                    m.setWhen(jsonReader.nextLong());
                                                } else if (name.equals("seqno")) {
                                                    m.setSeqno(jsonReader.nextInt());
                                                } else if (name.equals("topic")) {
                                                    m.setTopic(jsonReader.nextString());
                                                } else if (name.equals("payload")) {
                                                    m.setPayload(Base64.decode(jsonReader.nextString(), Base64.DEFAULT));
                                                } else {
                                                    jsonReader.skipValue();
                                                }
                                            }
                                            jsonReader.endObject();
                                            // Log.d(TAG, "message read: " + m.filter + ", " + m.status + ", " + m.getTopic() + ", " + new String(m.getPayload()) + ", " +m.getWhen() + ", " + m.getSeqno());
                                            result.add(m);
                                        }
                                        jsonReader.endArray();
                                    }
                                } finally {
                                    if (jsonReader != null) {
                                        jsonReader.close();
                                    }
                                }
                                if (result.size() > 0) {
                                    mCachedMessages.postValue(result);
                                }
                            }
                        }
                    } catch(Exception e) {
                        Log.e(TAG, "Error loading messages for account: " + pushServerID + ", " + mqttAccount, e);
                    }
                }
            });
        }
    }

    public void saveLastReceivedMessages() {
        if (mLastReceivedMessages != null && mLastReceivedMessages.size() > 0) {
            final ArrayList<Message> msgArr = new ArrayList<>(mLastReceivedMessages);
            final String pushServerID = mPushAccount.pushserverID;
            final String mqttAccount = mPushAccount.getMqttAccountName();
            final Application app = getApplication();
            Log.d(TAG, "Save messages: " + pushServerID + ", " + mqttAccount);
            if (!Utils.isEmpty(pushServerID) && !Utils.isEmpty(mqttAccount)) {
                Utils.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AppDatabase db = AppDatabase.getInstance(app);
                            MqttMessageDao dao = db.mqttMessageDao();
                            Long psid = dao.getCode(pushServerID);
                            if (psid == null) {
                                Code c = new Code();
                                c.setName(pushServerID);
                                try {
                                    psid = db.mqttMessageDao().insertCode(c);
                                } catch (SQLiteConstraintException e) {
                                    // rare case: sync messages has just inserted this pushServerID
                                    Log.d(TAG, "insert pushserver id into code table: ", e);
                                    psid = db.mqttMessageDao().getCode(pushServerID); // reread
                                }
                                // Log.d(TAG, " (before null) code: " + psCode);
                            }
                            Long accountID = dao.getCode(mqttAccount);
                            if (accountID == null) {
                                Code c = new Code();
                                c.setName(mqttAccount);
                                try {
                                    accountID = db.mqttMessageDao().insertCode(c);
                                } catch (SQLiteConstraintException e) {
                                    // rare case: sync messages has just inserted this accountname
                                    Log.d(TAG, "insert accountname into code table: ", e);
                                    accountID = db.mqttMessageDao().getCode(mqttAccount);
                                }
                                // Log.d(TAG, " (before null) mqttAccountCode: " + mqttAccountCode);
                            }
                            File msgs = new File(app.getFilesDir(), "mc_" + psid + "_" + accountID + ".tmp");
                            File msgsD = new File(app.getFilesDir(), "mc_" + psid + "_" + accountID + ".json");
                            BufferedWriter bufferedWriter = null;
                            JsonWriter jsonWriter = null;
                            boolean writeCompleted = false;
                            synchronized (mFileLock) {
                                try {
                                    bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(msgs), "UTF-8"));
                                    jsonWriter = new JsonWriter(bufferedWriter);
                                    jsonWriter.beginArray();
                                    for(Message m : msgArr) {
                                        jsonWriter.beginObject();
                                        jsonWriter.name("filter");
                                        jsonWriter.value(m.filter);
                                        jsonWriter.name("status");
                                        jsonWriter.value(m.status);
                                        jsonWriter.name("received");
                                        jsonWriter.value(m.getWhen());
                                        jsonWriter.name("seqno");
                                        jsonWriter.value(m.getSeqno());
                                        jsonWriter.name("topic");
                                        jsonWriter.value(m.getTopic() == null ? "" : m.getTopic());
                                        jsonWriter.name("payload");
                                        jsonWriter.value(Base64.encodeToString(m.getPayload(), Base64.DEFAULT));
                                        jsonWriter.endObject();
                                    }
                                    jsonWriter.endArray();
                                    writeCompleted = true;
                                } finally {
                                    if (jsonWriter != null) {
                                        jsonWriter.close();
                                    }
                                    if (writeCompleted) {
                                        msgs.renameTo(msgsD);
                                    }
                                    msgs.delete();
                                }
                            }
                        } catch(Exception e) {
                            Log.e(TAG, "Error saving messages for account: " + pushServerID + ", " + mqttAccount, e);
                        }
                    }
                });
            }
        }
    }
    private final Object mFileLock = new Object();


    public String getItemsRaw() {
        return mItemsRaw;
    }

    public long getItemsVersion() {
        return mModificationDate;
    }

    public boolean isInitialized() {
        return mInitialized;
    }

    public boolean isSaveRequestActive() {
        return saveRequestCnt > 0;
    }

    public void confirmSaveResultDelivered() {
        saveRequestCnt = 0;
    }

    public boolean isCurrentSaveRequest(Request request) {
        return currentSaveRequest == request;
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

    public void setLastReceivedMessages(List<Message> messages) {
        mLastReceivedMessages = messages;
    }

    public List<Message> getLastReceivedMessages() {
        return mLastReceivedMessages;
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
    protected JavaScriptExcecutor mJavaScriptExecutor;
    private ScheduledExecutorService mTimer;
    private ScheduledFuture<?> mGetMessagesTask;

    private ThreadPoolExecutor mRequestExecutor;

    private boolean mInitialized;
    private String mItemsRaw;
    private long mModificationDate;
    private PushAccount mPushAccount;

    private LinkedList<GroupItem> mGroups;
    private HashMap<Integer, LinkedList<Item>> mItemsPerGroup;
    private List<Message> mLastReceivedMessages;

    /* observers */
    public MutableLiveData<Request> mSaveRequest;
    public MutableLiveData<Request> mSyncRequest;
    public MutableLiveData<Request> mPublishRequest;
    public MutableLiveData<List<Message>> mCachedMessages;

    private int saveRequestCnt;
    private int syncRequestCnt;

    private Request currentSaveRequest;
    private Request currentSyncRequest;

    public final static String TAG = DashBoardViewModel.class.getSimpleName();
}
