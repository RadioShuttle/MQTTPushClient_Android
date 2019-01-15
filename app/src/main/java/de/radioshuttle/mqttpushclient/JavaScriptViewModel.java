/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Application;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.radioshuttle.db.AppDatabase;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.db.MqttMessageDao;
import de.radioshuttle.utils.Utils;
import de.radioshuttle.utils.JavaScript;

public class JavaScriptViewModel extends ViewModel {

    public MutableLiveData<Request> latestMessage;
    public MutableLiveData<JSResult> javaScriptResult;
    public MutableLiveData<Boolean> runState;

    /* used to cache test data */
    public HashMap<String, String> mContentFilterCache = new HashMap<>();
    public PushAccount mAccount;

    public boolean runJavaScript(final String souceCode) {
        boolean executed = false;
        if (javaScriptRunning.compareAndSet(false, true)) {
            executed = true;
            runState.setValue(true);
            final MqttMessage para = new MqttMessage();
            para.setMsg(mContentFilterCache.get("msg.content"));
            para.setTopic(mContentFilterCache.get("msg.topic"));
            para.setWhen(System.currentTimeMillis());
            final String accUser = mContentFilterCache.get("acc.user");
            final String accMqttServer = mContentFilterCache.get("acc.mqttServer");
            final String accPushServer = mContentFilterCache.get("acc.pushServer");

            Utils.executor.submit(new Runnable() {
                @Override
                public void run() {
                    JSResult result = new JSResult();
                    final JavaScript js = JavaScript.getInstance();
                    final JavaScript.Context context;
                    try {
                        context = js.initFormatter(souceCode, accUser, accMqttServer , accPushServer);
                        try {
                            Future<JSResult> future = Utils.executor.submit(new Callable<JSResult>() {
                                @Override
                                public JSResult call() {
                                    JSResult result = new JSResult();
                                    try {
                                        result.result = js.formatMsg(context, para, 0);
                                    } finally {
                                        context.close();
                                    }
                                    return result;
                                }
                            });

                            try {
                                result = future.get(JavaScript.TIMEOUT_MS, TimeUnit.MILLISECONDS);
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

    public void loadLastReceivedMsg(Application app, final String topic) {
        this.app = app; // context is required for database access
        requestCnt++;
        final Request request = new Request();
        request.id = requestCnt;
        request.result = null;
        currentRequest = request;

        if (this.app != null && mAccount != null) {
            Utils.executor.submit(new Runnable() {
                @Override
                public void run() {
                    AppDatabase db = AppDatabase.getInstance(JavaScriptViewModel.this.app);
                    MqttMessageDao dao = db.mqttMessageDao();
                    long psid = dao.getCode(mAccount.pushserverID);
                    long accountID = dao.getCode(mAccount.getMqttAccountName());
                    String t = (topic == null ? "" : topic);
                    List<MqttMessage> result = dao.loadReceivedMessagesForTopic(psid, accountID, t);
                    if (result != null && result.size() > 0) {
                        request.result = result.get(0);
                    } else {
                        request.result = null;
                    }
                    latestMessage.postValue(request);
                }
            });
        }

    }

    public JavaScriptViewModel() {
        latestMessage = new MutableLiveData<>();
        javaScriptResult = new MutableLiveData<>();
        runState = new MutableLiveData<>();
        currentRequest = null;
        requestCnt = 0;
        app = null;
        javaScriptRunning = new AtomicBoolean(false);
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

        public static int ERR_TIMEOUT = 1;
        public static int ERR_INTERPRETER = 2;
        public static int ERR_UNEXPECTED = 3;
    }

    private AtomicBoolean javaScriptRunning;
    private Request currentRequest;
    private int requestCnt;
    private Application app;

    private final static String TAG = JavaScriptViewModel.class.getSimpleName();
}
