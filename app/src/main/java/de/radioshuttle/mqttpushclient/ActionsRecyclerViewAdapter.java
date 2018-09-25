/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

public class ActionsRecyclerViewAdapter extends RecyclerView.Adapter {

    public ActionsRecyclerViewAdapter(AppCompatActivity activity, HashSet<String> selectedActions, RowSelectionListener listener) {
        mContext = activity.getApplicationContext();
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRowSelectionListener = listener;
        mActions = new ArrayList<>();
        mSelectedActions = selectedActions;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.activity_actions_row, parent, false);
        final ViewHolder holder = new ViewHolder(view);

        holder.name = view.findViewById(R.id.actionName);
        holder.topic = view.findViewById(R.id.actionTopic);

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
                if (mSelectedActions.size() > 0) {
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
        ActionsViewModel.Action e = mActions.get(position);
        vh.name.setText(e.name);
        vh.topic.setText(e.topic);
        vh.itemView.setSelected(mSelectedActions.contains(e.name));
    }


    public interface RowSelectionListener {
        void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems);
    }

    public void setData(ArrayList<ActionsViewModel.Action> data) {
        mActions = data;

        /* if topics have been deleted the selected topics hashmap must be updated */
        if (mSelectedActions != null && mSelectedActions.size() > 0) {
            int o = mSelectedActions.size();
            mSelectedActions.retainAll(data);
            int n = mSelectedActions.size();
            if (o != n) {
                mRowSelectionListener.onSelectionChange(o, n);
            }
        }

        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mActions.size();
    }

    private void toggleSelection(int pos) {
        int noOfSelectedItemsBefore = mSelectedActions.size();
        int noOfSelectedItems = noOfSelectedItemsBefore;

        String e = mActions.get(pos).name;
        if (mSelectedActions.contains(e)) {
            mSelectedActions.remove(e);
            noOfSelectedItems--;
        } else {
            mSelectedActions.add(e);
            noOfSelectedItems++;
        }
        notifyItemChanged(pos);
        if (mRowSelectionListener != null) {
            mRowSelectionListener.onSelectionChange(noOfSelectedItemsBefore, noOfSelectedItems);
        }
    }

    public void clearSelection() {
        int noOfSelectedItemsBefore = mSelectedActions.size();
        mSelectedActions.clear();

        if (mRowSelectionListener != null) {
            mRowSelectionListener.onSelectionChange(noOfSelectedItemsBefore, 0);
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }

        TextView name;
        TextView topic;
    }

    public ArrayList<ActionsViewModel.Action> getActions() {
        return mActions;
    }

    private Context mContext;
    private LayoutInflater mInflater;
    private RowSelectionListener mRowSelectionListener;
    private ArrayList<ActionsViewModel.Action> mActions;
    private HashSet<String> mSelectedActions;

}
