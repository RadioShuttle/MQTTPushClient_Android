/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

public class CustomItem extends Item {

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

    public static class JSObject {

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
    }

    /** build message call of _onUpdate as defined in custom_view.js */
    public static String build_onUpdateCall(CustomItem item) {
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
            String paraMsg = item.data.get("msg.content") == null ? "" : (String) item.data.get("msg.content");
            js.append("_onUpdate(");
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

    private String html = "";

    //UI state
    public boolean isLoading;
}
