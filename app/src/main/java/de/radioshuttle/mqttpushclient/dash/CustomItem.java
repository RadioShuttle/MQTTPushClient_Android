/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.util.Base64;
import android.util.Log;

import android.webkit.JavascriptInterface;

import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.Utils;

public class CustomItem extends Item {

    public CustomItem() {
        super();
        data = Collections.synchronizedMap(data);
        webViewLifeData = new MutableLiveData<>();
        webViewLifeData.setValue(0);
    }

    @Override
    public String getType() {
        return "custom";
    }

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        o.put("html", html == null ? "" : html);
        return o;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        html = o.optString("html");
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

        @JavascriptInterface
        public JSView getView() {
            return view;
        }

        @JavascriptInterface
        public void publish(String topic, String payload, boolean retain) {
            Log.d(TAG, "_publish: " + payload);
        }

        @JavascriptInterface
        public void log(String s) {
            if (s == null)
            s = "";
            Log.d(TAG, "webview: " + s);
        }

        JSView view;
    }

    public class JSView {

        @JavascriptInterface
        public void setError(String error) {
            String lastError = (String) data.get("error");
            if (!Utils.equals(error, lastError)) {
                data.put("error", (error == null ? "" : error));
                webViewLifeData.postValue(id);
            }
        }

        @JavascriptInterface
        public void setTextColor(double color) {
            //TODO
        }

        @JavascriptInterface
        public void setBackgroundColor(double color) {
            //TODO
        }

        @JavascriptInterface
        public double getTextColor() {
            return 0d; //TODO
        }

        @JavascriptInterface
        public double getBackgroundColor() {
            return 0d; //TODO
        };
    }


    /** build message call of _onMqttMessage as defined in custom_view.js */
    public static String build_onMqttMessageCall(CustomItem item) {
        StringBuilder js = new StringBuilder();
        if (item != null && item.data != null) {
            long paraWhen = item.data.get("msg.received") == null ? 0 : (Long) item.data.get("msg.received");
            String paraTopic = item.data.get("msg.topic") == null ? "" : (String) item.data.get("msg.topic");
            byte[] msgRaw = item.data.get("msg.raw") == null ? new byte[0] : (byte[]) item.data.get("msg.raw");
            String paraMsgRaw = Base64.encodeToString(msgRaw, Base64.NO_WRAP); //TODO: check there was a problem with Base64.Default
            String org;
            /*
            try {
                org = new String(Base64.decode(paraMsgRaw, Base64.DEFAULT), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            */
            String paraMsg = item.data.get("msg.text") == null ? "" : (String) item.data.get("msg.text");
            js.append("if (typeof window['onMqttMessage'] === 'function') _onMqttMessage(");
            js.append(paraWhen);
            js.append(",'");
            js.append(paraTopic);
            js.append("','");
            js.append(paraMsg);
            js.append("','");
            js.append(paraMsgRaw);
            js.append("');");
        }
        return js.toString();
    }

    public static String build_onMqttPushClientInitCall(PushAccount accountData, CustomItem item) {
        StringBuilder js = new StringBuilder();

        if (accountData != null && item != null) {
            js.append("MqttPushClient.acc = new Object();");
            js.append("MqttPushClient.acc.user = '");
            js.append(accountData.user == null ? "" : accountData.user);
            js.append("'; ");
            js.append("MqttPushClient.acc.mqttServer = '");
            try {
                URI u = new URI(accountData.uri);
                js.append(u.getAuthority());
            } catch (Exception e) {
                Log.d(TAG, "URI parse error: ", e);
            }
            js.append("'; ");
            js.append("MqttPushClient.acc.pushServer = '");
            js.append(accountData.pushserver == null ? "" : accountData.pushserver);
            js.append("'; ");

            js.append("if (typeof window['onMqttPushClientInit'] === 'function') onMqttPushClientInit(");
            js.append("MqttPushClient.acc");
            js.append(',');
            js.append("MqttPushClient.getView()");
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

    //UI state
    public boolean isLoading;
    public MutableLiveData<Integer> webViewLifeData;
}
