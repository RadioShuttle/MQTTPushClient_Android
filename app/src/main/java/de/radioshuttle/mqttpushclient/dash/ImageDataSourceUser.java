/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.paging.DataSource;
import androidx.paging.PositionalDataSource;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.radioshuttle.net.Cmd;
import de.radioshuttle.utils.HeliosUTF8Decoder;
import de.radioshuttle.utils.HeliosUTF8Encoder;
import de.radioshuttle.utils.Utils;

public class ImageDataSourceUser  extends PositionalDataSource<ImageResource> {

    public ImageDataSourceUser(ImageChooserViewModel viewModel) {
        mViewModel = viewModel;
        mUserImageResources = new ArrayList<>();
    }

    private List<ImageResource> loadRangeInternal(int startPosition, int loadCount) {
        ArrayList<ImageResource> result = new ArrayList<>();
        ImageResource r, resource;
        String filename;
        HeliosUTF8Encoder dec = new HeliosUTF8Encoder();
        for(int i = startPosition; i < startPosition + loadCount && i < mUserImageResources.size(); i++) {
            r = mUserImageResources.get(i);
            if (i == 0) {
                result.add(r);  // selection none entry
                continue;
            }
            try {
                URI u = new URI(r.uri);
                resource = new ImageResource();
                resource.uri = r.uri;
                resource.label = r.label;
                resource.id = r.id;
                resource.drawable = null;
                result.add(resource);
                if (ImageResource.isExternalResource(r.uri)) {
                    filename = u.getPath();
                    if (filename.startsWith("/")) {
                        filename = filename.substring(1);
                    }
                    File imageFile = null;
                    if (ImageResource.isImportedResource(r.uri)) {
                        imageFile = new File(ImportFiles.getImportedFilesDir(mViewModel.getApplication()), filename);
                    } else { // user resource
                        filename = dec.format(filename) + '.' + Cmd.DASH512_PNG;
                        imageFile = new File(ImportFiles.getUserFilesDir(mViewModel.getApplication()), filename);
                    }
                    resource.drawable = new BitmapDrawable(
                            mViewModel.getApplication().getResources(),
                            BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
                    Log.d(TAG, "datasource user: loaded image file: " + filename);

                }
            } catch (Exception e) {
                Log.d(TAG, "error loading internal image", e);
            }
        }
        return result;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback callback) {
        mUserImageResources.clear();
        mUserImageResources.add(new ImageResource()); // selection none entry

        //TODO: add user images (=already used images) to list (they should be shorted by number)
        List<ImageResource> serverImages = new ArrayList<>();
        File userImagesDir = ImportFiles.getUserFilesDir(mViewModel.getApplication());
        String[] userFiles = userImagesDir.list();
        if (userImagesDir != null) {
            ImageResource r;
            String resourcename;
            HeliosUTF8Decoder dec = new HeliosUTF8Decoder();
            for(String userFile : userFiles) {
                r = new ImageResource();
                try {
                    resourcename = ImageResource.removeExtension(userFile);
                    resourcename = dec.format(resourcename);
                    r.uri = "res://user/" + Utils.urlEncode(resourcename);
                    r.id = 0;
                    r.label = resourcename;
                    serverImages.add(r);
                    Log.d(TAG, "label: " + r.label + ", id: " + r.id + ", url: " + r.uri); //TODO: raus
                } catch(Exception e) {
                    Log.d(TAG, "Error parsing imported filename", e);
                }
            }
        }
        Collections.sort(serverImages, new ImageResource.LabelComparator());
        mUserImageResources.addAll(serverImages);

        HeliosUTF8Decoder mFilenameDecoder = new HeliosUTF8Decoder();

        List<ImageResource> importedImages = new ArrayList<>();
        File importedFilesDir = ImportFiles.getImportedFilesDir(mViewModel.getApplication());
        String[] importedFiles = importedFilesDir.list();
        int idx;
        if (importedFiles != null) {
            ImageResource r;
            String s;
            for(int i = 0; i < importedFiles.length; i++) {
                s = importedFiles[i];
                r = new ImageResource();
                // prefix
                idx = s.indexOf('_');
                if (idx != -1) {
                    try {
                        r.uri="res://imported/" + Utils.urlEncode(s);
                        r.id = Integer.valueOf(s.substring(0, idx));
                        r.label = mFilenameDecoder.format(s.substring(idx + 1));
                        Log.d(TAG, "label: " + r.label + ", id: " + r.id + ", url: " + r.uri); //TODO: raus
                    } catch(Exception e) {
                        Log.d(TAG, "Error parsing imported filename", e);
                    }
                } else {
                    /* should never occur */
                    continue;
                }
                importedImages.add(r);
            }
        }
        Collections.sort(importedImages, new ImageResource.IDComparator());
        mUserImageResources.addAll(importedImages);

        int totalCount = mUserImageResources.size();
        int position = computeInitialLoadPosition(params, totalCount);
        int loadSize = computeInitialLoadSize(params, position, totalCount);
        callback.onResult(loadRangeInternal(position, loadSize), position, totalCount);
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback callback) {
        callback.onResult(loadRangeInternal(params.startPosition, params.loadSize));
    }

    ArrayList<ImageResource> mUserImageResources;

    ImageChooserViewModel mViewModel;

    public final static class Factory extends DataSource.Factory<Integer, Drawable> {

        public Factory(ImageChooserViewModel viewModel) {
            mModel = viewModel;
        }

        @Override
        public DataSource create() {
            return new ImageDataSourceUser(mModel);
        }
        ImageChooserViewModel mModel;
    }

    private final static String TAG = ImageDataSourceUser.class.getSimpleName();
}
