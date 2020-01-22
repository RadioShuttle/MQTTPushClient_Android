/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.util.Log;

import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.net.PublishRequest;
import de.radioshuttle.utils.Utils;

public class CustomItem extends Item {

    public CustomItem() {
        super();
        data = Collections.synchronizedMap(data);
        mParameter = new ArrayList<>();
    }

    @Override
    public String getType() {
        return "custom";
    }

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        o.put("html", html == null ? "" : html);
        JSONArray paras = new JSONArray();
        if (mParameter != null) {
            for(String p : mParameter) {
                paras.put(p);
            }
            o.put("parameter", paras);
        }
        return o;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        html = o.optString("html");
        mParameter.clear();
        JSONArray paras = o.optJSONArray("parameter");
        if (paras != null) {
            for(int i = 0; i < paras.length(); i++) {
                mParameter.add(paras.optString(i));
            }
        }
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getHtml() {
        String h ;
        if (html == null) {
            h = "";
        } else {
            h = html;
        }
        return h;
    }

    public ArrayList<String> getParameterList() {
        if (mParameter == null) {
            mParameter = new ArrayList<>();
        }
        return mParameter;
    }

    public void setParameterList(ArrayList<String> parameterList) {
        mParameter = parameterList;
    }

    public JSObject getWebInterface() {
        if (mWebviewInterface == null) {
            mWebviewInterface = new JSObject();
        }
        return mWebviewInterface;
    }

    /* see also cv_interface.js */

    public class JSObject {

        public JSObject() {
            view = new JSView();
        }

        public void setViewModel(DashBoardViewModel viewModel) {
            if (this.viewModel != viewModel) {
                this.viewModel = viewModel;
            }
        }

        public boolean confirmResultDeliverd(PublishRequest request) {
            return request != null && currentPublishRequest.compareAndSet(request.getmPublishID(), 0L);
        }

        @JavascriptInterface
        public JSView getView() {
            return view;
        }

        /* the "public" publish is added via cv_interface.js (to handle ArrayBuffer data type) */
        @JavascriptInterface
        public boolean _publishHex(String topic, String payloadHex, boolean retain) {
            Log.d(TAG, "_publish: " + payloadHex + " " + retain);
            if (payloadHex == null) {
                payloadHex = "";
            }
            boolean publishStarted = false;
            /* only allow publish if no publish request is already running */
            if (viewModel != null && currentPublishRequest.compareAndSet(0L, -1L)) {
                long publishID = viewModel.publish(topic, Utils.hexToByteArray(payloadHex), retain, CustomItem.this);
                currentPublishRequest.set(publishID);
                publishStarted = true;
            }
            return publishStarted;
        }

        @JavascriptInterface
        public boolean _publishStr(String topic, String payload, boolean retain) {
            Log.d(TAG, "_publish: " + payload + " " + retain);
            if (payload == null) {
                payload = "";
            }
            boolean publishStarted = false;
            /* only allow publish if no publish request is already running */
            if (viewModel != null && currentPublishRequest.compareAndSet(0L, -1L)) {
                try {
                    long publishID = viewModel.publish(topic, payload.getBytes("UTF-8"), retain, CustomItem.this);
                    currentPublishRequest.set(publishID);
                    publishStarted = true;
                } catch (UnsupportedEncodingException e) {
                }
            }
            return publishStarted;
        }

        @JavascriptInterface
        public void log(String s) {
            if (s == null)
            s = "";
            Log.d(TAG, "webview: " + s);
        }

        JSView view;
        DashBoardViewModel viewModel;
        AtomicLong currentPublishRequest = new AtomicLong(0L);
    }

    public class JSView {

        @JavascriptInterface
        public void setError(String error) {
            String lastError = (String) data.get("error");
            if (!Utils.equals(error, lastError)) {
                data.put("error", (error == null ? "" : error));
                notifyDataChangedNonUIThread();
            }
        }

        @JavascriptInterface
        public void setTextColor(double color) {
            Double currVal = getTextColor();
            if (currVal != color) {
                data.put("textcolor", doubleToLong(color));
                notifyDataChangedNonUIThread();
            }
        }

        @JavascriptInterface
        public double getTextColor() {
            return longToDouble(CustomItem.this.getTextcolor());
        }

        @JavascriptInterface
        public void setBackgroundColor(double color) {
            Double currVal = getBackgroundColor();
            if (currVal != color) {
                data.put("background", doubleToLong(color));
                notifyDataChangedNonUIThread();
            }
        }

        @JavascriptInterface
        public double getBackgroundColor() {
            return longToDouble(CustomItem.this.getBackgroundColor());
        }

        @JavascriptInterface
        public void _setUserData(String userData) {
            data.put("userdata", userData);
        }

        @JavascriptInterface
        public String _getUserData() {
            return  data.get("userdata") == null ? "" : (String) data.get("userdata");
        }

        @JavascriptInterface
        public String _getHistoricalData() {
            Object history = data.get("history");
            String jsonStr;
            if (history instanceof String) {
                jsonStr = (String) history;
            } else if (history instanceof LinkedList) {
                LinkedList<Message> historyList = (LinkedList<Message>) history;
                JSONArray arr = new JSONArray();
                JSONObject entry;
                synchronized (historyList) {
                    for(Message m : historyList) { //TODO: data preparation might be done in request
                        entry = new JSONObject();
                        try {
                            entry.put("_received", m.getWhen());
                            entry.put("topic", m.getTopic());
                            entry.put("text", new String(m.getPayload()));
                            entry.put("_raw", Utils.byteArrayToHex(m.getPayload()));

                        } catch (JSONException e) {
                            Log.e(TAG, "error pasing json: " , e);
                        }
                        arr.put(entry);
                    }
                    jsonStr = arr.toString();
                    data.put("history", jsonStr); // for reuse
                }
            } else {
                jsonStr = "[]";
            }
            return jsonStr;
        }

