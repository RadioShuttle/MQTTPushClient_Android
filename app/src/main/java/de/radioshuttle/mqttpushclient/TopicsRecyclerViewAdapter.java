package de.radioshuttle.mqttpushclient;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

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
        String e = mTopics.get(position);
        vh.topic.setText(mTopics.get(position));
        vh.itemView.setSelected(mSelectedTopics.contains(e));
    }

    public void setData(ArrayList<String> data) {
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

        String e = mTopics.get(pos);
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
    }

    public interface RowSelectionListener {
        void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems);
    }

    private Context mContext;
    private LayoutInflater mInflater;
    private RowSelectionListener mRowSelectionListener;
    private ArrayList<String> mTopics;
    private HashSet<String> mSelectedTopics;

}
