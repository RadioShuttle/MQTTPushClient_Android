/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class ImageChooserActivity extends AppCompatActivity  implements ImageChooserAdapter.OnImageSelectListener  {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_chooser);

        setTitle(R.string.title_chooser);

        mViewModel = ViewModelProviders.of(this, new ImageChooserViewModel.Factory(getApplication())).get(ImageChooserViewModel.class);

        /* internal images */
        mInternalImageList = findViewById(R.id.internalImageList);
        DisplayMetrics dm = getResources().getDisplayMetrics();

        int image_width_px = getResources().getDimensionPixelSize(R.dimen.image_selection_cell_image_width);
        int image_cell_margin = getResources().getDimensionPixelSize(R.dimen.image_selection_cell_margin);
        int container_margin = (int) (12f * 2f * dm.density); // 16dp (minus cell margin 4 = 12) * 2
        int spanCount = (dm.widthPixels - container_margin) / (image_width_px + image_cell_margin * 2);
        int maxCellWidth = (dm.widthPixels - container_margin) / spanCount;

        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        mInternalImageList.setLayoutManager(layoutManager);
        mInternalImageAdapter = new ImageChooserAdapter(this, maxCellWidth);
        mInternalImageList.setAdapter(mInternalImageAdapter);
        mViewModel.mLiveDataInternalImages.observe(this, new Observer<PagedList<ImageResource>>() {
            @Override
            public void onChanged(PagedList<ImageResource> imageResources) {
                mInternalImageAdapter.submitList(imageResources);
            }
        });

        /* user images */
        mUserImageList = findViewById(R.id.userImageList);
        layoutManager = new GridLayoutManager(this, spanCount);
        mUserImageList.setLayoutManager(layoutManager);
        mUserImageAdapter = new ImageChooserAdapter(this, maxCellWidth);

        mUserImageList.setAdapter(mUserImageAdapter);
        mViewModel.mLiveDataUserImages.observe(this, new Observer<PagedList<ImageResource>>() {
            @Override
            public void onChanged(PagedList<ImageResource> imageResources) {
                /*
                Log.d(TAG, "Paged list entries: ");
                for(int i = 0; i < imageResources.size(); i++) {
                    Log.d(TAG, "" + imageResources.get(i).uri);
                }
                */
                mUserImageAdapter.submitList(imageResources);
            }
        });

        /* display import error */
        mViewModel.mImportedFilesErrorLiveData.observe(this, new Observer<JSONArray>() {
            @Override
            public void onChanged(JSONArray jsonArray) {
                if (jsonArray != null) {
                    int status;
                    JSONObject e;
                    int error = 0;
                    for(int i = 0; i < jsonArray.length(); i++) {
                        try {
                            e = jsonArray.getJSONObject(i);
                            status = e.optInt("status", -1);
                            if (status > 0) {
                                if (status > error) { // only show one error (with most importance)
                                    error = status;
                                }
                            }
                        } catch (JSONException ex) {
                            Log.d(TAG, "Error parsing import result", ex);
                        }
                    }
                    if (error > 0) {
                        String msg = null;
                        switch (error) {
                            case ImportFiles.STATUS_SECURITY_ERROR:
                                msg = getString(R.string.import_error_security);
                                break;
                            case ImportFiles.STATUS_LOWMEM_ERROR:
                                msg = getString(R.string.import_error_lowmem);
                                break;
                            case ImportFiles.STATUS_FORMAT_ERROR:
                                msg = getString(R.string.import_error_format);
                                break;
                            default:
                                msg = getString(R.string.import_error);
                                break;
                        }
                        showErrorMsg(msg);
                    } else {
                        if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                            mSnackbar.dismiss();
                        }
                    }
                    // only use once
                    mViewModel.mImportedFilesErrorLiveData.setValue(null);
                }
            }
        });

        /* tabs */
        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                mSelectedTAB = tab.getPosition();
                Log.i(TAG, "tab selected: " + mSelectedTAB);
                updateView();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        Bundle receivedArgs = getIntent().getExtras();
        if (savedInstanceState == null) {
            String u = receivedArgs.getString(ARG_RESOURCE_URI);
            if (ImageResource.isExternalResource(u)) {
                mSelectedTAB = TAB_USER;
            } else {
                mSelectedTAB = TAB_INTERNAL;
            }
        } else {
            mSelectedTAB = savedInstanceState.getInt(KEY_SELECTED_TAB);
        }

        if (tabLayout.getSelectedTabPosition() != mSelectedTAB) {
            tabLayout.getTabAt(mSelectedTAB).select();
        }
        updateView();

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled;
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                handled = true;
                break;
            case R.id.menu_import :
                handled = true;
                importFiles();
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void updateView() {
        if (mSelectedTAB == TAB_INTERNAL) {
            if (mInternalImageList.getVisibility() != View.VISIBLE) {
                mInternalImageList.setVisibility(View.VISIBLE);
            }
            if (mUserImageList.getVisibility() != View.GONE) {
                mUserImageList.setVisibility(View.GONE);
            }
        } else { // if (mSelectedTAB == TAB_USER)
            if (mInternalImageList.getVisibility() != View.GONE) {
                mInternalImageList.setVisibility(View.GONE);
            }
            if (mUserImageList.getVisibility() != View.VISIBLE) {
                mUserImageList.setVisibility(View.VISIBLE);
            }
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.menu_import);
        if (m != null) {
            m.setVisible(mSelectedTAB == TAB_USER);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_image_chooser, menu);
        return true;
    }


    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void handleBackPressed() {
        /* if user did not click an image, treat as canceled */
        setResult(AppCompatActivity.RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_SELECTED_TAB, mSelectedTAB);
    }

    @Override
    public void onImageSelected(int pos, String uri) {
        Bundle receivedArgs = getIntent().getExtras();
        Intent data = new Intent();
        if (receivedArgs != null && receivedArgs.getInt(ARG_CTRL_IDX) != -1) {
            data.putExtra(ARG_CTRL_IDX, receivedArgs.getInt(ARG_CTRL_IDX));
        }
        data.putExtra(ARG_RESOURCE_URI, uri);
        setResult(AppCompatActivity.RESULT_OK, data);
        finish();
    }

    @Override
    public void onSelectionCleared() {
        Bundle receivedArgs = getIntent().getExtras();
        Intent data = new Intent();
        if (receivedArgs != null && receivedArgs.getInt(ARG_CTRL_IDX) != -1) {
            data.putExtra(ARG_CTRL_IDX, receivedArgs.getInt(ARG_CTRL_IDX));
        }
        // status RESULT_OK and no return of ARG_RESOURCE_URI indicates "no image"
        setResult(AppCompatActivity.RESULT_OK, data);
        finish();
    }

    protected void importFiles() {
        Intent requestFiles = new Intent(Intent.ACTION_GET_CONTENT);
        requestFiles.setType("image/*");
        requestFiles.addCategory(Intent.CATEGORY_OPENABLE);
        requestFiles.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            requestFiles.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (requestFiles.resolveActivity(getPackageManager()) != null) {
            Intent browser = Intent.createChooser(requestFiles, getString(R.string.action_import));
            startActivityForResult(browser, RC_IMPORT_IMAGE);
        } else {
            Toast.makeText(getApplicationContext(), R.string.import_no_files, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "import ...");
        if (requestCode == RC_IMPORT_IMAGE) {

            if (resultCode == Activity.RESULT_OK) {
                boolean hasClipData = false;
                /* multiple selections supported since android 4.3 */
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    ClipData files = data.getClipData();

                    if (files != null) {
                        Utils.executor.submit(new ImportFiles(this, files));
                        hasClipData = true;
                    }
                }
                if (!hasClipData) {
                    Uri fileUri = data.getData();
                    if (fileUri != null) {
                        Utils.executor.submit(new ImportFiles(this, fileUri));
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // handle cancel
            }
        }
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.root_view);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    ImageChooserViewModel mViewModel;
    Snackbar mSnackbar;

    protected int mSelectedTAB;
    protected RecyclerView mInternalImageList;
    protected ImageChooserAdapter mInternalImageAdapter;
    protected ImageChooserAdapter mUserImageAdapter;
    protected RecyclerView mUserImageList;

    protected final static int TAB_INTERNAL = 0;
    protected final static int TAB_USER = 1;

    public final static String ARG_CTRL_IDX = "ARG_CTRL_IDX";
    public final static String ARG_RESOURCE_URI = "ARG_RESOURCE_URI";

    protected final static String KEY_SELECTED_TAB = " KEY_SELECTED_TAB";

    private final static int RC_IMPORT_IMAGE = 1;
    public final static String TAG = ImageChooserActivity.class.getSimpleName();

}
