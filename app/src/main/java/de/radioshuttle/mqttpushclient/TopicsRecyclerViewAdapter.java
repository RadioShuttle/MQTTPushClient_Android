/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import static de.radioshuttle.mqttpushclient.PushAccount.Topic.*;

public class TopicsRecyclerViewAdapter extends RecyclerView.Adapter {

    public TopicsRecyclerViewAdapter(AppCompatActivity activity, HashSet<String> selectedTopics, RowSelectionListener listener) {
        mContext = activity.getApplicationContext();
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRowSelectionListener = listener;
        mTopics = new ArrayList<>();
        mSelectedTopics = selectedTopics;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.activity_topics_row, parent, false);
        final ViewHolder holder = new ViewHolder(view);

        holder.topic = view.findViewById(R.id.topic);
        holder.notificationTypeImageHigh = view.findViewById(R.id.notificationImageHigh);
        holder.notificationTypeImageMed = view.findViewById(R.id.notificationImageMed);
        holder.notificationTypeImageLow = view.findViewById(R.id.notificationImageLow);

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            // Called when the user long-clicks on someView
            @Override
            public boolean onLongClick(View view) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    toggleSelection(pos);
                }
                return true;
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedTopics.size() > 0) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        toggleSelection(pos);
                    }
                }
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ViewHolder vh = (ViewHolder) holder;
        PushAccount.Topic e = mTopics.get(position);
        vh.topic.setText(e.name);
        if (e.prio == NOTIFICATION_HIGH) {
            setImageVisibility(vh.notificationTypeImageHigh, vh.notificationTypeImageLow, vh.notificationTypeImageMed);
        } else if (e.prio == NOTIFICATION_MEDIUM) {
            setImageVisibility(vh.notificationTypeImageMed, vh.notificationTypeImageLow, vh.notificationTypeImageHigh);
        } else { // r.prio == NOTIFICATION_LOW
            setImageVisibility(vh.notificationTypeImageLow, vh.notificationTypeImageMed, vh.notificationTypeImageHigh);
        }

        vh.itemView.setSelected(mSelectedTopics.contains(e.name));
    }

    protected void setImageVisibility(View visibleView, View gone1, View gone2) {
        if (visibleView.getVisibility() != View.VISIBLE) {
            visibleView.setVisibility(View.VISIBLE);
        }
        if (gone1.getVisibility() != View.GONE) {
            gone1.setVisibility(View.GONE);
        }
        if (gone2.getVisibility() != View.GONE) {
            gone2.setVisibility(View.GONE);
        }
    }


    public void setData(ArrayList<PushAccount.Topic> data) {
        mTopics = data;

        /* if topics have been deleted the selected topics hashmap must be updated */
        if (mSelectedTopics != null && mSelectedTopics.size() > 0) {
            int o = mSelectedTopics.size();
            mSelectedTopics.retainAll(data);
            int n = mSelectedTopics.size();
            if (o != n) {
                mRowSelectionListener.onSelectionChange(o, n);
            }
        }

        notifyDataSetChanged();
    }


    @Override
    public int getItemCount() {
        return mTopics.size();
    }

    private void toggleSelection(int pos) {
        int noOfSelectedItemsBefore = mSelectedTopics.size();
        int noOfSelectedItems = noOfSelectedItemsBefore;

        String e = mTopics.get(pos).name;
        if (mSelectedTopics.contains(e)) {
            mSelectedTopics.remove(e);
            noOfSelectedItems--;
        } else {
            mSelectedTopics.add(e);
            noOfSelectedItems++;
        }
        notifyItemChanged(pos);
        if (mRowSelectionListener != null) {
            mRowSelectionListener.onSelectionChange(noOfSelectedItemsBefore, noOfSelectedItems);
        }
    }

    public void clearSelection() {
        int noOfSelectedItemsBefore = mSelectedTopics.size();
        mSelectedTopics.clear();

        if (mRowSelectionListener != null) {
            mRowSelectionListener.onSelectionChange(noOfSelectedItemsBefore, 0);
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }

        TextView topic;
        ImageView notificationTypeImageHigh;
        ImageView notificationTypeImageMed;
        ImageView notificationTypeImageLow;
    }

    public interface RowSelectionListener {
        void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems);
    }

    public HashSet<String> getSelectedTopics() {
        return mSelectedTopics;
    }

    public ArrayList<PushAccount.Topic> getTopics() {
        return mTopics;
    }

    private Context mContext;
    private LayoutInflater mInflater;
    private RowSelectionListener mRowSelectionListener;
    private ArrayList<PushAccount.Topic> mTopics;
    private HashSet<String> mSelectedTopics;

}
