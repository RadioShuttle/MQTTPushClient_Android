/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import de.radioshuttle.utils.Utils;

public class Switch extends Item {

    public String val;
    public String uri; // res://internal/ic_alarm_on.png; res://user/2131231
    public int color;
    public int bgcolor;

    public String val2; // inactive state (if switch), unused if button
    public String uri2;
    public int color2;
    public int bgcolor2;

    @Override
    public String getType() {
        return "switch";
    }

    public boolean isActiveState() {
        return Utils.isEmpty(val2) || Utils.equals(val, data.get("content"));
    }

    /** add properties which may be get/set in JS to update view. Also extend DashboarJavascript.ViewProperties */
    public HashMap<String, Object> getJSViewProperties(HashMap<String, Object> viewProperties) {
        viewProperties = super.getJSViewProperties(viewProperties);
        //TODO:consider adding props for images (resource ids), and implement DashboarJavascript.ViewProperties
        viewProperties.put("ctrl_color", data.containsKey("ctrl_color") ? (Integer) data.get("ctrl_color") : color);
        viewProperties.put("ctrl_background", data.containsKey("ctrl_background") ? (Integer) data.get("ctrl_background") : color);
        viewProperties.put("ctrl_color2", data.containsKey("ctrl_color2") ? (Integer) data.get("ctrl_color2") : color);
        viewProperties.put("ctrl_background2", data.containsKey("ctrl_background2") ? (Integer) data.get("ctrl_background2") : color);
        return viewProperties;
    }

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        o.put("val", val == null ? "" : val);
        o.put("uri", uri == null ? "" : uri);
        o.put("color", color);
        o.put("bgcolor", bgcolor);
        if (!Utils.isEmpty(val2)) {
            o.put("val2", val2);
            o.put("uri2", uri2 == null ? "" : uri);
            o.put("bgcolor2", bgcolor2);
            o.put("color2", color2);
        }
        return o;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        val = o.optString("val");
        uri = o.optString("uri");
        val2 = o.optString("val2");
        uri2 = o.optString("uri2");
        bgcolor = o.optInt("bgcolor");
        bgcolor2 = o.optInt("bgcolor2");
        color = o.optInt("color");
        color2 = o.optInt("color2");
    }
}
