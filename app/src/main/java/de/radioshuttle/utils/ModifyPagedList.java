/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import de.radioshuttle.db.MqttMessage;

public class ModifyPagedList implements Callable {

    public ModifyPagedList(List<MqttMessage> pagedList, HashMap<String, JavaScript.Context> javaScript, String errPrefix) {
        mStopped = new AtomicBoolean(false);
        mPagedList = pagedList;
        mJavaScript = javaScript;
        cnt = 0;
        mErrPrefix = (errPrefix == null ? "Filter script error:" : errPrefix);
    }

    @Override
    public Object call() throws Exception {
        if (mPagedList != null && mJavaScript != null) {
            MqttMessage msg;
            JavaScript.Context jsContext;
            JavaScript interpreter = JavaScript.getInstance();
            String formatedContent;
            String orgPayload;

            for(int i = 0; i < mPagedList.size() && !mStopped.get(); i++) {
                msg = mPagedList.get(i);
                jsContext = mJavaScript.get(msg.getTopic());
                if (jsContext != null) {
                    if (jsContext instanceof JSInitError) {
                        orgPayload = new String(msg.getPayload(), Utils.UTF_8);
                        formatedContent = mErrPrefix + " " + ((JSInitError) jsContext).getMessage() + "\n" + orgPayload;
                    } else {
                        try {
                            formatedContent = interpreter.formatMsg(jsContext, msg, 0);
                            if (formatedContent == null) {
                                formatedContent = "";
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

    protected volatile int cnt;
    protected AtomicBoolean mStopped;
    protected List<MqttMessage> mPagedList;
    protected Map<String, JavaScript.Context> mJavaScript;
    protected String mErrPrefix;
}
