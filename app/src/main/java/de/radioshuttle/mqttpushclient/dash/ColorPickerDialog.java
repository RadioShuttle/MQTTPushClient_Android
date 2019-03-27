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

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.mqttpushclient.R;

public class ColorPickerDialog extends DialogFragment {

    static ColorPickerDialog newInstance(String name, ArrayList<Integer> colors, int labelBorderColor) {
        Bundle args = new Bundle();
        args.putString("name", name == null ? "colors" : name);
        args.putInt("border", labelBorderColor);
        args.putIntegerArrayList("palette", colors == null ? simplePalette() : colors);
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
            adapter.setData(args.getIntegerArrayList("palette"));
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

    protected static ArrayList<Integer> simplePalette() {
        ArrayList<Integer> palette = new ArrayList<>();

        //TODO: make a decent palette
        palette.add(Color.WHITE);
        palette.add(Color.LTGRAY);
        palette.add(Color.GRAY);
        palette.add(Color.DKGRAY);
        palette.add(Color.BLACK);
        palette.add(Color.YELLOW);
        palette.add(Color.RED);
        palette.add(Color.GREEN);
        palette.add(Color.CYAN);
        palette.add(Color.BLUE);
        palette.add(Color.MAGENTA);

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
            holder.colorLabel = (ColorLabel) view;
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
            ((ViewHolder) vhholder).colorLabel.setColor(color, mBorderColor);
        }

        @Override
        public int getItemCount() {
            return mData == null ? 0 : mData.size();
        }

        public void setData(List<Integer> colors) {
            mData = colors;
            notifyDataSetChanged();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(View v) {
                super(v);
            }
            ColorLabel colorLabel;
        }

        String mPaletteName;
        int mBorderColor;
        ColorPickerDialog mDialog;
        LayoutInflater mInflater;
        List<Integer> mData;

    }


    private final static String TAG = ColorPickerDialog.class.getSimpleName();
}
