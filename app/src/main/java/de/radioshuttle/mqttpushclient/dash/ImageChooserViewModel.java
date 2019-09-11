/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

public class ImageChooserViewModel extends AndroidViewModel {

    public ImageChooserViewModel(@NonNull Application application) {
        super(application);

        ImageDataSource.Factory factory = new ImageDataSource.Factory(this);
        mLiveDataInternalImages = new LivePagedListBuilder(factory, 100).build();
    }

    public final LiveData<PagedList<ImageResource>> mLiveDataInternalImages;

    public static class Factory implements ViewModelProvider.Factory {

        public Factory(Application app) {
            this.app = app;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new ImageChooserViewModel(app);
        }

        Application app;
    }

}
