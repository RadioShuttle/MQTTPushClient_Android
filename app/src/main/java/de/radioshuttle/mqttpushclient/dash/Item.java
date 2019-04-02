/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;


import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public abstract class Item {
    public Item() {
        id = cnt++;
        data = new HashMap<>();
    }

    public int id; // transient (unique id for internal use)

    public int color;
    public int background;
    public int textsize; // 0 - default, 1 - small, 2 - medium, 3 - large
    public String topic_s;
    public String script_f;

    public String label;

    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = new JSONObject();
        if (!(this instanceof GroupItem)) {
            o.put("type", getType());
            o.put("topic_s", topic_s);
        }
        o.put("label", label);
        o.put("color", color);
        o.put("background", background);
        o.put("textsize", textsize);
        o.put("script_f", (script_f == null ? "" : script_f));

        return o;
    }

    protected void setJSONData(JSONObject o) {
        label = o.optString("label");
        background = o.optInt("background");
        color = o.optInt("color");
        textsize = o.optInt("textsize");
        topic_s = o.optString("topic_s");
        script_f = o.optString("script_f");
    }

    public static Item createItemFromJSONObject(JSONObject o) {
        Item item = null;
        String type = o.optString("type");
        switch (type) {
            case "text": item = new TextItem(); break;
        }
        if (item != null) {
            item.setJSONData(o);
        }
        return item;
    }

    public HashMap<String, Object> data;

    public abstract String getType();

    protected static int cnt = 0;

    public final static int DEFAULT_TEXTSIZE = 1;

    protected static String TAG = Item.class.getSimpleName();
}
