/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.app.Activity;
import android.content.ActivityNotFoundException;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import de.radioshuttle.mqttpushclient.CertificateErrorDialog;
import de.radioshuttle.mqttpushclient.HelpActivity;
import de.radioshuttle.mqttpushclient.InsecureConnectionDialog;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.MQTTException;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity.ARG_ACCOUNT;
import static de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity.ARG_DASHBOARD;
import static de.radioshuttle.mqttpushclient.dash.DashBoardEditActivity.ARG_DASHBOARD_VERSION;

public class ImageChooserActivity extends AppCompatActivity  implements ImageChooserAdapter.OnImageSelectListener,
        Observer<Request> {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_chooser);
        mSwipeRefreshLayout = findViewById(R.id.root_view);
        mSwipeRefreshLayout.setEnabled(false);


        Bundle receivedArgs = getIntent().getExtras();
        boolean selectionMode = receivedArgs != null && receivedArgs.getInt(ARG_CTRL_IDX, -1) >= 0;
        String accountDir = receivedArgs.getString(ARG_ACCOUNT_DIR);

        mViewModel = ViewModelProviders.of(this, new ImageChooserViewModel.Factory(
                getApplication(), selectionMode, accountDir)).get(ImageChooserViewModel.class);

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
        mInternalImageAdapter = new ImageChooserAdapter(this, maxCellWidth, mViewModel.isSelectionMode());
        mInternalImageList.setAdapter(mInternalImageAdapter);
        mViewModel.mLiveDataInternalImages.observe(this, new Observer<PagedList<ImageResource>>() {
            @Override
            public void onChanged(PagedList<ImageResource> imageResources) {
                mInternalImageAdapter.submitList(imageResources);
            }
        });

        /* Image Edit Mode ?*/
        if (!mViewModel.isSelectionMode()) {
            PushAccount b = null;
            String json = receivedArgs.getString(ARG_ACCOUNT);
            String dashboardContentRaw = receivedArgs.getString(ARG_DASHBOARD, "");
            long dashboardContentVersion =  receivedArgs.getLong(ARG_DASHBOARD_VERSION, 0L);
            try {

                /* create viewModel */
                b = PushAccount.createAccountFormJSON(new JSONObject(json));
                mDashbardViewModel = ViewModelProviders.of(
                        this, new DashBoardViewModel.Factory(b, getApplication()))
                        .get(DashBoardViewModel.class);

                if (!mDashbardViewModel.isInitialized()) {
                    mDashbardViewModel.setItems(dashboardContentRaw, dashboardContentVersion);
                }
                mSwipeRefreshLayout.setRefreshing(mDashbardViewModel.isSaveRequestActive() || mViewModel.isImportAcitve());
                mDashbardViewModel.mSaveRequest.observe(this, this);
            } catch (Exception e) {
                Log.e(TAG, "init error", e);
            }
            setTitle(R.string.title_manage_images);
        } else {
            setTitle(R.string.title_chooser);
        }

        /* locked resources */
        ArrayList<String> lockedResources = null;
        if (savedInstanceState == null) {
            if (!mViewModel.isSelectionMode()) {
                lockedResources = new ArrayList<>(mDashbardViewModel.getLockedResources());
            } else {
                lockedResources = receivedArgs.getStringArrayList(ARG_LOCKED_RES);
            }
        } else {
            lockedResources = savedInstanceState.getStringArrayList(ARG_LOCKED_RES);
        }

        /* user images */
        mUserImageList = findViewById(R.id.userImageList);
        layoutManager = new GridLayoutManager(this, spanCount);
        mUserImageList.setLayoutManager(layoutManager);
        mUserImageAdapter = new ImageChooserAdapter(this, maxCellWidth, lockedResources, mViewModel.isSelectionMode());

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

        /* display import error (will also be called if no error) */
        mViewModel.mImportedFilesErrorLiveData.observe(this, new Observer<JSONArray>() {
            @Override
            public void onChanged(JSONArray jsonArray) {
                if (jsonArray != null) {
                    mViewModel.setImportActive(false);
                    mSwipeRefreshLayout.setRefreshing(false);
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

        if (savedInstanceState == null) {
            String u = receivedArgs.getString(ARG_RESOURCE_URI);
            if (ImageResource.isExternalResource(u) || !mViewModel.isSelectionMode()) {
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
        if (!
                mViewModel.isSelectionMode()) {
            tabLayout.setVisibility(View.GONE);
            //do not show tab in edit mode
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
            case R.id.menu_save :
                handled = true;
                checkAndSave();
                break;
            case R.id.menu_help :
                handled = true;
                showHelp();
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void showHelp() {
        Intent webIntent = new Intent(this, HelpActivity.class);
        webIntent.putExtra(HelpActivity.CONTEXT_HELP, HelpActivity.HELP_DASH_MANAGE_IMAGES_HTML);
        startActivityForResult(webIntent, RC_SHOW_HELP);
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
            m.setVisible(mSelectedTAB == TAB_USER && !mViewModel.isSelectionMode());
        }
        m = menu.findItem(R.id.menu_save);
        if (m != null) {
            m.setVisible(!mViewModel.isSelectionMode());
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
        if (!mViewModel.isSelectionMode() && hasDataChanged()) {
            DashBoardEditActivity.QuitWithoutSaveDlg dlg = new DashBoardEditActivity.QuitWithoutSaveDlg();
            dlg.show(getSupportFragmentManager(), DashBoardEditActivity.QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            /* if user did not click an image, treat as canceled */
            Intent data = new Intent();
            data.putStringArrayListExtra(ARG_LOCKED_RES, mUserImageAdapter.getLockedResources());
            setResult(AppCompatActivity.RESULT_CANCELED, data);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(KEY_SELECTED_TAB, mSelectedTAB);
        outState.putStringArrayList(ARG_LOCKED_RES, mUserImageAdapter.getLockedResources());
    }

    @Override
    public void onImageSelected(int pos, String uri) {
        Bundle receivedArgs = getIntent().getExtras();
        Intent data = new Intent();
        if (receivedArgs != null && receivedArgs.getInt(ARG_CTRL_IDX) != -1) {
            data.putExtra(ARG_CTRL_IDX, receivedArgs.getInt(ARG_CTRL_IDX));
        }
        data.putExtra(ARG_RESOURCE_URI, uri);
        data.putStringArrayListExtra(ARG_LOCKED_RES, mUserImageAdapter.getLockedResources());
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
        if (mViewModel.isImportAcitve() || mDashbardViewModel.isSaveRequestActive()) {
            displayPleaseWait();
        } else {
            Intent requestFiles = new Intent(Intent.ACTION_GET_CONTENT);
            requestFiles.setType("image/*");
            requestFiles.addCategory(Intent.CATEGORY_OPENABLE);
            requestFiles.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                requestFiles.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

            try {
                Intent browser = Intent.createChooser(requestFiles, getString(R.string.action_import));
                startActivityForResult(browser, RC_IMPORT_IMAGE);
            } catch(ActivityNotFoundException e) {
                Toast.makeText(getApplicationContext(), R.string.import_no_files, Toast.LENGTH_LONG).show();
            }
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
                        mViewModel.setImportActive(true);
                        mSwipeRefreshLayout.setRefreshing(true);
                        Utils.executor.submit(new ImportFiles(this, files));
                        hasClipData = true;
                    }
                }
                if (!hasClipData) {
                    Uri fileUri = data.getData();
                    if (fileUri != null) {
                        mSwipeRefreshLayout.setRefreshing(true);
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

    // only used in !mSelectionMode
    protected void checkAndSave() {
        if (!hasDataChanged()) {
            Toast.makeText(getApplicationContext(), R.string.error_data_unmodified, Toast.LENGTH_LONG).show();
        } else {
            save();
        }
    }

    // only used in !mSelectionMode
    protected boolean hasDataChanged() {
        boolean changed = false;

        ArrayList<String> lockedResources = mUserImageAdapter.getLockedResources();

        if (lockedResources.size() != mDashbardViewModel.getLockedResources().size()) {
            changed = true;
        } else {
            for(String key : mDashbardViewModel.getLockedResources()) {
                if (!lockedResources.contains(key)) {
                    changed = true;
                    break;
                }
            }
        }

        return changed;
    }

    // only used in !mSelectionMode
    protected void save() {
        if (mDashbardViewModel.isSaveRequestActive()) {
            displayPleaseWait();
        } else {
            /* convert complete dashboard to json */
            LinkedList<GroupItem> groupItems = new LinkedList<>();
            HashMap<Integer, LinkedList<Item>> items = new HashMap<>();
            HashSet<String> resources = new HashSet<>(mUserImageAdapter.getLockedResources());
            mDashbardViewModel.copyItems(groupItems, items, null);

            JSONObject obj = DBUtils.createJSONStrFromItems(groupItems, items, resources);
            mDashbardViewModel.saveDashboard(obj, 0);
            Log.d(TAG, "json: "+  obj.toString());

        }
    }

    protected void displayPleaseWait() {
        Toast t = Toast.makeText(this, getString(R.string.op_in_progress), Toast.LENGTH_LONG);
        t.show();
    }

    // only used in !mSelectionMode
    @Override
    public void onChanged(Request request) {
        if (request != null && request instanceof DashboardRequest) {
            DashboardRequest dashboardRequest = (DashboardRequest) request;
            PushAccount b = dashboardRequest.getAccount();
            if (b.status == 1) {
                mSwipeRefreshLayout.setRefreshing(true);
            } else {
                boolean isNew = false;
                if (mDashbardViewModel.isCurrentSaveRequest(request)) {
                    isNew = mDashbardViewModel.isSaveRequestActive(); // result already processed/displayed?
                    mDashbardViewModel.confirmSaveResultDelivered();
                    mSwipeRefreshLayout.setRefreshing(false);
                    // updateUI(true);

                    /* handle cerificate exception */
                    if (b.hasCertifiateException()) {
                        /* only show dialog if the certificate has not already been denied */
                        if (!AppTrustManager.isDenied(b.getCertificateException().chain[0])) {
                            FragmentManager fm = getSupportFragmentManager();

                            String DLG_TAG = CertificateErrorDialog.class.getSimpleName() + "_" +
                                    AppTrustManager.getUniqueKey(b.getCertificateException().chain[0]);

                            /* check if a dialog is not already showing (for this certificate) */
                            if (fm.findFragmentByTag(DLG_TAG) == null) {
                                CertificateErrorDialog dialog = new CertificateErrorDialog();
                                Bundle args = CertificateErrorDialog.createArgsFromEx(
                                        b.getCertificateException(), request.getAccount().pushserver);
                                if (args != null) {
                                    int cmd = dashboardRequest.mCmd;
                                    args.putInt("cmd", cmd);
                                    dialog.setArguments(args);
                                    dialog.show(getSupportFragmentManager(), DLG_TAG);
                                }
                            }
                        }
                    }
                    b.setCertificateExeption(null); // mark es "processed"

                    /* handle insecure connection */
                    if (b.inSecureConnectionAsk) {
                        if (Connection.mInsecureConnection.get(b.pushserver) == null) {
                            FragmentManager fm = getSupportFragmentManager();

                            String DLG_TAG = InsecureConnectionDialog.class.getSimpleName() + "_" + b.pushserver;

                            /* check if a dialog is not already showing (for this host) */
                            if (fm.findFragmentByTag(DLG_TAG) == null) {
                                InsecureConnectionDialog dialog = new InsecureConnectionDialog();
                                Bundle args = InsecureConnectionDialog.createArgsFromEx(b.pushserver);
                                if (args != null) {
                                    int cmd = dashboardRequest.mCmd;
                                    args.putInt("cmd", cmd);
                                    dialog.setArguments(args);
                                    dialog.show(getSupportFragmentManager(), DLG_TAG);
                                }
                            }
                        }
                    }
                    b.inSecureConnectionAsk = false; // mark as "processed"

                    if (b.requestStatus != Cmd.RC_OK) {
                        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                        if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                            t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                        }
                        showErrorMsg(t);
                    } else {
                        if (dashboardRequest.saveSuccesful()) {
                            if (isNew) {
                                Intent data = new Intent();
                                data.putExtra(ARG_DASHBOARD, dashboardRequest.getReceivedDashboard());
                                data.putExtra(ARG_DASHBOARD_VERSION, dashboardRequest.getServerVersion());
                                setResult(AppCompatActivity.RESULT_OK, data);
                                Toast.makeText(getApplicationContext(), R.string.info_data_saved, Toast.LENGTH_LONG).show();
                                finish();
                            }
                        } else if (!dashboardRequest.saveSuccesful() && dashboardRequest.requestStatus != Cmd.RC_OK) {
                            String t = (dashboardRequest.requestErrorTxt == null ? "" : dashboardRequest.requestErrorTxt);
                            if (dashboardRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                if (dashboardRequest.requestErrorCode == MQTTException.REASON_CODE_SUBSCRIBE_FAILED) {
                                    t = getString(R.string.dash_err_subscribe_failed);
                                } else {
                                    t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                            }
                            showErrorMsg(t);
                        } else if (dashboardRequest.isVersionError()) {
                            String t = getString(R.string.dash_err_version_err);
                            showErrorMsg(t);
                        }
                    }

                }
            }
        }


    }


    SwipeRefreshLayout mSwipeRefreshLayout;
    DashBoardViewModel mDashbardViewModel;
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
    public final static String ARG_LOCKED_RES = "ARG_LOCKED_RES";
    public final static String ARG_ACCOUNT_DIR = "ARG_ACCOUNT_DIR";

    protected final static String KEY_SELECTED_TAB = " KEY_SELECTED_TAB";

    private final static int RC_IMPORT_IMAGE = 1;
    private final static int RC_SHOW_HELP = 2;
    public final static String TAG = ImageChooserActivity.class.getSimpleName();

}
