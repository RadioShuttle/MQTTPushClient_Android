/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;


import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import de.radioshuttle.utils.Utils;

public abstract class Item {
    public Item() {
        id = cnt++;
    }

    public int id; // transient (unique id for internal use)

    public int color;
    public int background;
    public int textsize; // 0 - default, 1 - small, 2 - medium, 3 - large
    public String topic_s;

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

        return o;
    }

    protected void setJSONData(JSONObject o) {
        label = o.optString("label");
        background = o.optInt("background");
        color = o.optInt("color");
        textsize = o.optInt("textsize");
        topic_s = o.optString("topic_s");
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

    public static void createItemsFromJSONString(String json, LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> groupItems) {
        if (!Utils.isEmpty(json) && groups != null && groupItems != null) {
            try {
                JSONArray groupArray = new JSONArray(json);
                GroupItem groupItem;
                Item item;
                JSONObject groupJSON, itemJSON;
                for (int i = 0; i < groupArray.length(); i++) {
                    groupItem = new GroupItem();
                    groupJSON = groupArray.getJSONObject(i);
                    if (groupJSON.optInt("id", -1) != -1) {
                        groupItem.id = groupJSON.optInt("id");
                        if (groupItem.id >= cnt) {
                            cnt = groupItem.id + 1;
                        }
                    }
                    groupItem.setJSONData(groupJSON);
                    JSONArray itemArray = groupJSON.getJSONArray("items");
                    groups.add(groupItem);
                    LinkedList<Item> items = new LinkedList<>();
                    groupItems.put(groupItem.id, items);

                    for (int j = 0; j < itemArray.length(); j++) {
                        itemJSON = itemArray.getJSONObject(j);
                        item = createItemFromJSONObject(itemJSON);
                        if (itemJSON.optInt("id", -1) != -1) {
                            item.id = itemJSON.optInt("id");
                            if (item.id >= cnt) {
                                cnt = item.id  + 1;
                            }
                        }
                        items.add(item);
                    }
                }
            } catch (JSONException e) {
                Log.d(TAG, "Error parsing JSON: " + e.getMessage());
            }
        }
    }

    public static JSONArray createJSONStrFromItems(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> groupItems, boolean storeGroupID) {
        JSONArray groupItemArray = new JSONArray();
        if (groups != null && groupItems != null) {
            JSONObject groupJSON = null;
            JSONObject itemJSON = null;
            JSONArray itemArray = null;

            Iterator<GroupItem> it = groups.iterator();
            LinkedList<Item> items;
            while(it.hasNext()) {
                GroupItem g = it.next();
                try {
                    groupJSON = g.toJSONObject();
                    if (storeGroupID) {
                        groupJSON.put("id", g.id);
                    }
                    itemArray = new JSONArray();
                    groupJSON.put("items", itemArray);
                    groupItemArray.put(groupJSON);
                } catch(JSONException e) {
                    Log.d(TAG, "Error createJSONStrFromItems: " + e.getMessage());
                    continue;
                }
                items = groupItems.get(g.id);
                if (items != null) {
                    Iterator<Item> it2 = items.iterator();
                    while(it2.hasNext()) {
                        Item e = it2.next();
                        try {
                            itemJSON = e.toJSONObject();
                            if (storeGroupID) {
                                itemJSON.put("id", e.id);
                            }
                        } catch(JSONException e2) {
                            Log.d(TAG, "Error createJSONStrFromItems: " + e2.getMessage());
                            continue;
                        }
                        itemArray.put(itemJSON);
                    }
                }

            }
        }
        return groupItemArray;
    }

    public abstract String getType();

    private static int cnt = 0;

    public final static int TEXTSIZE_SMALL = 1;
    public final static int TEXTSIZE_MEDIUM = 2;
    public final static int TEXTSIZE_LARGE = 3;

    public final static int DEFAULT_TEXTSIZE = 1;

    protected static String TAG = Item.class.getSimpleName();
}
