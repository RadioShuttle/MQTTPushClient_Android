/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.paging.PagedList;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class ImageChooserAdapter extends PagedListAdapter<ImageResource, ImageChooserAdapter.ViewHolder> {

    public ImageChooserAdapter(Context context, int cellWidth, boolean selectionMode) {
        this(context, cellWidth, null, selectionMode);
    }

    public ImageChooserAdapter(Context context, int cellWidth, List<String> lockedResources, boolean selectionMode) {
        super(DIFF_CALLBACK);
        mInflater = LayoutInflater.from(context);
        if (context instanceof OnImageSelectListener) {
            callback = (OnImageSelectListener) context;
        }
        mShowLabels = true; //TODO
        mCellWidth = cellWidth;
        mLockedResources = new HashSet<>();
        if (lockedResources != null && lockedResources.size() > 0) {
            mLockedResources.addAll(lockedResources);
        }
        mSelectionMode = selectionMode;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_NO_SELECTION) { // no image
            view = mInflater.inflate(R.layout.activity_image_chooser_cell_none, parent, false);
        } else { // VIEW_TYPE_IMAGE_BUTTON
            view = mInflater.inflate(R.layout.activity_image_chooser_cell, parent, false);
        }
        if (view.getLayoutParams() != null && mCellWidth > 0) {
            view.getLayoutParams().width = mCellWidth;
        }
        final ViewHolder holder = new ViewHolder(view);
        if (viewType == VIEW_TYPE_NO_SELECTION) {
            holder.noImageButton = view.findViewById(R.id.noneButton);
        } else { // VIEW_TYPE_IMAGE_BUTTON
            holder.image = view.findViewById(R.id.image);
            holder.label = view.findViewById(R.id.label);
            holder.lockedImage = view.findViewById(R.id.resourceLocked);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (callback != null && mSelectionMode) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        ImageResource item = getItem(pos);
                        String uri = item.uri;
                        callback.onImageSelected(pos, uri);
                    }
                }
            }
        });

        if (!mSelectionMode) {
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    boolean consumed = false;
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && pos >= 0 && pos < getItemCount()) {
                        ImageResource item = getItem(pos);
                        if (ImageResource.isExternalResource(item.uri)) {
                            if (mLockedResources.contains(item.uri)) {
                                mLockedResources.remove(item.uri);
                            } else {
                                mLockedResources.add(item.uri);
                            }
                            ImageChooserAdapter.this.notifyItemChanged(pos, item);
                            consumed = true;
                        }

                    }
                    return consumed;
                }
            });
        }

        return holder;
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ImageChooserAdapter.ViewHolder vh = (ImageChooserAdapter.ViewHolder) holder;

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
            ImageResource item = getItem(position);
            String uri = item.uri;
            // Log.d(TAG, "onBindViewHolder: " + position + ", " + item.uri);
            String label = null;
            if (!Utils.isEmpty(uri)) {
                label = ImageResource.getLabel(uri);
            }
            vh.label.setText(label);

            vh.image.setImageDrawable(item.drawable);
            /* remove tint if user image */
            if (!ImageResource.isInternalResource(uri)) {
                ImageViewCompat.setImageTintList(vh.image, null);
            }
            if (!mSelectionMode && ImageResource.isExternalResource(uri)) {
                if (mLockedResources.contains(uri) && vh.lockedImage.getVisibility() != View.VISIBLE) {
                    vh.lockedImage.setVisibility(View.VISIBLE);
                }
                if (!mLockedResources.contains(uri) && vh.lockedImage.getVisibility() != View.GONE) {
                    vh.lockedImage.setVisibility(View.GONE);
                }
            }


        }
    }

    @Override
    public int getItemViewType(int position) {
        return mSelectionMode && position == 0 ? VIEW_TYPE_NO_SELECTION : VIEW_TYPE_IMAGE_BUTTON;
    }

    public ArrayList<String> getLockedResources() {
        return new ArrayList<>(mLockedResources);
    }

    private static DiffUtil.ItemCallback<ImageResource> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ImageResource>() {
                @Override
                public boolean areItemsTheSame(ImageResource o, ImageResource n) {
                    // Log.d(TAG, "areItemsTheSame = " + o.uri + ", " + n.uri + " bool: " + Utils.equals(o.uri, n.uri));
                    return Utils.equals(o.uri, n.uri);
                }

                @Override
                public boolean areContentsTheSame(ImageResource o,
                                                  ImageResource n) {
                    // Log.d(TAG, "areContentsTheSame = " + o.uri + ", " + n.uri + " bool: " + (o.drawable == n.drawable));
                    return o.drawable == n.drawable && ImageResource.isInternalResource(o.uri);
                }
            };

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View v) {
            super(v);
        }
        public ImageView image;
        public ImageView lockedImage;
        public Button noImageButton;
        public TextView label;
    }

    public interface OnImageSelectListener {
        void onImageSelected(int pos, String uri);
        void onSelectionCleared();
        int NONE = 0;
    }

    OnImageSelectListener callback;

    int mCellWidth;
    LayoutInflater mInflater;
    boolean mShowLabels;
    HashSet<String> mLockedResources;
    boolean mSelectionMode;

    protected static final int VIEW_TYPE_NO_SELECTION = 1;
    protected static final int VIEW_TYPE_IMAGE_BUTTON = 2;

    protected final static String TAG = ImageChooserAdapter.class.getSimpleName();
}
