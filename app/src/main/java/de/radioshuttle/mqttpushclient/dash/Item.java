/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;


import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public abstract class Item {
    public Item() {
        data = new HashMap<>();
        textcolor = DColor.OS_DEFAULT;
        background = DColor.OS_DEFAULT;
        liveData = new MutableLiveData<>();
        liveData.setValue(0);
    }

    public int id;
    public long textcolor; // flag, alpha, r, g, b
    public long background; // flag, alpha, r, g, b
    public int textsize; // 0 - default, 1 - small, 2 - medium, 3 - large
    public String topic_s;
    public String script_f;

    public String topic_p;
    public String script_p;
    public boolean retain;

    public String label;

    public String outputScriptError;

    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = new JSONObject();
        if (!(this instanceof GroupItem)) {
            o.put("topic_s", topic_s);
            o.put("script_f", (script_f == null ? "" : script_f));

            o.put("topic_p", topic_p);
            o.put("script_p", (script_p == null ? "" : script_p));

            o.put("retain", retain);
        }
        o.put("type", getType());
        o.put("label", label);
        o.put("textcolor", textcolor);
        o.put("background", background);
        o.put("textsize", textsize);
        o.put("id", id);

        return o;
    }

    protected void setJSONData(JSONObject o) {
        label = o.optString("label");
        background = o.optLong("background");
        textcolor = o.optLong("textcolor");
        textsize = o.optInt("textsize");
        topic_s = o.optString("topic_s");
        script_f = o.optString("script_f");
        topic_p = o.optString("topic_p");
        script_p = o.optString("script_p");
        retain = o.optBoolean("retain");
        id = o.optInt("id");
    }

    public static Item createItemFromJSONObject(JSONObject o) {
        Item item = null;
        String type = o.optString("type");
        switch (type) {
            case "text": item = new TextItem(); break;
            case "group": item = new GroupItem(); break;
            case "progress" : item = new ProgressItem(); break;
            case "switch" : item = new Switch(); break;
            case "custom" : item = new CustomItem(); break;
        }
        if (item != null) {
            item.setJSONData(o);
        }
        return item;
    }

    /* helper for view component (used in adapter and detail dialog */

    public void setViewBackground(View v, int defalutBackgroundColor) {
        if (v != null) {
            long bg = data.containsKey("background") ? (Long) data.get("background") : background;
            // int background = (bg == 0 ? defalutBackgroundColor : bg);
            int background;
            if (bg == DColor.OS_DEFAULT) {
                background = defalutBackgroundColor;
            } else if (bg == DColor.CLEAR) {
                background = defalutBackgroundColor; // chose default color as background TODO: consider transparent
            } else {
                background = (int) bg;
            }
            v.setBackgroundColor(background);
        }
    }

    /** set text color and text appearance */
    public void setViewTextAppearance(TextView v, int defaultColor) {
        if (v != null) {
            int textsize = data.containsKey("textsize") ?  (Integer) data.get("textsize") : this.textsize;
            int textSizeIdx = (textsize <= 0 ? Item.DEFAULT_TEXTSIZE : textsize ) -1;
            if (textSizeIdx >= 0 && textSizeIdx < TEXTAPP.length) {
                TextViewCompat.setTextAppearance(v, TEXTAPP[textSizeIdx]);
            }

            long color = getTextcolor();
            if (color == DColor.OS_DEFAULT || color == DColor.CLEAR) { // clear is inavalid, treat as DEFAULT
                v.setTextColor(defaultColor);
            } else {
                v.setTextColor((int) color);
            }

        }
    }

    /** add properties which may be get/set in JS to update view. Also extend DashboarJavascript.ViewProperties */
    public HashMap<String, Object> getJSViewProperties(HashMap<String, Object> viewProperties) {
        if (viewProperties == null) {
            viewProperties = new HashMap<>();
        }
        viewProperties.put("textcolor", data.containsKey("textcolor") ? (Long) data.get("textcolor") : textcolor);
        viewProperties.put("textsize", data.containsKey("textsize") ?  (Integer) data.get("textsize") : textsize);
        viewProperties.put("background", data.containsKey("background") ? (Long) data.get("background") : background);

        return viewProperties;
    }

    @MainThread
    public void notifyDataChanged() {
        liveDataTimestamp = System.currentTimeMillis();
        liveData.setValue(id);
    }

    public void notifyDataChangedThreadSafe() {
        liveDataTimestamp = System.currentTimeMillis();
        liveData.postValue(id);
    }

    protected void updateUIContent(Context context) {
    }

    protected long getTextcolor() {
        return data.containsKey("textcolor") ? (Long) data.get("textcolor") : textcolor;
    }

    final static int[] TEXTAPP = new int[] {android.R.style.TextAppearance_Small, android.R.style.TextAppearance_Medium, android.R.style.TextAppearance_Large};

    public Map<String, Object> data;

    public abstract String getType();

    public MutableLiveData<Integer> liveData;
    public volatile long liveDataTimestamp;

    public final static int DEFAULT_TEXTSIZE = 1;

    /** Dashboard version */
    public final static int DASHBOARD_VERSION = 0;

    protected static String TAG = Item.class.getSimpleName();
}
