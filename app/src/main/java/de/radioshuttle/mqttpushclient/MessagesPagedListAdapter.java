/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import androidx.core.graphics.drawable.DrawableCompat;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.dash.DColor;
import de.radioshuttle.utils.Utils;

public class MessagesPagedListAdapter extends PagedListAdapter<MqttMessage, MessagesPagedListAdapter.ViewHolder>{

    protected MessagesPagedListAdapter(AppCompatActivity activity) {
        super(DIFF_CALLBACK);
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        selectedItems = new HashSet<>();
        formatter = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault());
        timeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT);
        mDisplayMetrics = activity.getResources().getDisplayMetrics();

    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.activity_messages_row, parent, false);
        final MessagesPagedListAdapter.ViewHolder holder = new MessagesPagedListAdapter.ViewHolder(view);

        holder.subject = view.findViewById(R.id.subject);
        holder.msg = view.findViewById(R.id.msg);
        holder.backgroundLayer = view.findViewById(R.id.backgroundLayer);

        holder.subject.getTextColors().getDefaultColor();

        if (mDefaultTextColorBackground == null) {
            mDefaultTextColorSubject = holder.subject.getCurrentTextColor();
            mDefaultTextColorMsg = holder.msg.getCurrentTextColor();
            mDefaultTextColorBackground = 0; // assume transparent color
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MqttMessage m = getItem(position);

        int expBackground = mDefaultTextColorBackground;
        int expForegroundMsg = mDefaultTextColorMsg;

        if (m != null) {
            StringBuilder sb = new StringBuilder();
            if (DateUtils.isToday(m.getWhen())) {
                sb.append(timeFormatter.format(new Date(m.getWhen())));
            } else {
                sb.append(formatter.format(new Date(m.getWhen())));
            }
            sb.append(" - ");
            sb.append(m.getTopic());
            holder.subject.setText(sb.toString());

            holder.msg.setText(new String(m.getPayload(), Utils.UTF_8));

            holder.itemView.setSelected(selectedItems.contains(m.getId()));

            if (m.backgroundColor != null && m.backgroundColor != DColor.CLEAR && m.backgroundColor != DColor.OS_DEFAULT) {
                expBackground = m.backgroundColor.intValue();
            }
            if (m.textColor != null && m.textColor != DColor.CLEAR && m.textColor != DColor.OS_DEFAULT) {
                expForegroundMsg = m.textColor.intValue();
            }

        } else {
            holder.subject.setText(null);
            holder.msg.setText(null);
            if (holder.itemView.isSelected()) {
                holder.itemView.setSelected(false);
            }
        }

        if (expForegroundMsg != holder.currentForegroundMsg) {
            holder.msg.setTextColor(expForegroundMsg);
            holder.currentForegroundMsg = expForegroundMsg;
        }
        if (expBackground != holder.currentBackground) {
            if (holder.backgroundLayer.getBackground() != null) {
                Drawable d = DrawableCompat.wrap(holder.backgroundLayer.getBackground());
                holder.backgroundLayer.setBackground(d);
                DrawableCompat.setTint(holder.backgroundLayer.getBackground(), expBackground);
                holder.currentBackground = expBackground;
            }
        }
        if (holder.currentBackground != mDefaultTextColorBackground) {
            if (holder.backgroundLayer.getVisibility() != View.VISIBLE) {
                holder.backgroundLayer.setVisibility(View.VISIBLE);
            }
        } else {
            if (holder.backgroundLayer.getVisibility() != View.GONE) {
                holder.backgroundLayer.setVisibility(View.GONE);
            }
        }
    }

    public boolean hasNewItems() {
        return selectedItems != null && selectedItems.size() > 0;
    }

    public void clearSelection() {
        if (hasNewItems()) {
            selectedItems.clear();
            notifyDataSetChanged();
        }
    }

    public void submitList(PagedList<MqttMessage> pagedList, HashSet<Integer> newItems) {
        if (newItems != null && newItems.size() > 0) {
            selectedItems.addAll(newItems);
            newItems.clear();
            notifyDataSetChanged();
        }
        super.submitList(pagedList);
    }

    public static final DiffUtil.ItemCallback<MqttMessage> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MqttMessage>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull MqttMessage oldMsg, @NonNull MqttMessage newMsg) {
                    return oldMsg.getId() == newMsg.getId();
                }
                @Override
                public boolean areContentsTheSame(
                        @NonNull MqttMessage oldMsg, @NonNull MqttMessage newMsg) {
                    /* content may have changed after applying a filter script */
                    long t1 = oldMsg.textColor != null ? oldMsg.textColor : 0x0100000000L;
                    long t2 = newMsg.textColor != null ? newMsg.textColor : 0x0100000000L;
                    long b1 = oldMsg.backgroundColor != null ? oldMsg.backgroundColor : 0x0100000000L;
                    long b2 = newMsg.backgroundColor != null ? newMsg.backgroundColor : 0x0100000000L;
                    boolean sameColors = (t1 == t2 && b1 == b2);
                    //TODO: consider implementing equals in MqttMessage and use it here
                    return sameColors && Arrays.equals(oldMsg.getPayload(), newMsg.getPayload());
                }
            };

    HashSet<Integer> selectedItems;
    DateFormat formatter;
    DateFormat timeFormatter;
    private LayoutInflater mInflater;

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

        View backgroundLayer;
        TextView subject;
        TextView msg;

        int currentBackground;
        int currentForegroundSubject;
        int currentForegroundMsg;
        boolean selected;
    }

    Integer mDefaultTextColorSubject;
    Integer mDefaultTextColorMsg;
    Integer mDefaultTextColorBackground;
    DisplayMetrics mDisplayMetrics;

    private final static String TAG = MessagesPagedListAdapter.class.getSimpleName();
}