        @JavascriptInterface
        public String getSubscribedTopic() {
            return topic_s == null ? "" :  topic_s;
        }

        @JavascriptInterface
        public String getPublishTopic() { //TODO: currently not available in UI (but may in future)
            return topic_p == null ? "" :  topic_p;
        }

        protected double longToDouble(long i) {
            double v;
            if (i == DColor.CLEAR) {
                v = DColor.CLEAR;
            } else if (i == DColor.OS_DEFAULT) {
                v = DColor.OS_DEFAULT;
            } else {
                v = i & 0xFFFFFFFFL;
            }
            return v;
        }

        protected long doubleToLong(double d) {
            long v;
            if (d == (double) DColor.CLEAR) {
                v = DColor.CLEAR;
            } else if (d == (double) DColor.OS_DEFAULT) {
                v = DColor.OS_DEFAULT;
            } else {
                v = (long) d & 0xFFFFFFFFL;
            }
            return v;
        }

    }

    public boolean hasMessageData() {
        return  !Utils.isEmpty(topic_s) && data != null &&
                !Utils.isEmpty((String) data.get("msg.topic"));
    }


    /** build message call of _onMqttMessage as defined in custom_view.js */
    public static String build_onMqttMessageCall(CustomItem item) {
        StringBuilder js = new StringBuilder();
        if (item != null && item.data != null) {
            long paraWhen = item.data.get("msg.received") == null ? 0 : (Long) item.data.get("msg.received");
            String paraTopic = item.data.get("msg.topic") == null ? "" : (String) item.data.get("msg.topic");
            byte[] msgRaw = item.data.get("msg.raw") == null ? new byte[0] : (byte[]) item.data.get("msg.raw");
            String paraMsgRaw = Utils.byteArrayToHex(msgRaw);
            String paraMsg = item.data.get("msg.text") == null ? "" : (String) item.data.get("msg.text");

            String paraMsgEnc = "";
            String paraTopicEnc = "";
            try {
                paraMsgEnc = URLEncoder.encode(paraMsg, "UTF-8").replace("+", "%20");
            } catch (UnsupportedEncodingException e) {
            }
            try {
                paraTopicEnc = URLEncoder.encode(paraTopic, "UTF-8").replace("+", "%20");
            } catch (UnsupportedEncodingException e) {
            }

            js.append("if (typeof window['onMqttMessage'] === 'function') _onMqttMessage(");
            js.append(paraWhen);
            js.append(", decodeURIComponent('");
            js.append(paraTopicEnc);
            js.append("'), decodeURIComponent('");
            js.append(paraMsgEnc);
            js.append("'),'");
            js.append(paraMsgRaw);
            js.append("');");

        }
        return js.toString();
    }

    public static String build_onMqttPushClientInitCall(PushAccount accountData, CustomItem item, boolean isDialogView) {
        StringBuilder js = new StringBuilder();

        if (accountData != null && item != null) {
            String enc;
            js.append("MQTT.acc = new Object();");
            js.append("MQTT.acc.user = decodeURIComponent('");
            enc = accountData.user == null ? "" : accountData.user;
            try {
                enc = URLEncoder.encode(enc, "UTF-8").replace("+", "%20");
            } catch (UnsupportedEncodingException e) {}
            js.append(enc);
            js.append("'); ");
            js.append("MQTT.acc.mqttServer = decodeURIComponent('");
            try {
                URI u = new URI(accountData.uri);
                enc = u.getAuthority();
                try {
                    enc = URLEncoder.encode(enc, "UTF-8").replace("+", "%20");
                } catch (UnsupportedEncodingException e) {}
                js.append(enc);
            } catch (Exception e) {
                Log.d(TAG, "URI parse error: ", e);
            }
            js.append("'); ");

            js.append("MQTT.acc.pushServer = decodeURIComponent('");
            enc = accountData.pushserver == null ? "" : accountData.pushserver;
            try {
                enc = URLEncoder.encode(enc, "UTF-8").replace("+", "%20");
            } catch (UnsupportedEncodingException e) {}
            js.append(enc);
            js.append("'); ");

            js.append("MQTT.view.isDialog = function() { return " + isDialogView + ";}; ");

            js.append("if (typeof window['onMqttInit'] === 'function') onMqttInit(");
            js.append("MQTT.acc");
            js.append(',');
            js.append("MQTT.view");
            js.append("); ");
        }


        return js.toString();
    }

    /** the passed java script code will be executed when document in state complete */
    public static String buildJS_readyState(String src) {
        StringBuilder js = new StringBuilder();
        js.append("function _initMqttPushClient() {");
        js.append(src);
        js.append("}");

        js.append("if (document.readyState != 'loading') {");
        js.append("_initMqttPushClient()");
        js.append("} else {");
        js.append("document.addEventListener('readystatechange', function(e) {");
        js.append("if (e.target.readyState === 'complete') {");
        js.append("_initMqttPushClient()");
        js.append("}});");
        js.append("}");

        return js.toString();
    }

    private JSObject mWebviewInterface;
    private String html = "";
    private ArrayList<String> mParameter;

    //UI state
    public boolean isLoading;
    public boolean reloadRequested; // detail view triggered a reload

    public final static String BASE_URL = "http://pushclient/";
    public static URI BASE_URI;
    static {
        try {
            BASE_URI = new URI(BASE_URL);
        } catch (URISyntaxException e) {
            Log.e(CustomItem.class.getSimpleName(), "Invalid BASE URL: ", e);
        }
    }
}
