/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.mqttpushclient.R;

public class DashBoardAdapter extends RecyclerView.Adapter {

    public DashBoardAdapter(AppCompatActivity activity, int width, int spanCount, HashSet<Integer> selectedItems) {
        mInflater = activity.getLayoutInflater();
        mData = new ArrayList<>();
        mWidth = width;
        // setHasStableIds(true);

        if (Build.VERSION.SDK_INT >= 23) {
            mDefaultBackground = activity.getResources().getColor(R.color.dashboad_item_background, null);
        } else {
            mDefaultBackground = activity.getResources().getColor(R.color.dashboad_item_background);
        }
        spacing = activity.getResources().getDimensionPixelSize(R.dimen.dashboard_spacing);
        mSpanCnt = spanCount;
        mSelectedItems = selectedItems;
    }

    public void addListener(DashBoardActionListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Log.d(TAG, "onCreateViewHolder: " );
        View view;
        TextView label = null;
        TextView textContent = null;
        ImageView selectedImageView = null;
        int defaultColor = 0;
        if (viewType == TYPE_TEXT) {
            view = mInflater.inflate(R.layout.activity_dash_board_item_text, parent, false);
            label = view.findViewById(R.id.name);
            textContent = view.findViewById(R.id.textContent);
            selectedImageView = view.findViewById(R.id.check);
        } else {
            view = mInflater.inflate(R.layout.activity_dash_board_item_group, parent, false);
            label = view.findViewById(R.id.name);
            defaultColor = label.getTextColors().getDefaultColor();
            selectedImageView = view.findViewById(R.id.check);
        }

        final ViewHolder holder = new ViewHolder(view);
        holder.label = label; // item label
        holder.viewType = viewType;
        holder.textContent = textContent; // content for text items
        holder.selectedImageView = selectedImageView;
        holder.defaultColor = defaultColor;

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mListener != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        toggleSelection(pos);
                    }
                }
                return true;
            }
        });
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    if (mSelectedItems.size() > 0) {
                        int pos = holder.getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            toggleSelection(pos);
                        }
                    } else {
                        mListener.onItemClicked(getItem(holder.getAdapterPosition()));
                    }
                }
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // Log.d(TAG, "onBindViewHolder: " );
        ViewHolder h = (ViewHolder) holder;
        Item item = mData.get(position);
        if (h.viewType == TYPE_TEXT || h.viewType == TYPE_GROUP) {
            h.label.setText(item.label);
        }

        int background = (item.background == 0 ? mDefaultBackground : item.background);
        int color = (item.color == 0 ? h.defaultColor : item.color);
        if (mSelectedItems.contains(item.id)) {
            h.itemView.setActivated(true);
            if (h.selectedImageView != null && h.selectedImageView.getVisibility() != View.VISIBLE) {
                h.selectedImageView.setVisibility(View.VISIBLE);
            }
        } else {
            h.itemView.setActivated(false);
            if (h.selectedImageView != null && h.selectedImageView.getVisibility() != View.GONE) {
                h.selectedImageView.setVisibility(View.GONE);
            }
        }

        ViewGroup.LayoutParams lp = h.itemView.getLayoutParams();

        if (h.viewType == TYPE_TEXT) {
            lp = h.textContent.getLayoutParams();
            if (lp.width != mWidth || lp.height != mWidth) {
                lp.width = mWidth;
                lp.height = mWidth;
                h.textContent.setLayoutParams(lp);
            }
            h.textContent.setBackgroundColor(background);
        }

        if (h.viewType == TYPE_GROUP) {
            if (lp.width != mSpanCnt * mWidth + (mSpanCnt - 1) * spacing * 2) {
                lp.width = mSpanCnt * mWidth + (mSpanCnt - 1) * spacing * 2;
                h.itemView.setLayoutParams(lp);
            }
            h.itemView.setBackgroundColor(background);
            h.label.setTextColor(color);
        }
        // Log.d(TAG, "width: " + lp.width);
        // Log.d(TAG, "height: " + lp.height);
    }

    @Override
    public int getItemCount() {
        return (mData == null ? 0 : mData.size());
    }

    public void setData(List<Item> data) {
        mData = data;
        if (mData == null) {
            mData = new ArrayList<>();
        }

        /* if items have been deleted the selected items hashmap must be updated */
        if (mSelectedItems != null && mSelectedItems.size() > 0) {
            int o = mSelectedItems.size();
            HashSet<Integer> dataKeys = new HashSet<>();
            for (Item a : data) {
                dataKeys.add(a.id);
            }
            mSelectedItems.retainAll(dataKeys);
            int n = mSelectedItems.size();
            if (o != n && mListener != null) {
                mListener.onSelectionChange(o, n);
            }
        }


        notifyDataSetChanged();
    }

    public List<Item> getData() {
        return mData;
    }


    public void setItemWidth(int width, int spanCnt) {
        mSpanCnt = spanCnt;
        mWidth = width;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        TextView label;
        TextView textContent;
        ImageView selectedImageView;
        int defaultColor;
        int viewType;
    }

    @Override
    public int getItemViewType(int position) {
        int type = 0;
        if (mData != null && position < mData.size()) {
            Item item = mData.get(position);
            if (item instanceof GroupItem) {
                type = TYPE_GROUP;
            } else if (item instanceof TextItem) {
                type = TYPE_TEXT;
            }
        }
        return type;
    }

    @Override
    public long getItemId(int position) {
        return mData.get(position).id;
    }


    public Item getItem(int position) {
        Item item = null;
        if (mData != null && position < mData.size()) {
            item = mData.get(position);
        }
        return item;
    }

    protected void toggleSelection(int pos) {
        int noOfSelectedItemsBefore = mSelectedItems.size();
        int noOfSelectedItems = noOfSelectedItemsBefore;

        Integer e = mData.get(pos).id;
        if (mSelectedItems.contains(e)) {
            mSelectedItems.remove(e);
            noOfSelectedItems--;
        } else {
            mSelectedItems.add(e);
            noOfSelectedItems++;
        }
        notifyItemChanged(pos);
        if (mListener != null) {
            mListener.onSelectionChange(noOfSelectedItemsBefore, noOfSelectedItems);
        }
    }

    public void clearSelection() {
        int noOfSelectedItemsBefore = mSelectedItems.size();
        mSelectedItems.clear();

        if (mListener != null) {
            mListener.onSelectionChange(noOfSelectedItemsBefore, 0);
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return mSelectedItems != null && mSelectedItems.size() > 0;
    }

    public HashSet<Integer> getSelectedItems() {
        return mSelectedItems;
    }


    private HashSet<Integer> mSelectedItems;
    private DashBoardActionListener mListener;
    private Integer mDefaultColor;
    private int mDefaultBackground;
    private int mWidth;
    private int mSpanCnt;
    private int spacing;
    private LayoutInflater mInflater;
    private List<Item> mData;

    public final static int TYPE_GROUP = 0;
    public final static int TYPE_TEXT = 1;

    private final static String TAG = DashBoardAdapter.class.getSimpleName();
}
