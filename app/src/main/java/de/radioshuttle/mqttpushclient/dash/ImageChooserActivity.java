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

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

import de.radioshuttle.mqttpushclient.R;

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
        int spanCount = dm.widthPixels / (image_width_px + image_cell_margin * 2);

        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        mInternalImageList.setLayoutManager(layoutManager);
        mInternalImageAdapter = new ImageChooserAdapter(this);
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
        // ImageChooserAdapter userImagesAdaper = new ImageChooserAdapter(this); //TODO:
        // mUserImageList.setAdapter(userImagesAdaper);


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

        mSelectedTAB = (savedInstanceState == null ? TAB_INTERNAL : savedInstanceState.getInt(KEY_SELECTED_TAB));
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
                Toast.makeText(getApplicationContext(), "Not impelemented yet.", Toast.LENGTH_LONG).show();
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
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.menu_import);
        if (m != null) {
            //TODO: consider hiding menu item when TAB internal is active
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

    ImageChooserViewModel mViewModel;

    protected int mSelectedTAB;
    protected RecyclerView mInternalImageList;
    protected ImageChooserAdapter mInternalImageAdapter;
    protected RecyclerView mUserImageList;

    protected final static int TAB_INTERNAL = 0;
    protected final static int TAB_USER = 1;

    public final static String ARG_CTRL_IDX = "ARG_CTRL_IDX";
    public final static String ARG_RESOURCE_URI = "ARG_RESOURCE_URI";

    protected final static String KEY_SELECTED_TAB = " KEY_SELECTED_TAB";

    public final static String TAG = ImageChooserActivity.class.getSimpleName();

}
