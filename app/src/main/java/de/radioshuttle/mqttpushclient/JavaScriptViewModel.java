/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.app.Application;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.mqttpushclient.dash.DColor;
import de.radioshuttle.mqttpushclient.dash.DashBoardJavaScript;
import de.radioshuttle.mqttpushclient.dash.Item;
import de.radioshuttle.mqttpushclient.dash.Message;
import de.radioshuttle.utils.MqttUtils;
import de.radioshuttle.utils.Utils;
import de.radioshuttle.utils.JavaScript;

public class JavaScriptViewModel extends AndroidViewModel {

    public MutableLiveData<Request> latestMessage;
    public MutableLiveData<JSResult> javaScriptResult;
    public MutableLiveData<Boolean> runState;

    public Item mItem;

    /* used to cache test data */
    public HashMap<String, String> mContentFilterCache = new HashMap<>();
    public PushAccount mAccount;

    public boolean runJavaScript(final String souceCode) {
        boolean executed = false;
        if (javaScriptRunning.compareAndSet(false, true)) {
            executed = true;
            runState.setValue(true);
            final MqttMessage para = new MqttMessage();
            para.setPayload(mContentFilterCache.get("msg.text").getBytes(Utils.UTF_8));
            para.setTopic(mContentFilterCache.get("msg.topic"));
            para.setWhen(System.currentTimeMillis());
            final String accUser = mContentFilterCache.get("acc.user");
            final String accMqttServer = mContentFilterCache.get("acc.mqttServer");
            final String accPushServer = mContentFilterCache.get("acc.pushServer");
            final String accountDirName = mContentFilterCache.get("acc.dir");

            Utils.executor.submit(new Runnable() {
                @Override
                public void run() {
                    JSResult result = new JSResult();
                    HashMap<String, Object> contentFilterViewProps = null;
                    final DashBoardJavaScript js = DashBoardJavaScript.getInstance(getApplication());
                    final JavaScript.Context context;
                    try {
                        if (mMode == JavaScriptEditorActivity.CONTENT_OUTPUT_DASHBOARD) {
                            context = js.initSetContent(souceCode, accUser, accMqttServer , accPushServer);
                        } else {
                            context = js.initFormatter(souceCode, accUser, accMqttServer , accPushServer);
                        }

                        if (mMode == JavaScriptEditorActivity.CONTENT_FILTER_DASHBOARD || mMode == JavaScriptEditorActivity.CONTENT_OUTPUT_DASHBOARD) {
                            HashMap<String, Object> viewProperties = new HashMap<>();
                            if (mItem != null) {
                                mItem.getJSViewProperties(viewProperties);
                            }
                            js.initViewProperties(context, viewProperties, accountDirName);
                        } else if (mMode == JavaScriptEditorActivity.CONTENT_FILTER) {
                            /* javasdcript interface for textColor, backgroundColor*/
                            contentFilterViewProps = new HashMap<>();
                            contentFilterViewProps.put("background", DColor.OS_DEFAULT);
                            contentFilterViewProps.put("textcolor", DColor.OS_DEFAULT);
                            js.initMessageViewProperties(context, contentFilterViewProps);
                        }
                        try {
                            Future<JSResult> future = Utils.executor.submit(new Callable<JSResult>() {
                                @Override
                                public JSResult call() {
                                    JSResult result = new JSResult();
                                    try {
                                        if (mMode == JavaScriptEditorActivity.CONTENT_OUTPUT_DASHBOARD) {
                                            result.result = js.setContent(context, new String(para.getPayload()), para.getTopic());
                                        } else {
                                            result.result = js.formatMsg(context, para, 0);
                                        }
                                    } finally {
                                        context.close();
                                    }
                                    return result;
                                }
                            });

                            try {
                                result = future.get(JavaScript.TIMEOUT_MS, TimeUnit.MILLISECONDS);
                                if (mMode == JavaScriptEditorActivity.CONTENT_FILTER) {
                                    // colors set by script
                                    result.viewProperies = contentFilterViewProps;
                                }

                            } catch (ExecutionException e) {
                                result.code = JSResult.ERR_INTERPRETER;
                                if (e.getCause() != null) {
                                    result.errorMsg = "" + e.getCause().getMessage();
                                } else {
                                    result.errorMsg = "" + e.getMessage();
                                }
                            } catch (TimeoutException e) {
                                result.code = JSResult.ERR_TIMEOUT;
                            }
                        } finally {
                            context.close(); // context not released? do now
                        }

                    } catch(Exception e) {
                        result.code = JSResult.ERR_UNEXPECTED;;
                        result.errorMsg = "" + e.getMessage();
                    } finally {
                        javaScriptRunning.set(false);
                    }
                    javaScriptResult.postValue(result);
                    runState.postValue(false);
                }
            });
        }
        return executed;
    }

