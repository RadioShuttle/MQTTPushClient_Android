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
    }

    interface Callback {
        void onFinshed(Item item, Map<String, Object> result);
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
            JavaScript js = JavaScript.getInstance();

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
                            timeoutError = false;
                            lastSrc = task.item.script_f;
                            if (jsContext != null) { // release resources
                                jsContext.close();
                            }
                            jsContext = js.initFormatter(
                                    task.item.script_f, mAccount.user,
                                    new URI(mAccount.uri).getAuthority(), mAccount.pushserver);
                        }
                        /* if there was a timeout error in the previous run, do not try again, just return error*/
                        if (timeoutError) {
                            result.put("error", mApplication.getResources().getString(R.string.javascript_err_timeout));
                        } else {
                            /* delegate javascript run to other thread to avoid long blocking times */
                            runJS = new RunJS(jsContext, task.message);
                            future = Utils.executor.submit(runJS);
                            HashMap<String, Object> r = future.get(JavaScript.TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            result.putAll(r);
                        }
                    } catch(Exception e) {
                        if (e instanceof TimeoutException) {
                            result.put("error", mApplication.getResources().getString(R.string.javascript_err_timeout));
                        } else if (e instanceof ExecutionException && e.getCause() != null) {
                            result.put("error", e.getCause().getMessage());
                        } else {
                            result.put("error", e.getMessage());
                        }
                        if (future != null && !future.isDone()) { // make sure JS resources are always released in case of error
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
        public HashMap<String, Object> call() throws Exception {
            HashMap<String, Object> result = new HashMap<>();
            try {
                String content = JavaScript.getInstance().formatMsg(jsContext, message, 0);
                result.put("content", content);
            } finally {
                if (releaseResources) {
                    jsContext.close();
                    Log.d(TAG, "releaseResources !" ); //TODO: remove
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

    private Application mApplication;
    private HashMap<Integer, Worker> mExecutors;
    private PushAccount mAccount;
    private final static String TAG = JavaScriptExcecutor.class.getSimpleName();
}
