/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.JavaScript;
import de.radioshuttle.utils.Utils;

public class JavaScriptExcecutor {

    public JavaScriptExcecutor(PushAccount account, Application app) {
        mExecutors = new HashMap<>();
        mAccount = account;
        mApplication = app;
        mThrottleOutputScriptExecution = false;
        mHandler = new Handler(Looper.getMainLooper());
    }

    interface Callback {
        void onFinshed(Item item, Map<String, Object> result);
    }

    public void setThrottleOutputScriptExecution(boolean throttle) {
        this.mThrottleOutputScriptExecution = throttle;
    }

    public void executeOutputScript(final Item item, final String topic, final byte[] payload, boolean retain, final Callback callback) {

       final HashMap<String, Object> result = new HashMap<>();
        result.put("msg.topic", topic);
        result.put("msg.received", System.currentTimeMillis());
        result.put("msg.retain", retain);

        if (item != null) {
            Utils.executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mThrottleOutputScriptExecution) {
                        long now = System.currentTimeMillis();
                        long diff = now - mLastRunOutputScript;
                        if (diff < MIN_OUTPUTSCRIPT_INTERVAL) {
                            try {
                                // Log.d("JavaScriptExe", "publish wait: " + diff);
                                Thread.sleep(MIN_OUTPUTSCRIPT_INTERVAL - diff);
                            } catch (InterruptedException e) {
                            }
                        }
                        mLastRunOutputScript = now;
                    }

                    if (Utils.isEmpty(item.script_p)) {
                        if (callback != null) { // there is no script to execute
                            result.put("msg.raw", payload); // if there is no script, result equals argument payload
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFinshed(item, result);
                                }
                            });
                            return;
                        }
                    }

                    final DashBoardJavaScript js = DashBoardJavaScript.getInstance(mApplication);
                    try {
                        final JavaScript.Context context = js.initSetContent(
                                item.script_p,
                                mAccount.user,
                                new URI(mAccount.uri).getAuthority(),
                                mAccount.pushserver);
                        HashMap<String, Object> viewProperties = new HashMap<>();
                        item.getJSViewProperties(viewProperties);
                        js.initViewProperties(context, viewProperties);

                        Future future = Utils.executor.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String res = js.setContent(context, new String(payload), topic);
                                    if (!Utils.isEmpty(res)) {
                                        result.put("msg.raw", Base64.decode(res, Base64.DEFAULT));
                                    }
                                } finally {
                                    context.close();
                                }
                            }
                        });
                        future.get(JavaScript.TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        result.putAll(viewProperties);
                    } catch(Exception e) {
                        if (e instanceof TimeoutException) {
                            result.put("error", mApplication.getResources().getString(R.string.javascript_err_timeout));
                        } else if (e instanceof ExecutionException && e.getCause() != null) {
                            result.put("error", e.getCause().getMessage());
                        } else {
                            result.put("error", e.getMessage());
                        }
                    } finally {
                        if (callback != null) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onFinshed(item, result);
                                }
                            });
                        }
                    }
                }
            });
        }

    }

    public void executeFilterScript(Item item, MqttMessage message, Callback callback) {
        if (item != null) {
            Worker worker = null;
            if (!mExecutors.containsKey(item.id)) {
                worker = new Worker();
                worker.start();
                mExecutors.put(item.id, worker);
            } else {
                worker = mExecutors.get(item.id);
            }
            TaskInfo task = new TaskInfo();
            task.item = item;
            task.message = message;
            task.callback = callback;

            /* remove all queued messages */
            while(worker.tasks.poll() != null); //TODO: compare for newer messages
            worker.tasks.add(task);
        }
    }

    public void shutdown() {
        if (mExecutors != null) {
            for(Worker worker : mExecutors.values()) {
                if (worker != null) {
                    worker.stop();
                }
            }
        }
    }

    public void remove(int itemID) {
        if (mExecutors != null) {
            Worker w = mExecutors.get(itemID);
            if (w != null) {
                w.stop();
                mExecutors.remove(itemID);
            }
        }
    }

    protected class Worker implements Runnable {

        public Worker() {
            handler = new Handler(Looper.getMainLooper());
            tasks = new ArrayBlockingQueue<>(10);
        }

        public void start() {
            workerThread = new Thread(this);
            workerThread.start();
        }

        @Override
        public void run() {
            TaskInfo task = null;
            String lastSrc = null;
            boolean timeoutError = false; // indicates a timeout error in previous run
            JavaScript.Context jsContext = null;
            DashBoardJavaScript js = DashBoardJavaScript.getInstance(mApplication);
            HashMap<String, Object> viewProperties = null;

            while(!stopped) {
                final HashMap<String, Object> result = new HashMap<>();
                try {
                    task = tasks.take();
                } catch (InterruptedException e) {
                    task = null;
                }
                if (!stopped && task != null) {
                    Future<HashMap<String, Object>> future = null;
                    RunJS runJS = null;

                    try {
                        /* if java script souce has changed, create a new context object and release resouces of the previous one */
                        if ((!Utils.equals(lastSrc, task.item.script_f))) {
                            viewProperties = new HashMap<>();
                            timeoutError = false;
                            if (jsContext != null) { // release resources
                                jsContext.close();
                            }

                            try {
                                jsContext = js.initFormatter(
                                        task.item.script_f, mAccount.user,
                                        new URI(mAccount.uri).getAuthority(), mAccount.pushserver);
                            } catch (Exception e) {
                                lastSrc = null;
                                throw e;
                            }

                            js.initViewProperties(jsContext, viewProperties);
                            lastSrc = task.item.script_f;
                        }
                        /* if there was a timeout error in the previous run, do not try again, just return error*/
                        if (timeoutError) {
                            result.put("error", mApplication.getResources().getString(R.string.javascript_err_timeout));
                        } else {
                            /* set current view propertes */
                            task.item.getJSViewProperties(viewProperties);

                            /* delegate javascript run to other thread to avoid long blocking times */
                            runJS = new RunJS(jsContext, task.message);
                            future = Utils.executor.submit(runJS);
                            HashMap<String, Object> r = future.get(JavaScript.TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            result.putAll(r);
                            result.putAll(viewProperties);
                        }
                    } catch(Exception e) {
                        if (e instanceof TimeoutException) {
                            result.put("error", mApplication.getResources().getString(R.string.javascript_err_timeout));
                        } else if (e instanceof ExecutionException && e.getCause() != null) {
                            result.put("error", e.getCause().getMessage());
                        } else {
                            result.put("error", e.getMessage());
                        }
                        if (future != null && !future.isDone()) { // make sure JS resources are always released in case of timeout error
                            runJS.releaseResources = true; // tell running thread to release resources after he has finished executing
                            if (future.isDone()) { // reread and close if completed yet
                                jsContext.close();
                            }
                            jsContext = null;
                        }
                        timeoutError = (e instanceof TimeoutException);
                    }
                    /* post result on UI thread*/
                    if (task.callback != null && !stopped) {
                        final TaskInfo pTask = task;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                result.put("msg.received", pTask.message.getWhen());
                                result.put("msg.raw", pTask.message.getPayload());
                                result.put("msg.topic", pTask.message.getTopic() == null ? "" :  pTask.message.getTopic());
                                result.put("msg.text", new String(pTask.message.getPayload()));
                                if (result.containsKey("error")) {
                                    result.put("content", new String(pTask.message.getPayload()));
                                }
                                pTask.callback.onFinshed(pTask.item, result);
                            }
                        });
                    }
                }
            }
            if (task != null)
                Log.d(TAG, "JavaScript executor for " + task.item.label + " terminated.");
            if (jsContext != null) {
                jsContext.close();
            }
        }

        public void stop() {
            stopped = true;
            workerThread.interrupt();
        }

        public ArrayBlockingQueue<TaskInfo> tasks;

        Handler handler;
        Thread workerThread;
        volatile boolean stopped = false;
    }

    protected class RunJS implements Callable<HashMap<String, Object>> {
        public RunJS(JavaScript.Context jsContext, MqttMessage message) {
            this.jsContext = jsContext;
            this.message = message;
        }

        @Override
        public HashMap<String, Object> call() {
            HashMap<String, Object> result = new HashMap<>();
            try {
                String content = JavaScript.getInstance(mApplication).formatMsg(jsContext, message, 0);
                result.put("content", content);
            } finally {
                /* put additional message data to result too */
                if (releaseResources) {
                    jsContext.close();
                }
            }
            return result;
        }

        MqttMessage message;
        JavaScript.Context jsContext;
        public volatile boolean releaseResources;

    }

    protected static class TaskInfo {
        MqttMessage message;
        Item item;
        Callback callback;
    }

    public static long MIN_OUTPUTSCRIPT_INTERVAL = 500;
    protected volatile long mLastRunOutputScript = 0;
    protected volatile boolean mThrottleOutputScriptExecution;

    private Handler mHandler;
    private Application mApplication;
    private HashMap<Integer, Worker> mExecutors;
    private PushAccount mAccount;
    private final static String TAG = JavaScriptExcecutor.class.getSimpleName();
}
