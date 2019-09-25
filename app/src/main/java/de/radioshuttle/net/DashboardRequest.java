/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
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
import java.util.List;

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

    public DashboardRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData, long localVersion) {
        super(context, pushAccount, accountLiveData);
        mGetTopicFilterScripts = false; // disable getTopics in super class
        mLocalVersion = localVersion;
        invalidVersion = false;
        mStoreDashboardLocally = false;
        mReceivedDashboard = null;
        mCurrentDashboard =ViewState.getInstance(context).getDashBoardContent(pushAccount.getKey());
        mDeleteFiles = new ArrayList<>();
        mReceivedResources = false;
    }

    public void saveDashboard(JSONObject dashboard, int itemID) {
        mCmd = Cmd.CMD_SET_DASHBOARD;
        mDashboardPara = dashboard;
        mItemIDPara = itemID;
    }

    protected void saveImportedResources() throws IOException, ServerError, JSONException {
        /*
         * Interate over all elemnts to find imported file uris
         */
        if (mDashboardPara != null) {
            JSONArray groupArray = mDashboardPara.getJSONArray("groups");
            JSONObject groupJSON, itemJSON;
            for (int i = 0; i < groupArray.length(); i++) {
                groupJSON = groupArray.getJSONObject(i);
                JSONArray itemArray = groupJSON.getJSONArray("items");

                String encodedFilename;
                String cleanFilename; // decoded filename
                File importedFile;
                String finalResourceName;
                String uri, uri2;

                for (int j = 0; j < itemArray.length(); j++) {
                    itemJSON = itemArray.getJSONObject(j);

                    uri = itemJSON.optString("uri");
                    uri2 = itemJSON.optString("uri2");
                    if (ImageResource.isImportedResource(uri)) {
                        Log.d(TAG, "Save image on server: " + uri);
                        encodedFilename = ImageResource.getURIPath(uri);

                        cleanFilename = ImageResource.removeImportedFilePrefix(encodedFilename);
                        cleanFilename = ImageResource.removeExtension(cleanFilename);
                        cleanFilename = ImageResource.decodeFilename(cleanFilename);

                        importedFile = new File(ImportFiles.getImportedFilesDir(mAppContext), encodedFilename);

                        finalResourceName = mConnection.addResource(cleanFilename, Cmd.DASH512_PNG, importedFile);
                        if (mConnection.lastReturnCode != Cmd.RC_OK) {
                            throw new ServerError(0, mAppContext.getString(R.string.error_send_image_invalid_args));
                        }

                        /* move imported file to user file dir and rename it */
                        encodedFilename = ImageResource.encodeFilename(finalResourceName) + '.' + Cmd.DASH512_PNG;

                        File userDir = ImportFiles.getUserFilesDir(mAppContext);
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

                        itemJSON.putOpt("uri", ImageResource.buildUserResourceURI(finalResourceName));
                        if (uri.equals(uri2)) {
                            itemJSON.putOpt("uri2", ImageResource.buildUserResourceURI(finalResourceName));
                            continue;
                        }
                        Log.d(TAG, "Saved image on server: " + finalResourceName);
                    }
                    //TODO: check if user resource exists on server

                    if (ImageResource.isImportedResource(uri2)) {
                        Log.d(TAG, "Save image2 on server: " + uri2);
                        encodedFilename = ImageResource.getURIPath(uri2);

                        cleanFilename = ImageResource.removeImportedFilePrefix(encodedFilename);
                        cleanFilename = ImageResource.removeExtension(cleanFilename);
                        cleanFilename = ImageResource.decodeFilename(cleanFilename);

                        importedFile = new File(ImportFiles.getImportedFilesDir(mAppContext), encodedFilename);

                        finalResourceName = mConnection.addResource(cleanFilename, Cmd.DASH512_PNG, importedFile);
                        if (mConnection.lastReturnCode != Cmd.RC_OK) {
                            throw new ServerError(0, mAppContext.getString(R.string.error_send_image_invalid_args));
                        }

                        /* move imported file to user file dir and rename it */
                        encodedFilename = ImageResource.encodeFilename(finalResourceName) + '.' + Cmd.DASH512_PNG;

                        File userDir = ImportFiles.getUserFilesDir(mAppContext);
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
                        itemJSON.putOpt("uri2", ImageResource.buildUserResourceURI(finalResourceName));

                        Log.d(TAG, "Saved image2 on server: " + finalResourceName);
                    }
                    //TODO: check if user resource exists on server

                }
            }
        }
    }

    @Override
    public boolean perform() throws Exception {

        if (mCmd == Cmd.CMD_SET_DASHBOARD) {
            try {
                /* if images where added, they must be saved first -> check all resource uris */
                try {
                    saveImportedResources();
                } catch(ServerError e) {
                    String msg = mAppContext.getString(R.string.error_send_image_prefix);
                    msg += ' ' +  e.getMessage();
                    throw new ServerError(0, msg);
                }
                // deleteNonReferencedResorces();

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
                //TODO: set since
                mServerVersion = mConnection.getCachedMessagesDash(0, 0, result);
                mReceivedMessages = new ArrayList<>();

                Message mqttMessage;
                for(int i = 0; i < result.size(); i++) {
                    mqttMessage = new Message();
                    mqttMessage.setWhen((Long) result.get(i)[0] * 1000L);
                    mqttMessage.setTopic((String) result.get(i)[1]);
                    mqttMessage.setPayload((byte[]) result.get(i)[2]);
                    mqttMessage.setSeqno((Integer) result.get(i)[3]);
                    mqttMessage.status = (Short) result.get(i)[4];
                    mqttMessage.filter = (String) result.get(i)[5];
                    mReceivedMessages.add(mqttMessage);
                }
                //TODO save cached messages locally

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
                String uri, uri2; File f;
                HeliosUTF8Encoder enc = new HeliosUTF8Encoder();
                String resourceName;
                String internalFileName;
                File localDir = ImportFiles.getUserFilesDir(mAppContext);
                ArrayList<String> resourceNames = new ArrayList<>();
                if (!Utils.isEmpty(currentDash)) {
                    JSONObject jsonObject = new JSONObject(currentDash);
                    JSONArray groupArray = jsonObject.getJSONArray("groups");
                    JSONObject groupJSON, itemJSON;
                    for (int i = 0; i < groupArray.length(); i++) {
                        groupJSON = groupArray.getJSONObject(i);
                        JSONArray itemArray = groupJSON.getJSONArray("items");

                        for (int j = 0; j < itemArray.length(); j++) {
                            try {
                                itemJSON = itemArray.getJSONObject(j);
                                uri = itemJSON.optString("uri");
                                uri2 = itemJSON.optString("uri2");
                                if (ImageResource.isUserResource(uri)) {
                                    resourceName = ImageResource.getURIPath(uri);
                                    internalFileName = enc.format(resourceName) + '.' + Cmd.DASH512_PNG;
                                    f = new File(localDir, internalFileName);
                                    if (!f.exists()) {
                                        resourceNames.add(resourceName);
                                    }
                                }
                                if (ImageResource.isUserResource(uri2)) {
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
                    DataInputStream is = null;
                    for(String r : resourceNames) {
                        /* resource errors are not handled as long as there are not caused by an IO error */
                        // Log.d(TAG, "missing local resources: " + resourceNames);
                        int len = 0;
                        try {
                            is = mConnection.getResource(r, Cmd.DASH512_PNG);
                            if (mConnection.lastReturnCode == Cmd.RC_INVALID_ARGS) {
                                // Log.d(TAG, "Requested resource does not exist: " + r);
                                break;
                            }
                            if (is != null) {
                                len = is.readInt();
                                if (ImportFiles.lowInternalMemory(mAppContext, len)) {
                                    Log.d(TAG, "low mem, skipping get resource file " + r);
                                    continue;
                                }
                                String tmpFilename = System.nanoTime() + enc.format(r)  + '.' + Cmd.DASH512_PNG;
                                File tempFile = new File(ImportFiles.getUserFilesDir(mAppContext), tmpFilename);
                                File destFile = new File(ImportFiles.getUserFilesDir(mAppContext), enc.format(r)  + '.' + Cmd.DASH512_PNG);
                                FileOutputStream fos = null;
                                try {
                                    fos = new FileOutputStream(tempFile);
                                    int total = 0, read;
                                    byte[] buffer = new byte[Cmd.BUFFER_SIZE];
                                    while(total < len && (read = is.read(buffer, 0, Math.min(len - total, buffer.length))) != -1) {
                                        total += read;
                                        fos.write(buffer, 0, read);
                                    }
                                } finally {
                                    if (fos != null) {
                                        try {fos.close(); } catch(IOException io) {}
                                    }
                                    if (tempFile.exists()) {
                                        if (!tempFile.renameTo(destFile)) {
                                            Log.d(TAG, "Error renaming file: " + tempFile.getName());
                                            tempFile.delete();
                                        } else {
                                            mReceivedResources = true;
                                            Log.d(TAG, "Loaded resource from server: " + r + ", " + destFile.getName());
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

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    List<File> mDeleteFiles;
    List<Message> mReceivedMessages;

    boolean mSaved;
    boolean invalidVersion;
    int mItemIDPara;
    JSONObject mDashboardPara;
    String mReceivedDashboard;
    String mCurrentDashboard;
    long mLocalVersion;
    long mServerVersion;
    boolean mReceivedResources;

    public int mCmd;

    protected boolean mStoreDashboardLocally; // indicates if mReceivedDashboard must be stored in onPostExecute()

    private final static String TAG = DashboardRequest.class.getSimpleName();
}
