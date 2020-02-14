/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class DBUtils {

    public static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        public SpanSizeLookup(RecyclerView recyclerView) {
            mRecyclerView = recyclerView;
        }
        RecyclerView mRecyclerView;

        @Override
        public int getSpanSize(int position) {
            int spanSize = 1;
            if (mRecyclerView != null && mRecyclerView.getLayoutManager()instanceof GridLayoutManager) {
                int spanCount = ((GridLayoutManager) mRecyclerView.getLayoutManager()).getSpanCount();
                RecyclerView.Adapter a = mRecyclerView.getAdapter();
                if (a instanceof DashBoardAdapter) {
                    if (a.getItemViewType(position) == DashBoardAdapter.TYPE_GROUP) {
                        spanSize = spanCount;
                    } else {
                        List<Item> list = ((DashBoardAdapter) a).getData();
                        if (list != null && position + 1 < list.size()) {
                            if (list.get(position + 1) instanceof GroupItem) {
                                int z = 1;
                                for(int i = position - 1; i >= 0 && !(list.get(i) instanceof GroupItem); i--) {
                                    z++; //TODO: this can be calculated in viewModel when "building" adapter data
                                }
                                if (z % spanCount > 0) {
                                    spanSize = spanCount - (z % spanCount) + 1;
                                }
                                // Log.d(SpanSizeLookup.class.getSimpleName()+".SpanSizeLookup", "position: " + position + ", z: " + z + ", span size: " + spanSize);
                            }
                        }
                    }
                }
            }
            return spanSize;
        }
    }

    public static void showDeleteDialog(final DashBoardActivity context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        String title = context.getString(R.string.dlg_del_dash_title);
        String all = context.getString(R.string.dlg_dash_items_all);
        String selectedItems = context.getString(R.string.dlg_dash_selected);

        builder.setTitle(title);

        final int[] selection = new int[] {0};
        builder.setSingleChoiceItems(new String[]{selectedItems, all}, selection[0], new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                selection[0] = item;
            }
        });
        builder.setPositiveButton(context.getString(R.string.action_delete_msgs), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (selection[0] == 0) { // selected items
                    context.onItemsDelete(false); // selected items
                } else {
                    context.onItemsDelete(true); // all items
                }
            }
        });
        builder.setNegativeButton(context.getString(R.string.action_cancel), null);
        AlertDialog dlg = builder.create();
        dlg.show();
    }

    public static int createItemsFromJSONString(String json, LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> groupItems, Set<String> lockedResouces, Map<String, Object> props) {
        int maxID = 0;
        if (!Utils.isEmpty(json) && groups != null && groupItems != null) {
            try {
                JSONObject dashboardObj = new JSONObject(json);
                int version = dashboardObj.optInt("version", 0);
                if (props != null) {
                    props.put("version", version);
                }

                JSONArray resources = dashboardObj.optJSONArray("resources");
                if (resources != null) {
                    String r;
                    for(int i = 0; i < resources.length(); i++) {
                        r = resources.optString(i);
                        if (!Utils.isEmpty(r)) {
                            lockedResouces.add(r);
                        }
                    }
                }

                JSONArray groupArray = dashboardObj.getJSONArray("groups");
                GroupItem groupItem;
                Item item;
                JSONObject groupJSON, itemJSON;
                for (int i = 0; i < groupArray.length(); i++) {
                    groupItem = new GroupItem();
                    groupJSON = groupArray.getJSONObject(i);
                    groupItem.setJSONData(groupJSON);
                    JSONArray itemArray = groupJSON.getJSONArray("items");
                    groups.add(groupItem);
                    LinkedList<Item> items = new LinkedList<>();
                    groupItems.put(groupItem.id, items);
                    if (groupItem.id > maxID) {
                        maxID = groupItem.id;
                    }

                    for (int j = 0; j < itemArray.length(); j++) {
                        itemJSON = itemArray.getJSONObject(j);
                        item = Item.createItemFromJSONObject(itemJSON);
                        items.add(item);
                        if (item.id > maxID) {
                            maxID = item.id;
                        }
                    }
                }
            } catch (JSONException e) {
                Log.d(Item.TAG, "Error parsing JSON: " + e.getMessage());
            }
        }
        return maxID;
    }

    public static JSONObject createJSONStrFromItems(LinkedList<GroupItem> groups, HashMap<Integer, LinkedList<Item>> groupItems, Set<String> lockedResources) {
        JSONObject dashboardObj = new JSONObject();
        JSONArray groupItemArray = new JSONArray();
        JSONArray resourcesArray = new JSONArray();

        try {
            dashboardObj.put("version", Item.DASHBOARD_VERSION);
            dashboardObj.put("groups", groupItemArray);
            dashboardObj.put("resources", resourcesArray);
        } catch (JSONException e) {
            Log.d(Item.TAG, "Error createJSONStrFromItems: " + e.getMessage());
            return dashboardObj;
        }
        if (lockedResources != null) {
            for(String resource : lockedResources) {
                if (!Utils.isEmpty(resource)) {
                    resourcesArray.put(resource);
                }
            }
        }

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
                    itemArray = new JSONArray();
                    groupJSON.put("items", itemArray);
                    groupItemArray.put(groupJSON);
                } catch(JSONException e) {
                    Log.d(Item.TAG, "Error createJSONStrFromItems: " + e.getMessage());
                    continue;
                }
                items = groupItems.get(g.id);
                if (items != null) {
                    Iterator<Item> it2 = items.iterator();
                    while(it2.hasNext()) {
                        Item e = it2.next();
                        try {
                            itemJSON = e.toJSONObject();
                        } catch(JSONException e2) {
                            Log.d(Item.TAG, "Error createJSONStrFromItems: " + e2.getMessage());
                            continue;
                        }
                        itemArray.put(itemJSON);
                    }
                }

            }
        }
        return dashboardObj;
    }

}
