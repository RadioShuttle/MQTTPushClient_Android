/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedList;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class OptionListAdapter extends RecyclerView.Adapter<OptionListAdapter.ViewHolder> {

    public OptionListAdapter(Context context, int textColor, int selColor) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mTextColor = textColor;
        mSelColor = selColor & 0x80FFFFFF;
        mRadioTintColor = ColorStateList.valueOf(mTextColor);
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.activity_dash_board_item_optionlist_row, parent, false);
        final ViewHolder vh = new ViewHolder(view);
        vh.label = view.findViewById(R.id.label);
        vh.radioUnchecked = view.findViewById(R.id.radioUnchecked);
        vh.radioChecked = view.findViewById(R.id.radioChecked);
        ImageViewCompat.setImageTintList(vh.radioUnchecked, mRadioTintColor);
        ImageViewCompat.setImageTintList(vh.radioChecked, mRadioTintColor);

        vh.label.setTextColor(mTextColor);

        StateListDrawable res = new StateListDrawable();
        res.setExitFadeDuration(400);
        // res.setAlpha(45);
        // res.addState(new int[]{android.R.attr.state_enabled}, new ColorDrawable(background));
        res.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(mSelColor));
        // res.addState(new int[]{}, new ColorDrawable(0));

        vh.itemView.setBackground(res);


        vh.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCallback != null && mData != null) {
                    int pos = vh.getAdapterPosition();
                    if (pos >= 0 && pos < mData.size()) {
                        OptionList.Option e = mData.get(pos);
                        mCallback.onOptionClicked(e,  Utils.equals(e.value, mSelectedValue));
                    }
                }
            }
        });

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OptionList.Option item = mData.get(position);
        holder.label.setText(Utils.isEmpty(item.displayValue) ? item.value : item.displayValue );

        if (!Utils.isEmpty(mSelectedValue) && Utils.equals(item.value, mSelectedValue)) {
            if (holder.radioChecked.getVisibility() != View.VISIBLE) {
                holder.radioChecked.setVisibility(View.VISIBLE);
            }
            if (holder.radioUnchecked.getVisibility() != View.INVISIBLE) {
                holder.radioUnchecked.setVisibility(View.INVISIBLE);
            }
        } else {
            if (holder.radioChecked.getVisibility() != View.INVISIBLE) {
                holder.radioChecked.setVisibility(View.INVISIBLE);
            }
            if (holder.radioUnchecked.getVisibility() != View.VISIBLE) {
                holder.radioUnchecked.setVisibility(View.VISIBLE);
            }
        }

    }

    public void setData(LinkedList<OptionList.Option> list) {
        mData = list;
        notifyDataSetChanged();
    }

    public void setSelection(String value) {
        if (Utils.equals(value, mSelectedValue)) {
            return;
        }
        int oldPos = -1;
        int newPos = -1;
        if (mData != null) {
            OptionList.Option entry;
            for(int i = 0; i < mData.size() && (oldPos == -1 || newPos == -1); i++) {
                entry = mData.get(i);
                if (entry != null) {
                    /* clear old selection */
                    if (Utils.equals(mSelectedValue, entry.value)) {
                        oldPos = i;
                    }
                    /* new selection */
                    if (Utils.equals(value, entry.value)) {
                        newPos = i;
                    }
                }
            }
        }
        mSelectedValue = value;
        if (oldPos != -1) {
            notifyItemChanged(oldPos, mData);
        }
        if (newPos != -1 && newPos != oldPos) {
            notifyItemChanged(newPos, mData);
        }
    }

    public interface Callback {
        void onOptionClicked(OptionList.Option o, boolean isSelected);
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        public TextView label;
        public ImageView radioUnchecked;
        public ImageView radioChecked;
    }

    protected Callback mCallback;
    protected String mSelectedValue;
    protected LayoutInflater mInflater;
    protected LinkedList<OptionList.Option> mData;
    protected Context mContext;
    protected int mSelColor;
    protected int mTextColor;
    protected ColorStateList mRadioTintColor;

    private final static String TAG = OptionList.Option.class.getSimpleName();

}
