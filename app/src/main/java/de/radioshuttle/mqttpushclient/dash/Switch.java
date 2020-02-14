/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.graphics.drawable.Drawable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

import de.radioshuttle.utils.Utils;

public class Switch extends Item {

    public String val;
    public String uri; // res://internal/ic_alarm_on.png; res://user/2131231
    public long color; // 0 = default system color, else color value (value will not be used for tinting, if noTint = true and uri contains an image)
    public long bgcolor;

    public String valOff; // off state (if switch), unused if button
    public String uriOff;
    public long colorOff;// 0 and transparent2 false = default color, 0 and transparent2 true = noTint color, else color value
    public long bgcolorOff;

    //transient
    public Drawable image;
    public Drawable imageOff;
    public Drawable imageDetail;
    public Drawable imageDetailOff;
    public String imageUri;
    public String imageUriOff;

    public Switch () {
        color = DColor.OS_DEFAULT;
        bgcolor = DColor.OS_DEFAULT;
        colorOff = DColor.OS_DEFAULT;
        bgcolorOff = DColor.OS_DEFAULT;
    }

    @Override
    public String getType() {
        return "switch";
    }

    public boolean isOnState() {
        return Utils.isEmpty(valOff) || Utils.equals(val, data.get("content"));
    }

    /** add properties which may be get/set in JS to update view. Also extend DashboarJavascript.ViewProperties */
    public HashMap<String, Object> getJSViewProperties(HashMap<String, Object> viewProperties) {
        viewProperties = super.getJSViewProperties(viewProperties);
        viewProperties.put("ctrl_color", data.containsKey("ctrl_color") ? (Long) data.get("ctrl_color") : color);
        viewProperties.put("ctrl_color_off", data.containsKey("ctrl_color_off") ? (Long) data.get("ctrl_color_off") : colorOff);
        viewProperties.put("ctrl_background", data.containsKey("ctrl_background") ? (Long) data.get("ctrl_background") : bgcolor);
        viewProperties.put("ctrl_background_off", data.containsKey("ctrl_background_off") ? (Long) data.get("ctrl_background_off") : bgcolorOff);
        viewProperties.put("ctrl_image", data.containsKey("ctrl_image") ? (String) data.get("ctrl_image") : uri);
        viewProperties.put("ctrl_image_off", data.containsKey("ctrl_image_off") ? (String) data.get("ctrl_image_off") : uriOff);

        return viewProperties;
    }

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        o.put("val", val == null ? "" : val);
        o.put("uri", uri == null ? "" : uri);
        o.put("color", color);
        o.put("bgcolor", bgcolor);
        o.put("val_off",  valOff == null ? "" : valOff);
        o.put("uri_off", uriOff == null ? "" : uriOff);
        o.put("bgcolor_off", bgcolorOff);
        o.put("color_off", colorOff);
        return o;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        val = o.optString("val");
        uri = o.optString("uri");
        valOff = o.optString("val_off");
        uriOff = o.optString("uri_off");
        bgcolor = o.optLong("bgcolor");
        bgcolorOff = o.optLong("bgcolor_off");
        color = o.optLong("color");
        colorOff = o.optLong("color_off");
    }

}
