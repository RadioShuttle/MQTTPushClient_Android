/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import de.radioshuttle.db.MqttMessage;

public class JavaScriptExcecutor {

    public JavaScriptExcecutor() {
        executors = new HashMap<>();
    }

    interface Callback {
        void onFinshed(Item item, Map<String, Object> result);
    }

    public void executeFilterScript(Item item, MqttMessage message, Callback callback) {
        if (item != null) {
            Worker worker = null;
            if (!executors.containsKey(item.id)) {
                worker = new Worker();
                worker.start();
                executors.put(item.id, worker);
            } else {
                worker = executors.get(item.id);
            }
            TaskInfo task = new TaskInfo();
            task.item = item;
            task.message = message;
            task.callback = callback;

            //TODO: if worker stopped (jsDisabled due to timeout or error in JavaScript) directly call callback

            /* remove all queued messages */
            while(worker.tasks.poll() != null); //TODO: compare for newer messages
            worker.tasks.add(task);
        }
    }

    public void shutdown() {
        if (executors != null) {
            for(Worker worker : executors.values()) {
                if (worker != null) {
                    worker.stop();
                }
            }
        }
    }

    public void remove(int itemID) {
        if (executors != null) {
            Worker w = executors.get(itemID);
            if (w != null) {
                w.stop();
                executors.remove(itemID);
            }
        }
    }

    public class Worker implements Runnable {

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
            while(!stopped) {
                final HashMap<String, Object> result = new HashMap<>();
                try {
                    task = tasks.take();
                } catch (InterruptedException e) {}
                if (!stopped) {
                    try {
                        //TODO: remove test code
                        result.put("content",  new String(task.message.getPayload()) + " Grad");
                    } catch(Exception e) {
                        e.printStackTrace(); //TODO
                    }
                    /* post result on UI thread*/
                    if (task.callback != null && !stopped) {
                        final TaskInfo ftask = task;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                ftask.callback.onFinshed(ftask.item, result);
                            }
                        });
                    }
                }
            }
            if (task != null)
                Log.d(TAG, "JavaScript executor for " + task.item.label + " terminated.");
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

    protected static class TaskInfo {
        MqttMessage message;
        Item item;
        Callback callback;
    }

    private HashMap<Integer, Worker> executors;
    private final static String TAG = JavaScriptExcecutor.class.getSimpleName();
}
