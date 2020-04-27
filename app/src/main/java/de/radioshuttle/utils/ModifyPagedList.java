/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.utils;

import android.app.Application;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.MessagesViewModel;
import de.radioshuttle.mqttpushclient.dash.DColor;

public class ModifyPagedList implements Callable {

    public ModifyPagedList(Application app, List<MqttMessage> pagedList, HashMap<String, MessagesViewModel.JSContext> javaScript, String errPrefix) {
        mStopped = new AtomicBoolean(false);
        mPagedList = pagedList;
        mJavaScript = javaScript;
        cnt = 0;
        mErrPrefix = (errPrefix == null ? "Filter script error:" : errPrefix);
        mApp = app;
    }

    @Override
    public Object call() throws Exception {
        if (mPagedList != null && mJavaScript != null) {
            MqttMessage msg;
            MessagesViewModel.JSContext jsContext;
            JavaScript interpreter = JavaScript.getInstance(mApp);
            String formatedContent;
            String orgPayload;

            for(int i = 0; i < mPagedList.size() && !mStopped.get(); i++) {
                msg = mPagedList.get(i);
                jsContext = mJavaScript.get(msg.getTopic());
                if (jsContext != null) {
                    if (jsContext.context instanceof JSInitError) {
                        orgPayload = new String(msg.getPayload(), Utils.UTF_8);
                        formatedContent = mErrPrefix + " " + ((JSInitError) jsContext.context).getMessage() + "\n" + orgPayload;
                    } else {
                        try {
                            if (jsContext.viewProps != null) {
                                jsContext.viewProps.put("background", DColor.OS_DEFAULT);
                                jsContext.viewProps.put("textcolor", DColor.OS_DEFAULT);
                            }
                            formatedContent = interpreter.formatMsg(jsContext.context, msg, 0);
                            if (formatedContent == null) {
                                formatedContent = "";
                            }
                            if (jsContext.viewProps != null && jsContext.viewProps.containsKey("background") && jsContext.viewProps.containsKey("textcolor")) {
                                msg.textColor = (Long) jsContext.viewProps.get("textcolor");
                                msg.backgroundColor = (Long) jsContext.viewProps.get("background");
                            }
                        } catch(Exception e) {
                            orgPayload = new String(msg.getPayload(), Utils.UTF_8);
                            formatedContent = mErrPrefix + " " + e.getMessage() + "\n" + orgPayload;
                        }
                    }
                    if (!mStopped.get()) {
                        msg.setPayload(formatedContent.getBytes(Utils.UTF_8));
                    }
                }
                cnt++;
            }
        }
        return null;
    }

    public int itemsProcessed() {
        return cnt;
    }

    public static class JSInitError implements JavaScript.Context {

        @Override
        public Object getInterpreter() {
            return null;
        }

        @Override
        public void close() {

        }

        public String getMessage() {
            return errorText;
        }

        public int errorCode;
        public String errorText;
    }

    // if time out occured prevent further processing
    public void stop() {
        mStopped.set(true);
    }

    private final static String TAG = ModifyPagedList.class.getSimpleName();

    protected volatile int cnt;
    protected AtomicBoolean mStopped;
    protected List<MqttMessage> mPagedList;
    protected Map<String, MessagesViewModel.JSContext> mJavaScript;
    protected String mErrPrefix;
    protected Application mApp;
}
