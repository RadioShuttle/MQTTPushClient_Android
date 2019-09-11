/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ImageDataSource extends PositionalDataSource<ImageResource> {


    public ImageDataSource(ImageChooserViewModel viewModel) {
        mViewModel = viewModel;
    }

    private List<ImageResource> loadRangeInternal(int startPosition, int loadCount) {
        ArrayList<ImageResource> result = new ArrayList<>();
        Iterator<Map.Entry<String, Integer>> it = IconHelper.INTENRAL_ICONS.entrySet().iterator();
        Map.Entry<String, Integer> entry;
        int i = 0;
        ImageResource res;
        while(it.hasNext() && i < startPosition + loadCount) {
            if (i >= startPosition) {
                entry = it.next();
                res = new ImageResource();
                res.uri = entry.getKey();
                res.tag = entry.getValue();
                res.drawable = VectorDrawableCompat.create(
                        mViewModel.getApplication().getResources(), res.tag, null);
                result.add(res);
            }
            i++;
        }
        return result;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback callback) {
        int totalCount = IconHelper.INTENRAL_ICONS.size();
        int position = computeInitialLoadPosition(params, totalCount);
        int loadSize = computeInitialLoadSize(params, position, totalCount);
        callback.onResult(loadRangeInternal(position, loadSize), position, totalCount);
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback callback) {
        callback.onResult(loadRangeInternal(params.startPosition, params.loadSize));
    }

    ImageChooserViewModel mViewModel;

    public final static class Factory extends DataSource.Factory<Integer, Drawable> {

        public Factory(ImageChooserViewModel viewModel) {
            mModel = viewModel;
        }

        @Override
        public DataSource create() {
            return new ImageDataSource(mModel);
        }
        ImageChooserViewModel mModel;
    }

}
