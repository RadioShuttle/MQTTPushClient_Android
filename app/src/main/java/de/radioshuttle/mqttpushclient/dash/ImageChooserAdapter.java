/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import de.radioshuttle.mqttpushclient.R;

public class ImageChooserAdapter extends PagedListAdapter<ImageResource, ImageChooserAdapter.ViewHolder> {

    public ImageChooserAdapter(Context context) {
        super(DIFF_CALLBACK);
        mInflater = LayoutInflater.from(context);
        if (context instanceof OnImageSelectListener) {
            callback = (OnImageSelectListener) context;
        }
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
        final ViewHolder holder = new ViewHolder(view);
        if (viewType == VIEW_TYPE_NO_SELECTION) {
            holder.noImageButton = view.findViewById(R.id.noneButton);
        } else { // VIEW_TYPE_IMAGE_BUTTON
            holder.image = view.findViewById(R.id.image);
        }
        return holder;
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
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
            ImageResource item = getItem(position - 1);
            final String uri = item.uri;

            vh.image.setImageDrawable(item.drawable);
            vh.image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (callback != null) {
                        callback.onImageSelected(position, uri);
                    }
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return super.getItemCount() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_NO_SELECTION : VIEW_TYPE_IMAGE_BUTTON;
    }

    private static DiffUtil.ItemCallback<ImageResource> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ImageResource>() {
                @Override
                public boolean areItemsTheSame(ImageResource o, ImageResource n) {
                    return o.tag == n.tag;
                }

                @Override
                public boolean areContentsTheSame(ImageResource o,
                                                  ImageResource n) {
                    return o.drawable == n.drawable;
                }
            };

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

    protected static final int VIEW_TYPE_NO_SELECTION = 1;
    protected static final int VIEW_TYPE_IMAGE_BUTTON = 2;

    protected final static String TAG = ImageChooserAdapter.class.getSimpleName();
}
