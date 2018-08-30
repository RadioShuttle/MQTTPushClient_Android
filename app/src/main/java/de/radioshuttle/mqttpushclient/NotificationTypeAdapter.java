/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

public class NotificationTypeAdapter extends ArrayAdapter<Map.Entry<Integer, String>> {

    public NotificationTypeAdapter(Context context) {
        super(context, R.layout.spinner_notification_type_item, R.id.notificationTypeText);

        mInflater = mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        LinkedHashMap<Integer, String> entries = new LinkedHashMap<>();
        entries.put(NOTIFICATION_HIGH, context.getString(R.string.dlg_ntype_high));
        entries.put(NOTIFICATION_MEDIUM, context.getString(R.string.dlg_ntype_medium));
        entries.put(NOTIFICATION_LOW, context.getString(R.string.dlg_ntype_low));
        addAll(entries.entrySet());
    }

    public View getCustomView(int position, View convertView, ViewGroup parent) {

        View v = mInflater.inflate(R.layout.spinner_notification_type_item, parent, false);
        TextView label = (TextView) v.findViewById(R.id.notificationTypeText);
        ImageView icon = (ImageView) v.findViewById(R.id.notificationTypeIcon);

        Map.Entry<Integer, String> entry = getItem(position);
        label.setText(entry.getValue());
        Integer key = entry.getKey();
        if (key == NOTIFICATION_HIGH) {
            icon.setImageResource(R.drawable.ic_topic_prio_high);
        } else if (key == NOTIFICATION_MEDIUM) {
            icon.setImageResource(R.drawable.ic_topic_prio_medium);
        } else { // key == NOTIFICATION_LOW
            icon.setImageResource(R.drawable.ic_topic_prio_low);
        }
        return v;
    }

    public View getCustomDropView(int position, View convertView, ViewGroup parent) {


        View v = mInflater.inflate(R.layout.spinner_notification_type_item, parent, false);
        TextView label = (TextView) v.findViewById(R.id.notificationTypeText);
        ImageView icon = (ImageView) v.findViewById(R.id.notificationTypeIcon);

        Map.Entry<Integer, String> entry = getItem(position);
        label.setText(entry.getValue());
        Integer key = entry.getKey();
        if (key == NOTIFICATION_HIGH) {
            icon.setImageResource(R.drawable.ic_topic_prio_high);
        } else if (key == NOTIFICATION_MEDIUM) {
            icon.setImageResource(R.drawable.ic_topic_prio_medium);
        } else { // key == NOTIFICATION_LOW
            icon.setImageResource(R.drawable.ic_topic_prio_low);
        }
        return v;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getCustomDropView(position, convertView, parent);
    }

    private LayoutInflater mInflater;

    public final static int NOTIFICATION_HIGH = 2;
    public final static int NOTIFICATION_MEDIUM = 1;
    public final static int NOTIFICATION_LOW = 0;
}
