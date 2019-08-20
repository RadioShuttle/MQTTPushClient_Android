/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */


package de.radioshuttle.mqttpushclient.dash;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class ColorPickerDialog extends DialogFragment {

    static ColorPickerDialog newInstance(String name, ArrayList<Integer> colors, int labelBorderColor, ArrayList<String> labels) {
        Bundle args = new Bundle();
        args.putString("name", name == null ? "colors" : name);
        args.putInt("border", labelBorderColor);
        args.putIntegerArrayList("palette", colors == null ? simplePalette() : colors);
        if (labels != null) {
            args.putStringArrayList("labels", labels);
        }
        ColorPickerDialog dlg = new ColorPickerDialog();
        dlg.setArguments(args);

        return dlg;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // builder.setTitle("test");
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View  v = inflater.inflate(R.layout.dialog_color_body, null);


        mColorList = v.findViewById(R.id.colorList);
        if (mColorList != null) {

            final GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
            mColorList.setLayoutManager(layoutManager);

            Bundle args = getArguments();

            ColorListAdapter adapter = new ColorListAdapter(this);
            adapter.setData(args.getIntegerArrayList("palette"), args.getStringArrayList("labels"));
            mColorList.setAdapter(adapter);


        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        final int itemWidth = (int) ((float) 64 * dm.density);  //TODO: size and padding from layoutParas
        final int padding = (int) ((float) 12f * 2f * dm.density);

        builder.setView(v);
        v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int oldWidth = oldRight - oldLeft;
                int newWidth = right - left;
                if (oldWidth != newWidth) {
                    int spanCount = (newWidth - padding) / itemWidth;
                    ((GridLayoutManager) mColorList.getLayoutManager()).setSpanCount(spanCount);
                }
            }
        });

        return builder.create();

    }


    protected void onColorSelected(int idx, int color, String type) {
        if (getActivity() instanceof Callback) {
            ((Callback) getActivity()).onColorSelected(idx, color, type);
        }
    }

    public static ArrayList<Integer> simplePalette() {
        return optimum16Palette();
    }

    // see /http://alumni.media.mit.edu/~wad/color/palette.html
    public static ArrayList<Integer> optimum16Palette() {
        ArrayList<Integer> palette = new ArrayList<>();

        palette.add(Color.rgb(255, 255, 255)); // White
        palette.add(Color.rgb(160, 160, 160)); // Lt. Gray
        palette.add(Color.rgb(87, 87, 87)); // Dk. Gray
        palette.add(Color.rgb(0, 0, 0)); // Black

        palette.add(Color.rgb(233, 222, 187)); //  Tan
        palette.add(Color.rgb(255, 238, 51)); //  Yellow
        palette.add(Color.rgb(255, 146, 51)); // Orange

        palette.add(Color.rgb(173, 35, 35)); // Red
        palette.add(Color.rgb(129, 74, 25)); // Brown

        palette.add(Color.rgb(129, 197, 122)); // Lt. Green
        palette.add(Color.rgb(29, 105, 20)); // Green

        palette.add(Color.rgb(255, 205, 243)); // Pink
        palette.add(Color.rgb(129, 38, 192)); // Purple

        palette.add(Color.rgb(41, 208, 208)); // Cyan
        palette.add(Color.rgb(157, 175, 255)); // Lt. Blue
        palette.add(Color.rgb(42, 75, 215)); // Blue

        return palette;
    }

    interface Callback {
        void onColorSelected(int idx, int color, String name);
    }

    protected RecyclerView mColorList;

    public static class ColorListAdapter extends RecyclerView.Adapter {

        public ColorListAdapter(ColorPickerDialog dlg) {
            mDialog = dlg;
            mInflater = LayoutInflater.from(mDialog.getContext());
            Bundle args = dlg.getArguments();
            mBorderColor = args.getInt("border");
            mPaletteName = args.getString("name", "colors");
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

            View view = mInflater.inflate(R.layout.dialog_color_cell, parent, false);
            final ColorListAdapter.ViewHolder holder = new ViewHolder(view);
            holder.colorLabel = view.findViewById(R.id.colorLabel);
            holder.label = view.findViewById(R.id.label);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                   @Override
                   public void onClick(View v) {
                       int pos = holder.getAdapterPosition();
                       if (pos != RecyclerView.NO_POSITION) {
                           mDialog.onColorSelected(pos, mData.get(pos), mPaletteName);
                           mDialog.dismiss();
                       }
                   }
               }
            );

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder vhholder, int position) {
            Integer color = mData.get(position);
            ColorListAdapter.ViewHolder holder = (ColorListAdapter.ViewHolder) vhholder;
            holder.colorLabel.setColor(color, mBorderColor);

            boolean showLabel = false;
            if (mLabels != null && position >= 0 && position < mLabels.size()) {
                String label = mLabels.get(position);
                if (!Utils.isEmpty(label)) {
                    int c = mData.get(position);
                    double l = ColorUtils.calculateLuminance(c);
                    holder.label.setText(label);
                    if (l < .25d) {
                        holder.label.setTextColor(0xFFFFFFFF);
                    } else {
                        holder.label.setTextColor(0xFF000000);
                    }
                    holder.label.setVisibility(View.VISIBLE);
                    showLabel = true;
                }
            }
            if (showLabel && holder.label.getVisibility() != View.VISIBLE) {
                holder.label.setVisibility(View.VISIBLE);
            }
            if (!showLabel && holder.label.getVisibility() != View.GONE) {
                holder.label.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return mData == null ? 0 : mData.size();
        }

        public void setData(List<Integer> colors, ArrayList<String> labels) {
            mData = colors;
            mLabels = labels;
            notifyDataSetChanged();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(View v) {
                super(v);
            }
            ColorLabel colorLabel;
            TextView label;
        }

        String mPaletteName;
        int mBorderColor;
        ColorPickerDialog mDialog;
        LayoutInflater mInflater;
        List<Integer> mData;
        ArrayList<String> mLabels;

    }


    private final static String TAG = ColorPickerDialog.class.getSimpleName();
}
