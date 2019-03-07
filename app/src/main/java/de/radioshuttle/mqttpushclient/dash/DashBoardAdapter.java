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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.mqttpushclient.R;

public class DashBoardAdapter extends RecyclerView.Adapter {

    public DashBoardAdapter(AppCompatActivity activity, int width, int spanCount) {
        mInflater = activity.getLayoutInflater();
        mData = new ArrayList<>();
        mWidth = width;

        if (Build.VERSION.SDK_INT >= 23) {
            mDefaultBackground = activity.getResources().getColor(R.color.dashboad_item_background, null);
        } else {
            mDefaultBackground = activity.getResources().getColor(R.color.dashboad_item_background);
        }
        spacing = activity.getResources().getDimensionPixelSize(R.dimen.dashboard_spacing);
        mSpanCnt = spanCount;
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
        if (viewType == Item.TYPE_TEXT) {
            view = mInflater.inflate(R.layout.activity_dash_board_item_text, parent, false);
            label = view.findViewById(R.id.name);
        } else {
            view = mInflater.inflate(R.layout.activity_dash_board_item_group, parent, false);
            label = view.findViewById(R.id.name);
        }


        final ViewHolder holder = new ViewHolder(view);
        holder.label = label;

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    if (mListener != null && mData != null && pos < mData.size()) {
                        Item item = mData.get(pos);
                        mListener.onItemClicked(item);
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
        if (item.getType() == Item.TYPE_TEXT || item.getType() == Item.TYPE_HEADER) {
            h.label.setText(item.label);
        }

        h.itemView.setBackgroundColor(mDefaultBackground); // default background color

        ViewGroup.LayoutParams lp = h.itemView.getLayoutParams();
        if (item.getType() != Item.TYPE_HEADER && (lp.width != mWidth || lp.height != mWidth)) {
            lp.width = mWidth;
            lp.height = mWidth;
            h.itemView.setLayoutParams(lp);
        }

        if (item.getType() == Item.TYPE_HEADER ) {
            if (lp.width != mSpanCnt * mWidth) {
                lp.width = mSpanCnt * mWidth + (mSpanCnt - 1) * spacing * 2;
                h.itemView.setLayoutParams(lp);
            }
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        TextView label;
    }

    @Override
    public int getItemViewType(int position) {
        int type = 0;
        if (mData != null && position < mData.size()) {
            type = mData.get(position).getType();
        }
        return type;
    }

    private DashBoardActionListener mListener;
    private int mDefaultBackground;
    private int mWidth;
    private int mSpanCnt;
    private int spacing;
    private LayoutInflater mInflater;
    private List<Item> mData;

    private final static String TAG = DashBoardAdapter.class.getSimpleName();
}