    public void loadLastReceivedMsg(final String topic) {
        requestCnt++;
        final Request request = new Request();
        request.id = requestCnt;
        request.result = null;
        currentRequest = request;

        if (mAccount != null) {
            Utils.executor.submit(new Runnable() {
                @Override
                public void run() {
                    AppDatabase db = AppDatabase.getInstance(getApplication());
                    MqttMessageDao dao = db.mqttMessageDao();
                    long psid = dao.getCode(mAccount.pushserverID);
                    long accountID = dao.getCode(mAccount.getMqttAccountName());
                    String t = (topic == null ? "" : topic);
                    if (mMode == JavaScriptEditorActivity.CONTENT_FILTER) {
                        List<MqttMessage> result = dao.loadReceivedMessagesForTopic(psid, accountID, t);
                        if (result != null && result.size() > 0) {
                            request.result = result.get(0);
                        } else {
                            request.result = null;
                        }
                    } else if (mMode == JavaScriptEditorActivity.CONTENT_FILTER_DASHBOARD) {
                        File msgsD = new File(getApplication().getFilesDir(), "mc_" + psid + "_" + accountID + ".json");
                        BufferedReader bufferedReader = null;
                        JsonReader jsonReader = null;
                        Message m;
                        try {
                            if (msgsD.exists()) {
                                bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(msgsD), "UTF-8"));
                                jsonReader = new JsonReader(bufferedReader);
                                String name;
                                jsonReader.beginArray();
                                while (jsonReader.hasNext()) {
                                    m = new Message();
                                    jsonReader.beginObject();
                                    while (jsonReader.hasNext()) {
                                        name = jsonReader.nextName();
                                        if (name.equals("received")) {
                                            m.setWhen(jsonReader.nextLong());
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
                                    if (!t.isEmpty() && MqttUtils.topicIsMatched(t, m.getTopic())) {
                                        request.result = m;
                                        break;
                                    }
                                }
                                jsonReader.endArray();
                            }
                        } catch(Exception e) {
                            Log.d(TAG,"Error reading file cached messages: " + e.getMessage());
                        } finally {
                            if (jsonReader != null) {
                                try {jsonReader.close();} catch(Exception e) {}
                            }
                        }
                    }
                    latestMessage.postValue(request);
                }
            });
        }

    }

    public JavaScriptViewModel(Application app) {
        super(app);
        latestMessage = new MutableLiveData<>();
        javaScriptResult = new MutableLiveData<>();
        runState = new MutableLiveData<>();
        currentRequest = null;
        requestCnt = 0;
        javaScriptRunning = new AtomicBoolean(false);
    }

    public void setComponentType(int scriptType) {
        mMode = scriptType;
    }

    public boolean isCurrentRequest(Request request) {
        return currentRequest == request && requestCnt > 0;
    }

    public void confirmResultDelivered() {
        requestCnt = 0;
    }

    public static class Request {
        int id;
        MqttMessage result;
    }

    public static class JSResult {
        int code; // see constants below
        String result; // filtered content
        String errorMsg; // errorMsg
        HashMap<String, Object> viewProperies;

        public static int ERR_TIMEOUT = 1;
        public static int ERR_INTERPRETER = 2;
        public static int ERR_UNEXPECTED = 3;
    }

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(Application app) {
            this.app = app;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new JavaScriptViewModel(app);
        }

        PushAccount pushAccount;
        Application app;
    }


    private AtomicBoolean javaScriptRunning;
    private Request currentRequest;
    private int requestCnt;

    private int mMode;

    private final static String TAG = JavaScriptViewModel.class.getSimpleName();
}
