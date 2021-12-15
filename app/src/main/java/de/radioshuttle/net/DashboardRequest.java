/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.lifecycle.MutableLiveData;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.mqttpushclient.dash.ImageResource;
import de.radioshuttle.mqttpushclient.dash.ImportFiles;
import de.radioshuttle.mqttpushclient.dash.Message;
import de.radioshuttle.mqttpushclient.dash.ViewState;
import de.radioshuttle.utils.HeliosUTF8Encoder;
import de.radioshuttle.utils.Utils;

public class DashboardRequest extends Request {

    public DashboardRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData, long localVersion,
                            long lastReceivedMsgDate, int lastReceivedMsgSeqNo) {
        super(context, pushAccount, accountLiveData);
        mGetTopicFilterScripts = false; // disable getTopics in super class
        mLocalVersion = localVersion;
        invalidVersion = false;
        mStoreDashboardLocally = false;
        mReceivedDashboard = null;
        mCurrentDashboard =ViewState.getInstance(context).getDashBoardContent(pushAccount.getKey());
        mDeleteFiles = new ArrayList<>();
        mReceivedResources = false;
        mLastReceivedMsgDate = lastReceivedMsgDate;
        mLastReceivedMsgSeqNo = lastReceivedMsgSeqNo;
    }

    public void saveDashboard(JSONObject dashboard, int itemID) {
        mCmd = Cmd.CMD_SET_DASHBOARD;
        mDashboardPara = dashboard;
        mItemIDPara = itemID;
    }

    protected void saveResource(
            String objKey,
            JSONObject jsonObject,
            HashMap<String, String> replacedImportedResources,
            HashSet<String> serverResourceSet,
            File userDir, File importDir) throws JSONException, IOException, ServerError {

        String uri = jsonObject.optString(objKey);

        if (uri != null) {
            if (replacedImportedResources.containsKey(uri)) {
                // already done for this uri
                jsonObject.putOpt(objKey, replacedImportedResources.get(uri));
            } else if (ImageResource.isImportedResource(uri)) {
                Log.d(TAG, "Save image on server (background_uri): " + uri);

                String finalResourceName = addImportedResource(importDir, userDir, uri);

                Log.d(TAG, "Saved image on server: " + finalResourceName);
                String tmp = ImageResource.buildUserResourceURI(finalResourceName);
                jsonObject.putOpt(objKey, tmp);
                replacedImportedResources.put(uri, tmp);
            } else if (ImageResource.isUserResource(uri)) {
                /* maybe user has chosen an image from another account (which are locally stored in same dir)
                 * if so, add to account resources */
                String finalResourceName = addUserResource(userDir, serverResourceSet, uri);
                if (finalResourceName != null) {
                    Log.d(TAG, "Saved image on server (cloned image from other account): " + finalResourceName);
                    String tmp = ImageResource.buildUserResourceURI(finalResourceName);
                    jsonObject.putOpt(objKey, tmp);
                    replacedImportedResources.put(uri, tmp);
                }
            }
        }

    }

    protected void saveImportedResources(List<Cmd.FileInfo> serverResourceList) throws IOException, ServerError, JSONException {

        /*
         * Interate over all elemnts to find imported file uris and save/send to server
         */
        if (!isEmptyDashboard()) {

            HashSet<String> serverResourceSet = null;
            if (serverResourceList != null) {
                serverResourceSet = new HashSet<>();
                for(Cmd.FileInfo e : serverResourceList) {
                    serverResourceSet.add(e.name);
                }
            }

            HashSet<String> lockedResources = new HashSet<>();
            JSONArray resourcesArray = mDashboardPara.optJSONArray("resources");
            String r;
            if (resourcesArray != null) {
                for(int i = 0; i < resourcesArray.length(); i++) {
                    r = resourcesArray.optString(i);
                    if (!Utils.isEmpty(r)) {
                        lockedResources.add(r);
                        Log.d(TAG, "locked res: " + r);
                    }
                }
            }
            // Log.d(TAG, "json: x: " + mDashboardPara.toString());
            mDashboardPara.remove("resources");

            HashMap<String, String> replacedImportedResources = new HashMap<>();

            JSONArray groupArray = mDashboardPara.getJSONArray("groups");
            JSONObject groupJSON, itemJSON;
            File userDir = ImportFiles.getUserFilesDir(mAppContext);
            File importDir = ImportFiles.getImportedFilesDir(mAppContext);

            for (int i = 0; i < groupArray.length(); i++) {
                groupJSON = groupArray.getJSONObject(i);
                if (!groupJSON.has("items")) {
                    continue;
                }
                JSONArray itemArray = groupJSON.getJSONArray("items");

                for (int j = 0; j < itemArray.length(); j++) {
                    itemJSON = itemArray.getJSONObject(j);

                    saveResource("uri", itemJSON, replacedImportedResources, serverResourceSet, userDir, importDir);
                    saveResource("uri_off", itemJSON, replacedImportedResources, serverResourceSet, userDir, importDir);
                    saveResource("background_uri", itemJSON, replacedImportedResources, serverResourceSet, userDir, importDir);

                    JSONArray optionListArr = itemJSON.optJSONArray("optionlist");
                    if (optionListArr != null) {
                        JSONObject jsonOption;
                        for(int z = 0; z < optionListArr.length(); z++) {
                            jsonOption = optionListArr.optJSONObject(z);
                            if (jsonOption != null) {
                                saveResource("uri", jsonOption, replacedImportedResources, serverResourceSet, userDir, importDir);
                            }
                        }
                    }
                }
            } /* end for */

            /* locked resources */
            Iterator<String> it = lockedResources.iterator();
            String uri;
            String finalResourceName;
            resourcesArray = new JSONArray();
            while(it.hasNext()) {
                uri = it.next();
                if (uri != null) {
                    if (replacedImportedResources.containsKey(uri)) {
                        resourcesArray.put(replacedImportedResources.get(uri));
                    } else if (ImageResource.isImportedResource(uri)) {
                        Log.d(TAG, "Save image on server (locked res): " + uri);

                        finalResourceName = addImportedResource(importDir, userDir, uri);

                        Log.d(TAG, "Saved image on server: " + finalResourceName);
                        resourcesArray.put(ImageResource.buildUserResourceURI(finalResourceName));
                    } else if (ImageResource.isUserResource(uri)) {
                        /* maybe user has chosen an image from another account (which are locally stored in same dir)
                         * if so, add to account resources */
                        finalResourceName = addUserResource(userDir, serverResourceSet, uri);
                        if (finalResourceName != null) {
                            Log.d(TAG, "Saved image on server (cloned image from other account, locked res): " + finalResourceName);
                            resourcesArray.put(ImageResource.buildUserResourceURI(finalResourceName));
                        } else {
                            resourcesArray.put(uri);
                        }
                        //TODO: check for URIs not refering a file (rare case)
                    }
                }

            }
            mDashboardPara.put("resources", resourcesArray);
        }
    }

    protected String addImportedResource(File importDir, File userDir, String uri) throws IOException, ServerError {
        String encodedFilename;
        String cleanFilename; // decoded filename
        File importedFile;
        String finalResourceName;

        encodedFilename = ImageResource.getURIPath(uri);

        cleanFilename = ImageResource.removeImportedFilePrefix(encodedFilename);
        cleanFilename = ImageResource.removeExtension(cleanFilename);
        cleanFilename = ImageResource.decodeFilename(cleanFilename);

        importedFile = new File(importDir, encodedFilename);

        finalResourceName = mConnection.addResource(cleanFilename, Cmd.DASH512_PNG, importedFile);
        if (mConnection.lastReturnCode != Cmd.RC_OK) {
            throw new ServerError(0, mAppContext.getString(R.string.error_send_image_invalid_args));
        }

        /* move imported file to user file dir and rename it */
        encodedFilename = ImageResource.encodeFilename(finalResourceName) + '.' + Cmd.DASH512_PNG;

        File userFile = new File(userDir, encodedFilename);
        try {
            ImportFiles.copyFile(importedFile, userFile);
        } catch(Exception e) {
            if (userFile.exists()) {
                userFile.delete();
            }
            throw e;
        }

        mDeleteFiles.add(importedFile); // delete later, save might fail
        return finalResourceName;
    }

    protected String addUserResource(File userDir, HashSet<String> serverResourceSet, String uri) throws IOException, ServerError {
        String encodedFilename;
        String cleanFilename; // decoded filename
        String finalResourceName = null;

        /* maybe user has chosen an image from another account (which are locally stored in same dir)
         * if so, add to account resources */
        cleanFilename = ImageResource.getURIPath(uri);
        encodedFilename = ImageResource.encodeFilename(cleanFilename) + '.' + Cmd.DASH512_PNG;

        File userFile = new File(userDir, encodedFilename);
        if (userFile.exists() && serverResourceSet != null && !serverResourceSet.contains(cleanFilename)) {
            finalResourceName = mConnection.addResource(cleanFilename, Cmd.DASH512_PNG, userFile);
            if (mConnection.lastReturnCode != Cmd.RC_OK) {
                throw new ServerError(0, mAppContext.getString(R.string.error_send_image_invalid_args));
            }
            /* if resource name has changed, we must create a copy of the resource */
            if (!cleanFilename.equals(finalResourceName)) {
                encodedFilename = ImageResource.encodeFilename(finalResourceName) + '.' + Cmd.DASH512_PNG;
                File target = new File(userDir, encodedFilename);
                try {
                    ImportFiles.copyFile(userFile, target);
                } catch (Exception e) {
                    if (target.exists()) {
                        target.delete();
                    }
                    throw e;
                }
            }
            Log.d(TAG, "Saved image on server (cloned image from other account): " + finalResourceName);
        }
        return finalResourceName;
    }

    @Override
    public boolean perform() throws Exception {

        if (mCmd == Cmd.CMD_SET_DASHBOARD) {
            try {

                List<Cmd.FileInfo> serverResourceList = null;
                try {
                    serverResourceList = mConnection.enumResources(Cmd.DASH512_PNG);
                    /*
                    if (serverResourceList != null) {
                        for (String s : serverResourceList) {
                            Log.d(TAG, "server entry: " + s);
                        }
                    }
                     */
                } catch(ServerError e) {
                    /* ignonre server error, which are not impotant here (connection errors will not be caught here) */
                    Log.d(TAG, "enum resources server error: " +  e.getMessage());
                }

                /* if images where added, they must be saved first -> check all resource uris */
                try {
                    saveImportedResources(serverResourceList);
                } catch(ServerError e) {
                    String msg = mAppContext.getString(R.string.error_send_image_prefix);
                    msg += ' ' +  e.getMessage();
                    throw new ServerError(0, msg);
                }

                String jsonStr = mDashboardPara.toString();

                long result = mConnection.setDashboardRequest(mLocalVersion, mItemIDPara, jsonStr);
                if (result != 0) { //Cmd.RC_OK
                    mServerVersion = result;
                    mReceivedDashboard = jsonStr;
                    mStoreDashboardLocally = true;
                    mSaved = true;
                    // delete dsf
                    for(File f : mDeleteFiles) {
                        f.delete();
                    }
                    // clean up (delete unused image resources)
                    try {
                        List<String> unusedResources = findUnusedResources(serverResourceList);
                        if (unusedResources.size() > 0) {
                            mConnection.deleteResources(unusedResources, Cmd.DASH512_PNG);
                        }
                    } catch(ServerError e) {
                        // igonre error handling for server error
                        Log.d(TAG, "Error deleting resources: " + e.getMessage());
                    }

                } else {
                    invalidVersion = true;
                }
            } catch(MQTTException e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            } catch(ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
            if (requestStatus == Cmd.RC_INVALID_ARGS) {
                requestErrorTxt = mAppContext.getString(R.string.err_invalid_topic_format);
            }

        } else {
            /* get last messages of subcribed topics and dashboard version timestamp */
            List<Object[]> result = new ArrayList<>();
            try {
                mServerVersion = mConnection.getCachedMessagesDash(mLastReceivedMsgDate / 1000L, mLastReceivedMsgSeqNo, result);
                mReceivedMessages = new ArrayList<>();
                HashMap<String, LinkedList<Message>> msgs = new HashMap<>();
                LinkedList<Message> mList;

                Message mqttMessage;
                for(int i = 0; i < result.size(); i++) {
                    mqttMessage = new Message();
                    mqttMessage.setWhen((Long) result.get(i)[0] * 1000L);
                    mqttMessage.setTopic((String) result.get(i)[1]);
                    mqttMessage.setPayload((byte[]) result.get(i)[2]);
                    mqttMessage.setSeqno((Integer) result.get(i)[3]);
                    mqttMessage.status = (Short) result.get(i)[4];

                    mList = msgs.get(mqttMessage.getTopic());
                    if (mList == null) {
                        mList = new LinkedList<>();
                        msgs.put(mqttMessage.getTopic(), mList);
                    }
                    mList.add(mqttMessage);
                    // mReceivedMessages.add(mqttMessage);

                    if (mLastReceivedMsgDate < mqttMessage.getWhen() || (mLastReceivedMsgDate == mqttMessage.getWhen()
                            && mLastReceivedMsgSeqNo < mqttMessage.getSeqno())) {
                        mLastReceivedMsgDate = mqttMessage.getWhen();
                        mLastReceivedMsgSeqNo = mqttMessage.getSeqno();
                    }
                }
                Message.AscComparator comp = new Message.AscComparator();

                for(Iterator<LinkedList<Message>> it = msgs.values().iterator(); it.hasNext();) {
                    mList = it.next();
                    if (mList.size() > 1) {
                        Collections.sort(mList, comp);
                    }
                    mReceivedMessages.add(mList.getLast());
                }
                Collections.sort(mReceivedMessages, comp);
                mHistroicalData = (msgs.size() > 0 ? msgs : null);


            } catch (ServerError e) {
                requestErrorCode = e.errorCode;
                requestErrorTxt = e.getMessage();
            }
            requestStatus = mConnection.lastReturnCode;
        }


        if (requestStatus == Cmd.RC_OK) {
            /* server version != local version: update required */
            invalidVersion = invalidVersion || (mCmd != Cmd.CMD_SET_DASHBOARD && mServerVersion > 0 && mLocalVersion != mServerVersion);

            if (invalidVersion) {
                try {
                    Object[] dash = mConnection.getDashboard();
                    if (dash != null) {
                        mServerVersion = (long) dash[0];
                        mReceivedDashboard = (String) dash[1];
                        mStoreDashboardLocally = true;
                    }
                } catch(ServerError e) {
                    requestErrorCode = e.errorCode;
                    requestErrorTxt = e.getMessage();
                }
                requestStatus = mConnection.lastReturnCode;
            }
        }

        syncImages();

        return true;
    }

    protected void syncImages() {
        try {
            /* sync image resources */
            if (requestStatus == Cmd.RC_OK) {
                String currentDash = null;
                if (!Utils.isEmpty(mReceivedDashboard)) {
                    currentDash = mReceivedDashboard;
                } else if (!Utils.isEmpty(mCurrentDashboard)) {
                    currentDash = mCurrentDashboard;
                }
                String uri, uri2, background_uri; File f;
                HeliosUTF8Encoder enc = new HeliosUTF8Encoder();
                String resourceName;
                String internalFileName;
                File localDir = ImportFiles.getUserFilesDir(mAppContext);
                ArrayList<String> resourceNames = new ArrayList<>();
                if (!Utils.isEmpty(currentDash)) {
                    JSONObject jsonObject = new JSONObject(currentDash);
                    JSONArray groupArray = jsonObject.getJSONArray("groups");
                    JSONObject groupJSON, itemJSON;
                    JSONArray resourcesArray = jsonObject.optJSONArray("resources");
                    if (resourcesArray != null) {
                        for(int i = 0; i < resourcesArray.length(); i++) {
                            try {
                                uri = resourcesArray.optString(i);
                                if (ImageResource.isUserResource(uri)) {
                                    resourceName = ImageResource.getURIPath(uri);
                                    internalFileName = enc.format(resourceName) + '.' + Cmd.DASH512_PNG;
                                    f = new File(localDir, internalFileName);
                                    if (!f.exists()) {
                                        resourceNames.add(resourceName);
                                    }
                                }
                            } catch(Exception e) {
                                Log.d(TAG, "Error checking resource names: ", e);
                            }
                        }
                    }
                    for (int i = 0; i < groupArray.length(); i++) {
                        groupJSON = groupArray.getJSONObject(i);
                        JSONArray itemArray = groupJSON.getJSONArray("items");

                        for (int j = 0; j < itemArray.length(); j++) {
                            try {
                                itemJSON = itemArray.getJSONObject(j);
                                String[] uris = new String[] {"uri", "uri_off", "background_uri"};
                                for(String u : uris) {
                                    uri = itemJSON.optString(u);
                                    if (ImageResource.isUserResource(uri)) {
                                        resourceName = ImageResource.getURIPath(uri);
                                        internalFileName = enc.format(resourceName) + '.' + Cmd.DASH512_PNG;
                                        f = new File(localDir, internalFileName);
                                        if (!f.exists()) {
                                            resourceNames.add(resourceName);
                                        }
                                    }
                                }
                                JSONArray optionListArr = itemJSON.optJSONArray("optionlist");
                                if (optionListArr != null) {
                                    JSONObject jsonOption;
                                    for(int z = 0; z < optionListArr.length(); z++) {
                                        jsonOption = optionListArr.optJSONObject(z);
                                        if (jsonOption != null) {
                                            uri = jsonOption.optString("uri");
                                            if (ImageResource.isUserResource(uri)) {
                                                resourceName = ImageResource.getURIPath(uri);
                                                internalFileName = enc.format(resourceName) + '.' + Cmd.DASH512_PNG;
                                                f = new File(localDir, internalFileName);
                                                if (!f.exists()) {
                                                    resourceNames.add(resourceName);
                                                }
                                            }
                                        }
                                    }
                                }

                            } catch(Exception e) {
                                Log.d(TAG, "Error checking resource names: ", e);
                            }
                        }
                    }

                    DataInputStream is = null;
                    for(String r : resourceNames) {
                        /* resource errors are not handled as long as there are not caused by an IO error */
                        // Log.d(TAG, "missing local resources: " + resourceNames);
                        int len = 0;
                        long mdate;
                        try {
                            is = mConnection.getResource(r, Cmd.DASH512_PNG);
                            if (mConnection.lastReturnCode == Cmd.RC_INVALID_ARGS) {
                                Log.d(TAG, "Requested resource does not exist: " + r);
                                continue;
                            }
                            if (is != null) {
                                mdate = is.readLong() * 1000L;
                                len = is.readInt();
                                if (ImportFiles.lowInternalMemory(mAppContext, len)) {
                                    Log.d(TAG, "low mem, skipping get resource file " + r);
                                    continue;
                                }
                                String tmpFilename = System.nanoTime() + enc.format(r)  + '.' + Cmd.DASH512_PNG;
                                File tempFile = new File(ImportFiles.getUserFilesDir(mAppContext), tmpFilename);
                                File destFile = new File(ImportFiles.getUserFilesDir(mAppContext), enc.format(r)  + '.' + Cmd.DASH512_PNG);
                                FileOutputStream fos = null;
                                boolean downloadCompleted = false;
                                try {
                                    fos = new FileOutputStream(tempFile);
                                    int total = 0, read;
                                    byte[] buffer = new byte[Cmd.BUFFER_SIZE];
                                    while(total < len && (read = is.read(buffer, 0, Math.min(len - total, buffer.length))) != -1) {
                                        total += read;
                                        fos.write(buffer, 0, read);
                                    }
                                    downloadCompleted = true;
                                } finally {
                                    if (fos != null) {
                                        try {fos.close(); } catch(IOException io) {}
                                    }
                                    if (tempFile.exists()) {
                                        if (!downloadCompleted) {
                                            Log.d(TAG, "Deleting tmp file. Error occured " + tempFile.getName());
                                            tempFile.delete();
                                        } else {
                                            if (!tempFile.renameTo(destFile)) {
                                                Log.d(TAG, "Error renaming file: " + tempFile.getName());
                                                tempFile.delete();
                                            } else {
                                                mReceivedResources = true;
                                                if (mdate > 0) {
                                                    destFile.setLastModified(mdate);
                                                }
                                                Log.d(TAG, "Loaded resource from server: " + r + ", " + destFile.getName());
                                            }
                                        }
                                    }
                                }
                            }
                        } catch(ServerError e) {
                            Log.d(TAG, "Error try to get resource: " + r, e);
                        }

                    }

                }
            }
        } catch(Exception e) {
            Log.d(TAG, "Error syncing images: ", e);
        }
    }

    protected List<String> findUnusedResources(List<Cmd.FileInfo> serverResourceList) throws JSONException {
        HashSet<String> unusedResources = new HashSet<>();
        if (serverResourceList != null && serverResourceList.size() > 0 && !isEmptyDashboard()) {

            for(Cmd.FileInfo e : serverResourceList) {
                unusedResources.add(e.name);
            }

            HashSet<String> usedResoureces = new HashSet<>();
            JSONArray groupArray = mDashboardPara.getJSONArray("groups");
            JSONObject groupJSON, itemJSON;
            String uri;
            for (int i = 0; i < groupArray.length(); i++) {
                groupJSON = groupArray.getJSONObject(i);
                if (groupJSON.has("items")) {
                    JSONArray itemArray = groupJSON.getJSONArray("items");
                    for (int j = 0; j < itemArray.length(); j++) {
                        itemJSON = itemArray.getJSONObject(j);
                        String[] uris = new String[] {"uri", "uri_off", "background_uri"};
                        for(String u : uris) {
                            uri = itemJSON.optString(u);
                            if (ImageResource.isUserResource(uri)) {
                                usedResoureces.add(ImageResource.getURIPath(uri));
                            }
                        }
                        JSONArray optionListArr = itemJSON.optJSONArray("optionlist");
                        if (optionListArr != null) {
                            JSONObject jsonOption;
                            for(int z = 0; z < optionListArr.length(); z++) {
                                jsonOption = optionListArr.optJSONObject(z);
                                if (jsonOption != null) {
                                    uri = jsonOption.optString("uri");
                                    if (ImageResource.isUserResource(uri)) {
                                        usedResoureces.add(ImageResource.getURIPath(uri));
                                    }
                                }
                            }
                        }

                    }
                }
            }
            JSONArray resourcesArray = mDashboardPara.optJSONArray("resources");
            if (resourcesArray != null) {
                for (int i = 0; i < resourcesArray.length(); i++) {
                    uri = resourcesArray.optString(i);
                    usedResoureces.add(ImageResource.getURIPath(uri));
                }
            }
            unusedResources.removeAll(usedResoureces);
        }
        return new ArrayList<>(unusedResources);
    }

    protected boolean isEmptyDashboard() {
        boolean empty = false;
        if (mDashboardPara == null || !mDashboardPara.has("groups")) {
            empty = true;
        } else {
            try {
                JSONArray groupArray = mDashboardPara.getJSONArray("groups");
                empty = groupArray.length() == 0;
            } catch(JSONException e) {
            }
        }
        return empty;
    }

    @Override
    protected void onPostExecute(PushAccount pushAccount) {
        mCompleted = true;
        super.onPostExecute(pushAccount);
        if (mStoreDashboardLocally) {
            ViewState.getInstance(mAppContext).saveDashboard(mPushAccount.getKey(), mServerVersion, mReceivedDashboard);
            Log.d("DashboradRequest",  "local version: " + mLocalVersion + ", server version: " + mServerVersion);
        }
    }

    // save might have success, but afterwards getCachedDashMessages() could be fail
    public boolean saveSuccesful() {
        return mSaved;
    }

    public boolean isVersionError() {
        return invalidVersion;
    }

    public boolean hasNewResourcesReceived() {
        return mReceivedResources;
    }

    /** not defined if requestStatus != Cmd.RC_OK*/
    public long getServerVersion() {
        return mServerVersion;
    }

    public String getReceivedDashboard() {
        return mReceivedDashboard;
    }

    public List<Message> getReceivedMessages() {
        return mReceivedMessages == null ? new ArrayList<Message>() : mReceivedMessages;
    }

    public long getLastReceivedMsgDate() {
        return mLastReceivedMsgDate;
    }

    public Map<String, LinkedList<Message>> getHistoricalData() {
        return mHistroicalData;
    }

    public int getLastReceivedMsgSeqNo() {
        return mLastReceivedMsgSeqNo;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    List<File> mDeleteFiles;
    private List<Message> mReceivedMessages;
    private Map<String, LinkedList<Message>> mHistroicalData;

    boolean mSaved;
    boolean invalidVersion;
    int mItemIDPara;
    JSONObject mDashboardPara;
    String mReceivedDashboard;
    String mCurrentDashboard;
    long mLocalVersion;
    long mServerVersion;
    boolean mReceivedResources;

    private long mLastReceivedMsgDate;
    private int mLastReceivedMsgSeqNo;

    public int mCmd;

    protected boolean mStoreDashboardLocally; // indicates if mReceivedDashboard must be stored in onPostExecute()

    private final static String TAG = DashboardRequest.class.getSimpleName();
}
