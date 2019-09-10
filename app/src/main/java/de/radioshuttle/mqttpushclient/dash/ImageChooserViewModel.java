package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ImageChooserViewModel extends AndroidViewModel {

    public ImageChooserViewModel(@NonNull Application application) {
        super(application);
    }

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
