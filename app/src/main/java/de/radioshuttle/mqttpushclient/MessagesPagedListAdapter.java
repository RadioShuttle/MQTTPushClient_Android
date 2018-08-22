/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.arch.paging.DataSource;
import android.arch.paging.PagedList;
import android.arch.paging.PagedListAdapter;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

import de.radioshuttle.db.MqttMessage;

public class MessagesPagedListAdapter extends PagedListAdapter<MqttMessage, MessagesPagedListAdapter.ViewHolder>{

    protected MessagesPagedListAdapter(AppCompatActivity activity) {
        super(DIFF_CALLBACK);
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        formatter = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.activity_messages_row, parent, false);
        final MessagesPagedListAdapter.ViewHolder holder = new MessagesPagedListAdapter.ViewHolder(view);

        holder.msg = view.findViewById(R.id.msg);


        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MqttMessage m = getItem(position);
        if (m != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(formatter.format(new Date(m.getWhen())));
            sb.append("\n");
            sb.append(m.getTopic());
            sb.append("\n");
            sb.append(m.getMsg());

            holder.msg.setText(sb.toString());

        } else {
            holder.msg.setText(null);
        }
    }

    @Override
    public void submitList(PagedList<MqttMessage> pagedList) {
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
                    return oldMsg.getId() == newMsg.getId(); // data will never change
                }
            };

    DateFormat formatter;
    private LayoutInflater mInflater;

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View itemView) {
            super(itemView);
        }

        TextView msg;
    }
}
