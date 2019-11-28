/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;
import java.util.Map;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class OptionListEditAdapter extends RecyclerView.Adapter<OptionListEditAdapter.ViewHolder>{

    public OptionListEditAdapter(Context context, DashBoardViewModel viewModel) {
        mInflater = LayoutInflater.from(context);
        mViewModel = viewModel;
    }

    public void setRowSelectionListener(RowSelectionListener callback) {
        mRowSelectionListener = callback;
    }

    @NonNull
    @Override
    public OptionListEditAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.activity_dash_board_edit_option_row, parent, false);
        final OptionListEditAdapter.ViewHolder vh = new OptionListEditAdapter.ViewHolder(view);
        vh.label = view.findViewById(R.id.label);
        vh.image = view.findViewById(R.id.image);

        vh.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            // Called when the user long-clicks on someView
            @Override
            public boolean onLongClick(View view) {
                int pos = vh.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    view.requestFocus();
                    toggleSelection(pos);
                }
                return true;
            }
        });

        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hasSelection()) {
                    int pos = vh.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        v.requestFocus();
                        toggleSelection(pos);
                    }
                }
            }
        });

        return vh;
    }

    public boolean hasSelection() {
        boolean isSelected = false;
        if (mData != null) {
            int n = getItemCount();
            for (int i = 0; i < n; i++) {
                if (mData.get(i).selected != 0L) {
                    isSelected = true;
                    break;
                }
            }
        }
        return isSelected;
    }

    @Override
    public void onBindViewHolder(@NonNull OptionListEditAdapter.ViewHolder holder, int position) {
        OptionList.Option item = mData.get(position);
        String label = (Utils.isEmpty(item.value) ? "" : item.value) + " - " +
                (Utils.isEmpty(item.displayValue) ? "" : item.displayValue);
        holder.label.setText(label);
        holder.itemView.setSelected(item.selected != 0L);
        if (Utils.isEmpty(item.imageURI)) {
            if (holder.image.getDrawable() != null) {
                holder.image.setImageDrawable(null);
            }
        } else {
            Map<String, Drawable> cache = mViewModel.getOptionListImageCache();
            Drawable img = cache.get(item.imageURI);
            holder.image.setImageDrawable(img);
        }
    }

    public void setData(LinkedList<OptionList.Option> list) {
        mData = list;
        notifyDataSetChanged();
    }

    private void toggleSelection(int pos) {
        if (mData != null) {
            int noOfSelectedItemsBefore = 0;
            int n = getItemCount();
            for (int i = 0; i < n; i++) {
                if (mData.get(i).selected != 0) {
                    noOfSelectedItemsBefore++;
                }
            }
            int noOfSelectedItems = noOfSelectedItemsBefore;

            OptionList.Option e = mData.get(pos);
            if (e.selected != 0) {
                e.selected = 0L;
                noOfSelectedItems--;
            } else {
                e.selected = System.currentTimeMillis();
                noOfSelectedItems++;
            }
            notifyItemChanged(pos);
            if (mRowSelectionListener != null) {
                mRowSelectionListener.onSelectionChange(noOfSelectedItemsBefore, noOfSelectedItems);
            }
        }
    }

    public void clearSelection() {
        if (mData != null) {
            int noOfSelectedItemsBefore = 0;
            int n = getItemCount();
            for (int i = 0; i < n; i++) {
                if (mData.get(i).selected != 0) {
                    noOfSelectedItemsBefore++;
                    mData.get(i).selected = 0L;
                }
            }
            if (mRowSelectionListener != null) {
                mRowSelectionListener.onSelectionChange(noOfSelectedItemsBefore, 0);
            }
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public OptionList.Option getLastSelectedItem() {
        OptionList.Option lastSelected = null, tmp;
        int n = getItemCount();
        for(int i = 0; i < n; i++) {
            tmp = mData.get(i);
            if (tmp.selected > 0) {
                if (lastSelected == null) {
                    lastSelected = tmp;
                    lastSelected.temp = i;
                } else if (tmp.selected > lastSelected.selected) {
                    lastSelected = tmp;
                    lastSelected.temp = i;
                }
            }
        }
        return lastSelected;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        public TextView label;
        public ImageView image;
    }

    public interface RowSelectionListener {
        void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems);
    }

    protected DashBoardViewModel mViewModel;
    protected LayoutInflater mInflater;
    protected LinkedList<OptionList.Option> mData;
    protected RowSelectionListener mRowSelectionListener;
    protected Context mContext;

}
