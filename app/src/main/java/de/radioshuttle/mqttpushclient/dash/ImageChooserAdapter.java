/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import de.radioshuttle.mqttpushclient.R;

public class ImageChooserAdapter extends RecyclerView.Adapter {

    public ImageChooserAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
        if (context instanceof OnImageSelectListener) {
            callback = (OnImageSelectListener) context;
        }
    }

    public void setInternalImageData(LinkedHashMap<String, Integer> resourceIDs) {
        mInternalResourceIDs = resourceIDs;
        if (resourceIDs == null) {
            mResouceIDs = null;
        } else {
            mResouceIDs = new ArrayList<>(mInternalResourceIDs.values());
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_NO_SELECTION) { // no image
            view = mInflater.inflate(R.layout.activity_image_chooser_cell_none, parent, false);
        } else { // VIEW_TYPE_IMAGE_BUTTON
            view = mInflater.inflate(R.layout.activity_image_chooser_cell, parent, false);
        }
        final ViewHolder holder = new ViewHolder(view);
        if (viewType == VIEW_TYPE_NO_SELECTION) {
            holder.noImageButton = view.findViewById(R.id.noneButton);
        } else { // VIEW_TYPE_IMAGE_BUTTON
            holder.image = view.findViewById(R.id.image);
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        ImageChooserAdapter.ViewHolder vh = (ImageChooserAdapter.ViewHolder) holder;

        if (getItemViewType(position) == VIEW_TYPE_NO_SELECTION) {
            vh.noImageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) {
                        callback.onSelectionCleared();
                    }
                }
            });
        } else { // VIEW_TYPE_IMAGE_BUTTON
            vh.image.setImageResource(mResouceIDs.get(position - 1));
            vh.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) {
                        if (mInternalResourceIDs != null) {
                            String uri = IconHelper.getURIForResourceID(mResouceIDs.get(position - 1));
                            // Log.d(TAG, "uri selected: " + uri);
                            callback.onImageSelected(position, uri);
                        } else {
                            //TODO:
                            callback.onImageSelected(position, null);
                        }
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return (mInternalResourceIDs != null ? mInternalResourceIDs.size() : 0) + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_NO_SELECTION : VIEW_TYPE_IMAGE_BUTTON;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        public ImageButton image;
        public Button noImageButton;
    }

    public interface OnImageSelectListener {
        void onImageSelected(int pos, String uri);
        void onSelectionCleared();
        int NONE = 0;
    }

    OnImageSelectListener callback;

    LayoutInflater mInflater;
    LinkedHashMap<String, Integer> mInternalResourceIDs;
    List<Integer> mResouceIDs;

    protected static final int VIEW_TYPE_NO_SELECTION = 1;
    protected static final int VIEW_TYPE_IMAGE_BUTTON = 2;

    protected final static String TAG = ImageChooserAdapter.class.getSimpleName();
}
