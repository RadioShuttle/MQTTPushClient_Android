/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.annotation.SuppressLint;
import android.app.Application;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.Code;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
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
        mLockedResources = new HashSet<>();
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
        mVersion = -1;
        mImageLoaderActive = false;
        mLastReceivedMsgDate = 0L;
        mLastReceivedMsgSeqNo = 0;
        mLastReceivedMessages = null;
        mLoadResources = false;
        mHistoricalData = new HashMap<>();
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

    public void setLoadResources(boolean load) {
        mLoadResources = load;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
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

    public long loadOptionListImages(List<OptionList.Option> optionList) {
        long requestID = -1;
        if (optionList != null && optionList.size() > 0) {
            if (mOptionListImageCache == null) {
                mOptionListImageCache = new HashMap<>();
            }
            ArrayList<String> taskList = new ArrayList<>();
            for(OptionList.Option opt : optionList) {
                if (!Utils.isEmpty(opt.imageURI) && !mOptionListImageCache.containsKey(opt.imageURI)) {
                    taskList.add(opt.imageURI);
                }
            }
            if (taskList.size() > 0) {
                mLoadOptionsCnt++;
                requestID = mLoadOptionsCnt;
                final long taskID = requestID;
                @SuppressLint("StaticFieldLeak")
                AsyncTask<List<String>, Void, Map<String, Drawable>> task =
                        new AsyncTask<List<String>, Void, Map<String, Drawable>>() {
                            @Override
                            protected Map<String, Drawable> doInBackground(List<String>... lists) {
                                HashMap<String, Drawable> result = new HashMap<>();
                                if (lists != null && lists.length > 0) {
                                    List<String> uris = lists[0];
                                    for(String uri : uris) {
                                        try {
                                            Drawable drawable;
                                            if (ImageResource.isInternalResource(uri)) {
                                                drawable = AppCompatResources.getDrawable(getApplication(), IconHelper.INTENRAL_ICONS.get(uri));
                                            } else {
                                                drawable =  ImageResource.loadExternalImage(getApplication(), mPushAccount.getAccountDirName(), uri);
                                            }
                                            result.put(uri, drawable);
                                        } catch (Exception e) {
                                            result.put(uri, null);
                                            Log.d(TAG, "Error loading image (option list): ", e);
                                        }
                                    }
                                }
                                return result;
                            }

                            @Override
                            protected void onPostExecute(Map<String, Drawable> stringDrawableMap) {
                                super.onPostExecute(stringDrawableMap);
                                mOptionListImageCache.putAll(stringDrawableMap);
                                mOptionListImageUpdate.setValue(taskID);
                            }
                        };
                task.executeOnExecutor(Utils.executor, taskList);
            }
        }
        return requestID;
    }

    public boolean isCurrentOptionImageLoadTask(long taksid) {
        return taksid == mLoadOptionsCnt;
    }

    public void configrmOptionImageTaskDelivered() {
        mLoadOptionsCnt = 0;
    }

    public Map<String, Drawable> getOptionListImageCache() {
        if (mOptionListImageCache == null) {
            mOptionListImageCache = new HashMap<>();
        }
        return mOptionListImageCache;
    }

    @MainThread
    public void onMessageReceived(Message message) {
        if (message != null) {
            // Log.d(TAG, "--> onMessageReceived: " + message.getTopic() + " " + new Date(message.getWhen()) + " " + new String(message.getPayload()));
            /* iterate over all items and check for subscribed topics */
            for(GroupItem gr : mGroups) {
                LinkedList<Item> items = mItemsPerGroup.get(gr.id);
                if (items != null && items.size() > 0) {
                    for(Item item : items) {
                        try {
                            if (!Utils.isEmpty(item.topic_s) && MqttUtils.topicIsMatched(item.topic_s, message.getTopic())) {
                                //TODO:
                                // Log.d(TAG, "onMessageReceived: " + item.label + " " + message.getTopic() + " " + new Date(message.getWhen()) + " " + new String(message.getPayload()));
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
                                    item.data.put("msg.text", msg);
                                    item.data.put("msg.topic",message.getTopic() == null ? "" : message.getTopic());
                                    item.data.put("content", msg);
                                    if (item instanceof CustomItem) {
                                      /* do not reset error of web component */ ;
                                    } else {
                                        item.data.remove("error"); // remove previous set error
                                    }
                                    checkForResourceNotFoundError(item);
                                    item.updateUIContent(getApplication());
                                    item.notifyDataChanged();
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
                                                    item.notifyDataChanged();
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
        mLockedResources.clear();
        mItemsRaw = json;
        mModificationDate = modificationDate;
        mMaxID = 0;
        if (!Utils.isEmpty(json)) {
            HashMap<String, Object> props = new HashMap<>();
            try {
                mMaxID = DBUtils.createItemsFromJSONString(json, mGroups, mItemsPerGroup, mLockedResources, props);
                if (mHistoricalData != null && mHistoricalData.size() > 0) {
                    for (GroupItem gr : mGroups) {
                        LinkedList<Item> items = mItemsPerGroup.get(gr.id);
                        if (items != null) {
                            for (Item item : items) {
                                if (item.history && !Utils.isEmpty(item.topic_s) && mHistoricalData.containsKey(item.topic_s)) {
                                    LinkedList<Message> dataList = mHistoricalData.get(item.topic_s);
                                    if (dataList != null) {
                                        item.data.put("history", dataList);
                                    }
                                }
                            }
                        }
                    }
                }

                if (props.containsKey("version")) {
                    mVersion = (Integer) props.get("version");
                }
            } catch(Exception e) {
                Log.e(TAG, "Load items: Parsing json failed: " + e.getMessage());
            }
        }
        refresh();
    }

    /** refreshes UI (does not perform a reload). for single item updates use item.notifyDataChanged() */
    public void refresh() {
        List<Item> list = buildDisplayList();
        if (mLoadResources) {
            loadImages(list);
        }
    }

    protected void loadImages(final List<Item> list) {
        if (list != null) {
            @SuppressLint("StaticFieldLeak")
            AsyncTask<List<Item>, Void, List<Item>> loadImages = new AsyncTask<List<Item>, Void, List<Item>>() {
                @Override
                protected List<Item> doInBackground(List<Item>... lists) {
                    List<Item> list = lists[0];
                    ArrayList<Item> updatedItems = new ArrayList<>();
                    boolean itemUpdated;
                    for(Item item : list) {
                        itemUpdated = false;
                        if (item != null && !Utils.isEmpty(item.background_uri)) {
                            /* check if we have the image already loaded */
                            if (!Utils.isEmpty(item.background_uri) && Utils.isEmpty(item.backgroundImageURI)) {
                                try {
                                    if (ImageResource.isInternalResource(item.background_uri)) {
                                        item.backgroundImage = AppCompatResources.getDrawable(getApplication(), IconHelper.INTENRAL_ICONS.get(item.background_uri));
                                    } else {
                                        item.backgroundImage = ImageResource.loadExternalImage(getApplication(), mPushAccount.getAccountDirName(), item.background_uri);
                                    }
                                    if (item.backgroundImage != null) {
                                        item.backgroundImageDetail = item.backgroundImage.getConstantState().newDrawable();
                                        item.backgroundImageURI = item.background_uri;
                                    }
                                    itemUpdated = true;
                                } catch(Exception e) {
                                    Log.e(TAG, "error loading image: ", e);
                                }
                            }
                        }
                        if (item instanceof Switch) {
                            Switch sw = (Switch) item;
                            /* check if we have the image already loaded */
                            if (!Utils.isEmpty(sw.uri) && Utils.isEmpty(sw.imageUri)) {
                                try {
                                    if (ImageResource.isInternalResource(sw.uri)) {
                                        sw.image = AppCompatResources.getDrawable(getApplication(), IconHelper.INTENRAL_ICONS.get(sw.uri));
                                    } else {
                                        sw.image = ImageResource.loadExternalImage(getApplication(), mPushAccount.getAccountDirName(), sw.uri);
                                    }
                                    if (sw.image != null) {
                                        sw.imageDetail = sw.image.getConstantState().newDrawable();
                                        sw.imageUri = sw.uri;
                                    }
                                    itemUpdated = true;
                                } catch(Exception e) {
                                    Log.e(TAG, "error loading image: ", e);
                                }
                            }
                            if (!Utils.isEmpty(sw.uriOff) && Utils.isEmpty(sw.imageUriOff)) {
                                try {
                                    if (ImageResource.isInternalResource(sw.uriOff)) {
                                        sw.imageOff = AppCompatResources.getDrawable(getApplication(), IconHelper.INTENRAL_ICONS.get(sw.uriOff));
                                    } else {
                                        sw.imageOff = ImageResource.loadExternalImage(getApplication(), mPushAccount.getAccountDirName(), sw.uriOff);
                                    }
                                    if (sw.imageOff != null) {
                                        sw.imageDetailOff = sw.imageOff.getConstantState().newDrawable();
                                        sw.imageUriOff = sw.uriOff;
                                    }
                                    itemUpdated = true;
                                } catch(Exception e) {
                                    Log.e(TAG, "error loading image: ", e);
                                }
                            }
                        } else if (item instanceof OptionList) {
                            OptionList ol = (OptionList) item;
                            try {
                                if (ol.optionList != null) {
                                    OptionList.Option opt;
                                    for(int i = 0; i < ol.optionList.size(); i++) {
                                        opt = ol.optionList.get(i);
                                        if (!Utils.isEmpty(opt.imageURI) && Utils.isEmpty(opt.uiImageURL)) {
                                            if (opt != null) {
                                                if (ImageResource.isInternalResource(opt.imageURI)) {
                                                    opt.uiImage = AppCompatResources.getDrawable(getApplication(), IconHelper.INTENRAL_ICONS.get(opt.imageURI));
                                                } else {
                                                    opt.uiImage = ImageResource.loadExternalImage(getApplication(), mPushAccount.getAccountDirName(), opt.imageURI);
                                                }
                                                if (opt.uiImage != null) {
                                                    opt.uiImageDetail = opt.uiImage.getConstantState().newDrawable();
                                                    opt.uiImageURL = opt.imageURI;
                                                }
                                                itemUpdated = true;
                                            }
                                        }
                                    }
                                }
                            } catch(Exception e) {
                                Log.e(TAG, "error loading image: ", e);
                            }
                        }
                        if (itemUpdated) {
                            updatedItems.add(item);
                        }
                    }
                    return updatedItems;
                }

                @Override
                protected void onPostExecute(List<Item> items) {
                    super.onPostExecute(items);
                    mImageLoaderActive = false;
                    if (items != null) {
                        for(Item item : items) {
                            checkForResourceNotFoundError(item);
                        }
                        // Log.d(TAG, "new resource loadImages, onPostExecute " + items.size());
                        mDashBoardItemsLiveData.setValue(list);
                    }
                }
            };
            mImageLoaderActive = true;
            loadImages.executeOnExecutor(Utils.executor, (List<Item>[]) new List[] {list});
        }
    }


    protected void checkForResourceNotFoundError(Item item) {
        if (mImageLoaderActive) {
            return;
        }

        boolean resourceMissing = false;

        if (item instanceof Switch) {
            Switch sw = (Switch) item;

            /* an error occured while loading the image (probably server sync was not possible due to network no availabe) */
            if (sw.image == null && !Utils.isEmpty(sw.uri)) {
                resourceMissing = true;
            }

            /* an error occured while loading the image (probably server sync was not possible due to network no availabe) */
            if (sw.imageOff == null && !Utils.isEmpty(sw.uriOff)) {
                resourceMissing = true;
            }
        }

        if (item instanceof OptionList) {
            OptionList ol = (OptionList) item;
            if (ol.optionList != null) {
                for(OptionList.Option option : ol.optionList) {
                    if (option.uiImage == null && !Utils.isEmpty(option.imageURI)) {
                        resourceMissing = true;
                        break;
                    }
                }
            }
        }

        if (item != null) {
            Log.d(TAG, "item " + item.label + ", " + item.background_uri + ", item.backgroundImage == null " + (item.backgroundImage ));
            if (item.backgroundImage == null && !Utils.isEmpty(item.background_uri)){
                resourceMissing = true;
            }

            String error = (String) item.data.get("error");
            String errorMsg = mApplication.getString(R.string.error_image_not_found);
            if (resourceMissing) {
                if (Utils.isEmpty(error)) { // only show error, if no other error occured
                    item.data.put("error", errorMsg);
                }
            } else {
                if (errorMsg.equals(error)) {
                    // no resource missing anymore -> remove message
                    item.data.remove("error"); //
                }
            }
        }
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
            if (pos < 0 || pos > mGroups.size()) {
                pos = mGroups.size();
            }
            mGroups.add(pos, groupItem);
            GroupItem item;
            for(int i = 0; i < mGroups.size(); i++) {
                item = mGroups.get(i);
                if (item.id == groupItem.id && item != groupItem) {
                    groupItem.data = item.data;
                    mGroups.remove(i);
                    break;
                }
            }
        }
    }

    public static void setItem(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> itemsPerGroup, int groupIdx, int itemPos, Item item) {

        if (groupIdx >= 0 && groupIdx < groups.size()) {
            GroupItem group = groups.get(groupIdx);
            Item replacedItem = null;
            if (group != null) {
                // get reference of edited item
                ItemContext ic = getItem(groups, itemsPerGroup, item.id);

                LinkedList<Item> items = itemsPerGroup.get(group.id);

                if (itemPos < 0 || itemPos > items.size()) {
                    itemPos = items.size();
                }
                items.add(itemPos, item);

                if (items != null) {
                    if (ic != null) {
                        item.data = ic.item.data;
                        items = itemsPerGroup.get(ic.group.id);
                        if (items != null) {
                            Item ele;
                            for(int i = 0; i < items.size(); i++) {
                                ele = items.get(i);
                                if (ele.id == item.id && ele != item) {
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

    public Set<String> getLockedResources() { return mLockedResources; }

    public void copyItems(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> items, Set<String> resources) {
        if (groups != null && mGroups != null && items != null && mItemsPerGroup != null) {
            groups.addAll(mGroups);

            for(Map.Entry<Integer, LinkedList<Item>> e : mItemsPerGroup.entrySet()) {
                items.put(e.getKey(), new LinkedList<>(e.getValue()));
            }
        }
        if (resources != null) {
            resources.addAll(mLockedResources);
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
        DashboardRequest request = new DashboardRequest(mApplication, mPushAccount, mSaveRequest,
                mModificationDate, mLastReceivedMsgDate, mLastReceivedMsgSeqNo);
        request.saveDashboard(data, itemID);
        currentSaveRequest = request;
        request.executeOnExecutor(mRequestExecutor);
    }

    public void loadMessages() {
        syncRequestCnt++;
        // Log.d(TAG, "loadMessages: " + mModificationDate);
        DashboardRequest request = new DashboardRequest(mApplication, mPushAccount, mSyncRequest,
                mModificationDate, mLastReceivedMsgDate, mLastReceivedMsgSeqNo);
        currentSyncRequest = request;

        if (!mRequestExecutor.isShutdown()) {
            request.executeOnExecutor(mRequestExecutor);
        }
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
                            originator.data.putAll(result); // view properties modified by java script
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
                                                if (name.equals("status")) {
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
            final ArrayList<Message> msgArr = new ArrayList<>(mLastReceivedMessages.values());
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

    public void setLastReceivedMessages(List<Message> messages, long lastReceivedMsgDate, int lastReceivedMsgSeqNo) {
        if (messages != null) {
            if (mLastReceivedMessages == null) {
                mLastReceivedMessages = new LinkedHashMap<>();
            }
            for(Message m : messages) {
                mLastReceivedMessages.put(m.getTopic(), m);
            }
        }
        mLastReceivedMsgDate = lastReceivedMsgDate;
        mLastReceivedMsgSeqNo = lastReceivedMsgSeqNo;
    }

    public LinkedHashMap<String, Message> getLastReceivedMessages() {
        return mLastReceivedMessages;
    }

    public void setHistoricalData(Map<String, LinkedList<Message>> historicalData) {
        if (historicalData != null && historicalData.size() > 0) {
            String topic;
            for( Map.Entry<String, LinkedList<Message>> e : historicalData.entrySet() ) {
                topic = e.getKey();
                LinkedList<Message> dataList = mHistoricalData.get(topic);
                if (dataList == null) {
                    dataList = e.getValue();
                    mHistoricalData.put(topic, dataList);
                } else {
                    synchronized (dataList) {
                        dataList.addAll(e.getValue());
                    }
                }

                synchronized (dataList) {
                    if (dataList.size() > MAX_HISTOICAL_DATA) {
                        int n = dataList.size() - MAX_HISTOICAL_DATA;
                        while(n > 0) {
                            n--;
                            if (dataList.poll() == null) {
                                break;
                            }
                        }
                    }
                }

            } // end for
            for (GroupItem gr : mGroups) {
                LinkedList<Item> items = mItemsPerGroup.get(gr.id);
                if (items != null && items.size() > 0) {
                    for (Item item : items) {
                        if (item.history && !Utils.isEmpty(item.topic_s) && historicalData.containsKey(item.topic_s)) {
                            LinkedList<Message> dataList = mHistoricalData.get(item.topic_s);
                            if (dataList != null) {
                                Object history = item.data.get("history");
                                // history might return a jsonstring (representing history in json format), then pass datalist reference
                                if (history != dataList ) {
                                    /*
                                    Log.d(TAG, "historic data: " + item.label);
                                    for(Message m : dataList) {
                                        Log.d(TAG, "historic data: " + m.getTopic() + " " + new String(m.getPayload()));
                                    }
                                     */
                                    item.data.put("history", dataList);
                                }
                            }
                        }
                    }
                }
            }

        }
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
    protected JavaScriptExcecutor mJavaScriptExecutor;
    protected int mVersion;
    private ScheduledExecutorService mTimer;
    private ScheduledFuture<?> mGetMessagesTask;
    private boolean mImageLoaderActive;

    private ThreadPoolExecutor mRequestExecutor;

    private boolean mInitialized;
    private String mItemsRaw;
    private long mModificationDate;
    private PushAccount mPushAccount;

    private LinkedList<GroupItem> mGroups;
    private HashMap<Integer, LinkedList<Item>> mItemsPerGroup;
    private HashSet<String> mLockedResources;
    private LinkedHashMap<String, Message> mLastReceivedMessages;
    protected HashMap<String, LinkedList<Message>> mHistoricalData;
    private final static int MAX_HISTOICAL_DATA = 200;

    private HashMap<String, Drawable> mOptionListImageCache;
    private int mLoadOptionsCnt;
    public MutableLiveData<Long> mOptionListImageUpdate = new MutableLiveData<>();
    boolean mLoadResources;

    /* observables */
    public MutableLiveData<Request> mSaveRequest;
    public MutableLiveData<Request> mSyncRequest;
    public MutableLiveData<Request> mPublishRequest;
    public MutableLiveData<List<Message>> mCachedMessages;

    private int saveRequestCnt;
    private int syncRequestCnt;

    private Request currentSaveRequest;
    private Request currentSyncRequest;

    private long mLastReceivedMsgDate;
    private int mLastReceivedMsgSeqNo;

    public final static String TAG = DashBoardViewModel.class.getSimpleName();
}
