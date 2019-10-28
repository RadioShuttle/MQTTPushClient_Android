/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import de.radioshuttle.mqttpushclient.AccountListActivity;
import de.radioshuttle.mqttpushclient.CertificateErrorDialog;
import de.radioshuttle.mqttpushclient.InsecureConnectionDialog;
import de.radioshuttle.mqttpushclient.MessagesActivity;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.PublishRequest;
import de.radioshuttle.net.Request;
import de.radioshuttle.utils.Utils;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class DashBoardActivity extends AppCompatActivity implements
        DashBoardActionListener, CertificateErrorDialog.Callback {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board);

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);

        if (ZOOM_LEVEL_1 == 0) {
            ZOOM_LEVEL_1 = getResources().getDimensionPixelSize(R.dimen.dashboard_zoom_1);
            ZOOM_LEVEL_2 = getResources().getDimensionPixelSize(R.dimen.dashboard_zoom_2);
            ZOOM_LEVEL_3 = getResources().getDimensionPixelSize(R.dimen.dashboard_zoom_3);
        }


        PushAccount b = null;
        boolean init = false;
        try {
            b = PushAccount.createAccountFormJSON(new JSONObject(json));
            mViewModel = ViewModelProviders.of(
                    this, new DashBoardViewModel.Factory(b, getApplication()))
                    .get(DashBoardViewModel.class);

            ViewState vs = ViewState.getInstance(getApplication());
            String account = b.getKey();
            Log.d(TAG, "onCreate()");

            if (!mViewModel.isInitialized()) {
                mViewModel.setItems(vs.getDashBoardContent(b.getKey()), vs.getDashBoardModificationDate(account));
                mViewModel.startJavaScriptExecutors();
                mViewModel.loadLastReceivedMessages();
                mViewModel.mCachedMessages.observe(this, new Observer<List<Message>>() {
                    @Override
                    public void onChanged(List<Message> messages) {
                        if (messages != null) {
                            /* only set cahced messages if we do not have received any messages so far */
                            if (mViewModel.getLastReceivedMessages() == null) {
                                Log.d(TAG, "oncreate -  onMessageReceived");//TODO: raus
                                for(Message m : messages) {
                                    mViewModel.onMessageReceived(m);
                                }
                                mViewModel.mCachedMessages.removeObserver(this);
                            }
                        }
                    }
                });

                init = true;
            }

            if (savedInstanceState == null) {
                mZoomLevel = ViewState.getInstance(getApplication()).getLastZoomLevel(account);
                if (mZoomLevel == 0) {
                    mZoomLevel = 1;
                }

                vs.setLastState(b.getKey(), ViewState.VIEW_DASHBOARD);
            } else {
                mZoomLevel = savedInstanceState.getInt(KEY_ZOOM_LEVEL, 1);
            }
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.account_display_name);
            server.setText(b.pushserver);
            key.setText(b.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        mControllerList = findViewById(R.id.controllerList);
        if (mControllerList != null) {
            int spanCount = calcSpanCount();
            // Log.d(TAG, "item width: " + itemWidth + ", width dpi: " + widthDPI + ", span count:  " + spanCount);

            final GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
            // layoutManager.setMeasurementCacheEnabled(true);
            layoutManager.setSpanSizeLookup(new DBUtils.SpanSizeLookup(mControllerList));

            final int spacing = getResources().getDimensionPixelSize(R.dimen.dashboard_spacing);
            Log.d(TAG, "spacing: " + spacing + " dpi " + (int) ((float) spacing * (160f / (float) getResources().getDisplayMetrics().densityDpi)));

            mControllerList.setLayoutManager(layoutManager);
            mControllerList.addItemDecoration(new DBUtils.ItemDecoration(getApplication()));
            LinkedHashSet<Integer> selectedItems = new LinkedHashSet<>();
            if (savedInstanceState != null) {
                List<Integer> itemsList= savedInstanceState.getIntegerArrayList(KEY_SELECTED_ITEMS);
                if (itemsList != null && itemsList.size() > 0) {
                    selectedItems.addAll(itemsList);
                }
            }

            mAdapter = new DashBoardAdapter(b,this, getWidthPixel(), layoutManager.getSpanCount(), selectedItems);
            mAdapter.addListener(this);
            mControllerList.setAdapter(mAdapter);

            mViewModel.mDashBoardItemsLiveData.observe(this, new Observer<List<Item>>() {
                @Override
                public void onChanged(List<Item> dashBoardItems) {
                    RecyclerView.Adapter a = mControllerList.getAdapter();
                    if (a instanceof DashBoardAdapter) {
                        ((DashBoardAdapter) a).setData(dashBoardItems);
                    }
                }
            });

            if (selectedItems != null && selectedItems.size() > 0) {
                mActionMode = startSupportActionMode(mActionModeCallback);
            }

            mViewModel.mSyncRequest.observe(this, new Observer<Request>() {
                @Override
                public void onChanged(Request request) {
                    if (request instanceof DashboardRequest) {
                        onLoadMessagesFinished((DashboardRequest) request);
                    }
                }
            });

            mViewModel.mSaveRequest.observe(this, new Observer<Request>() {
                @Override
                public void onChanged(Request request) {
                    if (request instanceof DashboardRequest) {
                        onSaveFinished((DashboardRequest) request);
                    }
                }
            });

            mViewModel.mPublishRequest.observe(this, new Observer<Request>() {
                @Override
                public void onChanged(Request request) {
                    if (request instanceof PublishRequest) {
                        onPublishFinished((PublishRequest) request);
                    }
                }
            });

            if (init) {
                mViewModel.startGetMessagesTimer();
            }

            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        setTitle(getString(R.string.title_dashboard));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(false);
        mSwipeRefreshLayout.setRefreshing(mViewModel.isSaveRequestActive());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                break;
            case R.id.menu_change_view :
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    switchToMessagesActivity();
                }
                break;
            case R.id.action_add_group :
                openEditor(GroupItem.class);
                // addGroupItem();
                break;
            case R.id.action_add_text :
                openEditor(TextItem.class);
                // addTextItem();
                break;
            case R.id.action_add_switch :
                openEditor(Switch.class);
                break;
            case R.id.action_add_progress :
                openEditor(ProgressItem.class);
                break;
            case R.id.action_add_custom :
                openEditor(CustomItem.class);
                break;
            case R.id.action_zoom :
                zoom();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_dash_board, menu);
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_ZOOM_LEVEL, mZoomLevel);
        if (mAdapter != null) {
            HashSet<Integer> selectedItems = mAdapter.getSelectedItems();
            if (selectedItems != null && selectedItems.size() > 0) {
                outState.putIntegerArrayList(KEY_SELECTED_ITEMS, new ArrayList<Integer>(selectedItems));
            }
        }
    }

    protected void switchToMessagesActivity() {
        Intent intent = new Intent(this, MessagesActivity.class);
        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean notifStart = args.getBoolean(AccountListActivity.ARG_NOTIFSTART, false);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);
        intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, hastMultipleServer);
        intent.putExtra(PARAM_ACCOUNT_JSON, json);
        intent.putExtra(AccountListActivity.ARG_NOTIFSTART, notifStart);
        startActivityForResult(intent, AccountListActivity.RC_MESSAGES);
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mViewModel.startGetMessagesTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mViewModel.stopGetMessagesTimer();
        // cache last received messages
        mViewModel.saveLastReceivedMessages();
    }

    protected void handleBackPressed() {
        Intent intent = new Intent();
        intent.putExtra(AccountListActivity.ARG_NOTIFSTART, getIntent().getBooleanExtra(AccountListActivity.ARG_NOTIFSTART, false));
        setResult(AppCompatActivity.RESULT_OK, intent);
        finish();
    }

    protected void zoom() {
        mZoomLevel++;
        if (mZoomLevel > 3) {
            mZoomLevel = 1;
        }
        if (mViewModel != null && mViewModel.getPushAccount() != null) {
            ViewState.getInstance(getApplication()).setLastZoomLevel(mViewModel.getPushAccount().getKey(), mZoomLevel);
        }

        int spanCount = calcSpanCount();
        Log.d(TAG, "new zoomlevel: " + mZoomLevel + " span count: " + spanCount + " width pixel: " + getWidthPixel() + " width dpi: " + getWidthDPI());
        RecyclerView.LayoutManager lm = mControllerList.getLayoutManager();
        if (lm instanceof GridLayoutManager) {
            RecyclerView.Adapter adapter = mControllerList.getAdapter();
            if (adapter instanceof DashBoardAdapter) {
                ((GridLayoutManager) lm).setSpanCount(spanCount);
                ((DashBoardAdapter) adapter).setItemWidth(getWidthPixel(), spanCount);
            }
        }
    }

    protected int calcSpanCount() {
        int itemWidth = getWidthDPI(); // item width in dpi
        int spanCount = 0;
        DisplayMetrics dm = getResources().getDisplayMetrics();

        float spacingDPI = getResources().getDimension(R.dimen.dashboard_spacing) / dm.density;

        // Log.d(TAG, "width px: " + getWidthPixel() + " width dpi: " + getWidthDPI());

        // calc number of columns depending on width
        float widthDPI = (float) dm.widthPixels  / dm.density;
        widthDPI -= 24; // subtract left and right margin (not including spacing for most left and most right cell)

        if ((float) itemWidth > widthDPI) {
            itemWidth = (int) widthDPI;
            switch (mZoomLevel) {
                case 1 : ZOOM_LEVEL_1 = itemWidth; break;
                case 2 : ZOOM_LEVEL_2 = itemWidth; break;
                case 3 : ZOOM_LEVEL_3 = itemWidth; break;
            }
            spanCount = 1;
        } else {
            // spanCount = ((int) widthDPI + spacing) / (itemWidth + spacing);
            spanCount = (int) widthDPI /  (itemWidth + (int)(spacingDPI * 2f));
        }
        return spanCount;
    }

    protected int getWidthDPI() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthPixel = getWidthPixel();
        return (int) ((float) widthPixel / dm.density);
    }

    protected int getWidthPixel() {
        int itemWidth; // item width in dpi
        if (mZoomLevel == 2) {
            itemWidth = ZOOM_LEVEL_2;
        } else if (mZoomLevel == 3) {
            itemWidth = ZOOM_LEVEL_3;
        } else {
            itemWidth = ZOOM_LEVEL_1;
        }
        return itemWidth;
    }

    @Override
    public void onItemClicked(Item item) {
        /*
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(item.label + " clicked.");
        builder.create().show();
        */
        FragmentManager fm = getSupportFragmentManager();

        String DLG_TAG = DetailViewDialog.class.getSimpleName();
        if (fm.findFragmentByTag(DLG_TAG) == null) {

        }

        DetailViewDialog dlg = DetailViewDialog.newInstance(item);
        dlg.show(fm, DLG_TAG);

    }

    @Override
    public void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems) {
        if (noOfSelectedItemsBefore == 0 && noOfSelectedItems > 0) {
            mActionMode = startSupportActionMode(mActionModeCallback);
        } else if (noOfSelectedItemsBefore > 0 && noOfSelectedItems == 0) {
            if (mActionMode != null)
                mActionMode.finish();
        }
    }

    public void onItemEdit() {
        Log.d(TAG, "edit item: ");
        Integer lastSelectedItem = mAdapter.getLastSelectedItem();
        if (lastSelectedItem != null) {
            DashBoardViewModel.ItemContext ic = mViewModel.getItem(lastSelectedItem);
            if (ic != null && ic.item != null) {
                openEditor(ic);
            }
        }
    }

    public void onItemsDelete(boolean all) {
        if (mAdapter != null) {

            /* convert complete dashboard to json */
            LinkedList<GroupItem> groupItems = new LinkedList<>();
            HashMap<Integer, LinkedList<Item>> items = new HashMap<>();
            mViewModel.copyItems(groupItems, items);
            mViewModel.removeItems(groupItems, items, all ? null : mAdapter.getSelectedItems(), mViewModel.mJavaScriptExecutor);
            JSONObject obj = DBUtils.createJSONStrFromItems(groupItems, items);
            mViewModel.saveDashboard(obj, 0);
        }
    }

    // add
    protected void openEditor(Class<? extends Item> type) {
        if (!mActivityStarted) {
            if (checkIfUpdateRequired()) {
                return;
            }
            mActivityStarted = true;
            Intent intent = new Intent(this, DashBoardEditActivity.class);
            intent.putExtra(DashBoardEditActivity.ARG_ACCOUNT, getIntent().getStringExtra(PARAM_ACCOUNT_JSON));
            intent.putExtra(DashBoardEditActivity.ARG_MODE, DashBoardEditActivity.MODE_ADD);
            intent.putExtra(DashBoardEditActivity.ARG_TYPE, type.getName());
            intent.putExtra(DashBoardEditActivity.ARG_DASHBOARD, mViewModel.getItemsRaw());
            intent.putExtra(DashBoardEditActivity.ARG_DASHBOARD_VERSION, mViewModel.getItemsVersion());
            if (type.getName().equals(GroupItem.class.getName())) {
                intent.putExtra(DashBoardEditActivity.ARG_GROUP_POS, -1); // no groups selection
                intent.putExtra(DashBoardEditActivity.ARG_ITEM_POS, mViewModel.getGroups().size());
            } else {
                int pos = -1;
                LinkedList<GroupItem> groups = mViewModel.getGroups();
                int grpIdx = mViewModel.getGroups().size() - 1;
                intent.putExtra(DashBoardEditActivity.ARG_GROUP_POS, grpIdx);
                if (grpIdx >= 0 && grpIdx < groups.size()) {
                    int groupID = mViewModel.getGroups().get(grpIdx).id;
                    LinkedList<Item> items = mViewModel.getItems(groupID);
                    if (items != null) {
                        pos = items.size();
                    }
                }
                intent.putExtra(DashBoardEditActivity.ARG_ITEM_POS, pos);
            }

            startActivityForResult(intent, RC_EDIT_ITEM);
        }
    }

    protected boolean checkIfUpdateRequired() {
        boolean updateRequired = false;
        if (mViewModel.mVersion != -1 && mViewModel.mVersion != Item.DASHBOARD_VERSION ) {
            updateRequired = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dash_update_dlg_title);
            builder.setMessage(R.string.dash_update_dlg_msg);
            builder.setPositiveButton(R.string.dash_update_dlg_update, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(
                            "https://play.google.com/store/apps/details?id=" + getPackageName()));
                    // intent.setPackage("com.android.vending");
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(R.string.action_cancel, null);
            AlertDialog dlg = builder.create();
            dlg.show();
        }
        return updateRequired;
    }


    // edit
    protected void openEditor(DashBoardViewModel.ItemContext selectedItem) {
        if (!mActivityStarted && selectedItem != null && selectedItem.item != null) {
            if (checkIfUpdateRequired()) {
                return;
            }
            mActivityStarted = true;
            Intent intent = new Intent(this, DashBoardEditActivity.class);
            intent.putExtra(DashBoardEditActivity.ARG_ACCOUNT, getIntent().getStringExtra(PARAM_ACCOUNT_JSON));
            intent.putExtra(DashBoardEditActivity.ARG_MODE, DashBoardEditActivity.MODE_EDIT);
            intent.putExtra(DashBoardEditActivity.ARG_TYPE, selectedItem.getClass().getName());
            intent.putExtra(DashBoardEditActivity.ARG_DASHBOARD, mViewModel.getItemsRaw());
            intent.putExtra(DashBoardEditActivity.ARG_DASHBOARD_VERSION, mViewModel.getItemsVersion());
            intent.putExtra(DashBoardEditActivity.ARG_ITEM_ID, selectedItem.item.id);
            if (selectedItem.item instanceof GroupItem) {
                intent.putExtra(DashBoardEditActivity.ARG_GROUP_POS, -1);
            } else {
                intent.putExtra(DashBoardEditActivity.ARG_GROUP_POS, selectedItem.groupPos);
            }
            intent.putExtra(DashBoardEditActivity.ARG_ITEM_POS, selectedItem.itemPos);

            startActivityForResult(intent, RC_EDIT_ITEM);
        }
    }

    public void onLoadMessagesFinished(DashboardRequest request) {

        if (request != null) {
            PushAccount b = request.getAccount();
            if (request.hasCompleted()) {
                boolean isNew = false;
                boolean isInitialRequest = (mViewModel.getLastReceivedMessages() == null); // first time messages received
                if (mViewModel.isCurrentSyncRequest(request)) {
                    isNew = mViewModel.isSyncRequestActive(); // result already processed/displayed?
                    mViewModel.confirmResultDeliveredSyncRequest();

                    handleCertError(Cmd.CMD_GET_MESSAGES_DASH, request);

                    if (isNew) {
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                                t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else if (request.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            showErrorMsg(t);
                        } else if (request.isVersionError()) { // ok, but maybe the current dashboard is outdated
                            mViewModel.setItems(
                                    request.getReceivedDashboard(),
                                    request.getServerVersion());
                            setCachedMessages();
                            String t = getString(R.string.dash_err_version_err_replaced);
                            showErrorMsg(t);
                            return;
                        } else { // no errors. hide previous shown error message
                            if (request.hasNewResourcesReceived()) {
                                Log.d(TAG, "new resource onLoadMessagesFinished, calling refresh ");
                                mViewModel.refresh();
                            }
                            mViewModel.setLastReceivedMessages(request.getReceivedMessages(),
                                    request.getLastReceivedMsgDate(), request.getLastReceivedMsgSeqNo()); // to be cached later
                            mLastErrorStr = null;
                            if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                mSnackbar.dismiss(); //TODO: make sure, error message is shown at least a few seconds (there may be a publish, deletion error currntyl showing)
                            }
                        }
                    }
                }

                /*
                if (request.getReceivedMessages().size() > 0) {
                    Log.d(TAG, "onLoadMessagesFinished - onMessageReceived ");
                }
                 */

                HashMap<String, Message> cachedMsgs = null;
                if (isInitialRequest) {
                    List<Message> msgs =  mViewModel.mCachedMessages.getValue();
                    if (msgs != null && msgs.size() > 0) {
                        cachedMsgs = new HashMap<>();
                        for(Message m : msgs) {
                            if (m.filter != null)
                                cachedMsgs.put(m.filter, m);
                        }
                    }
                }

                for(Message m : request.getReceivedMessages()) {
                    /* filter received messages which have already been set (local cache) */
                    if (cachedMsgs != null && m.filter != null) {
                        Message c = cachedMsgs.get(m.filter);
                        if (c != null && c.getWhen() == m.getWhen() && c.getSeqno() == m.getSeqno()) {
                            // Log.d(TAG, "onLoadMessagesFinished - onMessageReceived - filtered: ");
                            continue;
                        }
                    }
                    mViewModel.onMessageReceived(m);
                }
            }
        }
    }

    public void onPublishFinished(PublishRequest publishRequest) {
        if (publishRequest != null) {
            PushAccount b = publishRequest.getAccount();
            if (!publishRequest.hasCompleted()) {
                //TODO: set progress bar, if not already done when publish was triggered
            } else {
                if (!publishRequest.isDelivered()) {
                    publishRequest.setResultDelivered(true);
                    //TODO: hide progress bar
                    DashBoardViewModel.ItemContext ic = mViewModel.getItem(publishRequest.getItemID());

                    /*
                     * If current publish request was triggered by a slider event (ProgressItem type):
                     * For performance reasons ui update (repaint) will be skipped (except setting error2 data),
                     * The UI will be updated after next polling request.
                     */
                    boolean skipUIupdate = ic !=null && ic.item instanceof ProgressItem;

                     //handleCertError(Cmd.CMD_MQTT_PUBLISH, publishRequest);

                    if (b.requestStatus != Cmd.RC_OK) {
                        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                        if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                            t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                        }
                        if (ic != null && ic.item != null) {
                            ic.item.data.put("error2", t); //TODO: consider showing error in global window too
                            if (!skipUIupdate) {
                                ic.item.notifyDataChanged();
                            }
                        }
                    } else if (publishRequest.requestStatus != Cmd.RC_OK) {
                        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                        if (ic != null && ic.item != null) {
                            ic.item.data.put("error2", t); //TODO: consider showing error in global window too
                            if (!skipUIupdate) {
                                ic.item.notifyDataChanged();
                            }
                        }
                    } else { // reesult OK
                        boolean updateItem = false;
                        if (!Utils.isEmpty(publishRequest.outputScriptError)) {
                            if (ic != null && ic.item != null) {
                                if (!ic.item.data.containsKey("error2") || !Utils.equals(ic.item.data.get("error2"), (String) publishRequest.outputScriptError)) {
                                    updateItem = true;
                                }
                                ic.item.data.put("error2", publishRequest.outputScriptError); // clear error message
                            }
                        } else {
                            if (ic != null && ic.item != null) {
                                if (ic.item.data.containsKey("error2")) {
                                    updateItem = true;
                                }
                                ic.item.data.remove("error2"); // clear error message
                            }
                        }
                        if (updateItem && !skipUIupdate) {
                            ic.item.notifyDataChanged();
                        }
                        if (Utils.isEmpty(publishRequest.outputScriptError) && !skipUIupdate) {
                            mViewModel.onMessagePublished(publishRequest.getMessage());
                        }
                    }
                }
            }
        }
    }

    protected void handleCertError(int cmd, Request r) {
        PushAccount b = r.getAccount();

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
                            b.getCertificateException(), r.getAccount().pushserver);
                    if (args != null) {
                        mViewModel.stopGetMessagesTimer();
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
                        mViewModel.stopGetMessagesTimer();
                        args.putInt("cmd", Cmd.CMD_GET_MESSAGES_DASH);
                        dialog.setArguments(args);
                        dialog.show(getSupportFragmentManager(), DLG_TAG);
                    }
                }
            }
        }
        b.inSecureConnectionAsk = false; // mark as "processed"
    }

    public void onSaveFinished(DashboardRequest dashboardRequest) {
        PushAccount b = dashboardRequest.getAccount();
        if (!dashboardRequest.hasCompleted()) {
            mSwipeRefreshLayout.setRefreshing(true);
        } else {
            boolean isNew = false;
            if (mViewModel.isCurrentSaveRequest(dashboardRequest)) {
                isNew = mViewModel.isSaveRequestActive(); // result already processed/displayed?
                mViewModel.confirmSaveResultDelivered();
                mSwipeRefreshLayout.setRefreshing(false);
                // updateUI(true);

                // handleCertError(Cmd.CMD_SET_DASHBOARD, dashboardRequest);

                if (isNew) {
                    if (b.requestStatus != Cmd.RC_OK) {
                        String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                        if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
                            t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                        }
                        showErrorMsg(t);
                    } else if (dashboardRequest.saveSuccesful()) {
                        Log.d(TAG, "onSaveFinished(): ");
                        mLastErrorStr = null;
                        mViewModel.setItems(
                                dashboardRequest.getReceivedDashboard(),
                                dashboardRequest.getServerVersion());
                        setCachedMessages();
                        if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                            mSnackbar.dismiss();
                        }
                    } else if (dashboardRequest.requestStatus != Cmd.RC_OK) {
                        String t = (dashboardRequest.requestErrorTxt == null ? "" : dashboardRequest.requestErrorTxt);
                        if (dashboardRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                            t = getString(R.string.errormsg_mqtt_prefix) + " " + t;
                        }
                        showErrorMsg(t);
                    } else if (dashboardRequest.isVersionError()) { // deleted content replaced by new version
                        mViewModel.setItems(
                                dashboardRequest.getReceivedDashboard(),
                                dashboardRequest.getServerVersion());
                        setCachedMessages();
                        String t = getString(R.string.dash_err_version_err_replaced);
                        showErrorMsg(t);
                    }
                }
            }
        }
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            if (!Utils.equals(msg, mLastErrorStr) || mSnackbar == null || !mSnackbar.isShownOrQueued()) {
                mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
                mSnackbar.show();
                mLastErrorStr = msg;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        if (requestCode == RC_EDIT_ITEM) {

            if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
                Log.d(TAG, "onActivityResult(): ");
                mViewModel.setItems(
                        data.getStringExtra(DashBoardEditActivity.ARG_DASHBOARD),
                        data.getLongExtra(DashBoardEditActivity.ARG_DASHBOARD_VERSION, - 1));
                setCachedMessages();
            }
        }
    }

    // call after dashboard has been reloaded/updated
    protected void setCachedMessages() {
        LinkedHashMap<String, Message> msgs = mViewModel.getLastReceivedMessages();
        if (msgs != null && msgs.size() > 0) {
            for(Message m : msgs.values()) {
                mViewModel.onMessageReceived(m);
            }
        } else {
            List<Message> cachedMsgs = mViewModel.mCachedMessages.getValue();
            if (cachedMsgs != null && cachedMsgs.size() > 0) {
                for(Message m : cachedMsgs) {
                    mViewModel.onMessageReceived(m);
                }
            }
        }
    }

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd");
            if (cmd == Cmd.CMD_GET_MESSAGES_DASH) {
                mViewModel.startGetMessagesTimer();
            } //TODO: handle other
        }
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.activity_dash_board_action, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            boolean handled = false;
            switch (item.getItemId()) {
                case R.id.action_delete_items:
                    if (mViewModel.isSaveRequestActive()) {
                        Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                    } else {
                        DBUtils.showDeleteDialog(DashBoardActivity.this);
                    }
                    handled = true;
                    break;
                case R.id.action_edit_item:
                    onItemEdit();
                    handled = true;
                    break;
            }

            return handled;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (mAdapter != null)
                mAdapter.clearSelection();
            mActionMode = null;
        }
    };


    private RecyclerView mControllerList;

    private String mLastErrorStr;
    private ActionMode mActionMode;
    private DashBoardAdapter mAdapter;
    private int mZoomLevel;
    private boolean mActivityStarted;
    protected DashBoardViewModel mViewModel;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Snackbar mSnackbar;


    private int ZOOM_LEVEL_1 = 0; // dpi
    private int ZOOM_LEVEL_2 = 0;
    private int ZOOM_LEVEL_3 = 0;

    private final static int RC_EDIT_ITEM = 1;

    private final static String KEY_ZOOM_LEVEL = "ZOOM_LEVEL";
    private final static String KEY_SELECTED_ITEMS = "KEY_SELECTED_ITEMS";

    private final static String TAG = DashBoardActivity.class.getSimpleName();

}
