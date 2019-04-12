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
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.AccountListActivity;
import de.radioshuttle.mqttpushclient.MessagesActivity;
import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.DashboardRequest;
import de.radioshuttle.net.Request;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class DashBoardActivity extends AppCompatActivity implements DashBoardActionListener {


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

            if (!mViewModel.isInitialized()) {
                mViewModel.setItems(vs.getDashBoardContent(b.getKey()), vs.getDashBoardModificationDate(account));
                mViewModel.startJavaScriptExecutors();
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
            HashSet<Integer> selectedItems = new HashSet<>();
            if (savedInstanceState != null) {
                List<Integer> itemsList= savedInstanceState.getIntegerArrayList(KEY_SELECTED_ITEMS);
                if (itemsList != null && itemsList.size() > 0) {
                    selectedItems.addAll(itemsList);
                }
            }

            mAdapter = new DashBoardAdapter(this, getWidthPixel(), layoutManager.getSpanCount(), selectedItems);
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

            mViewModel.mMessagesRequest.observe(this, new Observer<Request>() {
                @Override
                public void onChanged(Request request) {
                    if (request instanceof DashboardRequest) {
                        onLoadFinishedDashboard((DashboardRequest) request);
                    }
                }
            });

            if (init) {
                mViewModel.startGetMessagesTimer();
            }

        }


        setTitle(getString(R.string.title_dashboard));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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

        float spacingDPI = getResources().getDimension(R.dimen.dashboard_spacing) * (160f / (float) dm.densityDpi) ;

        // Log.d(TAG, "width px: " + getWidthPixel() + " width dpi: " + getWidthDPI());

        // calc number of columns depending on width
        float widthDPI = (float) dm.widthPixels * (160f / (float) dm.densityDpi);
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
        return (int) ((float) widthPixel * (160f / (float) dm.densityDpi));
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(item.label + " clicked.");
        builder.create().show();
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
        HashSet<Integer> selItems = mAdapter.getSelectedItems();
        if (selItems != null && selItems.size() > 0) {
            if (selItems.size() > 1) {
                Toast.makeText(getApplicationContext(), R.string.select_single_dash, Toast.LENGTH_LONG).show();
            } else {
                DashBoardViewModel.ItemContext ic = mViewModel.getItem(selItems.iterator().next());
                if (ic != null && ic.item != null) {
                    openEditor(ic);
                }
            }
        }

    }

    public void onItemsDelete(boolean all) {
        if (mAdapter != null) {
            mViewModel.removeItems(all ? null : mAdapter.getSelectedItems());
        }
    }

    // add
    protected void openEditor(Class<? extends Item> type) {
        if (!mActivityStarted) {
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

    // edit
    protected void openEditor(DashBoardViewModel.ItemContext selectedItem) {
        if (!mActivityStarted && selectedItem != null && selectedItem.item != null) {
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

    public void onLoadFinishedDashboard(DashboardRequest request) {


        if (mViewModel.isCurrentRequest(request)) {
            mViewModel.confirmResultDelivered();
        }

        for(MqttMessage m : request.getReceivedMessages()) {
            mViewModel.onMessageReceived(m);
        }
        //TODO: handle errors:
        if (request.requestErrorCode == Cmd.RC_OK) {
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        if (requestCode == RC_EDIT_ITEM) {

            if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
                mViewModel.setItems(
                        data.getStringExtra(DashBoardEditActivity.ARG_DASHBOARD),
                        data.getLongExtra(DashBoardEditActivity.ARG_DASHBOARD_VERSION, - 1));
            }
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
                    DBUtils.showDeleteDialog(DashBoardActivity.this);
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

    private ActionMode mActionMode;
    private DashBoardAdapter mAdapter;
    private int mZoomLevel;
    private boolean mActivityStarted;
    protected DashBoardViewModel mViewModel;

    private int ZOOM_LEVEL_1 = 0; // dpi
    private int ZOOM_LEVEL_2 = 0;
    private int ZOOM_LEVEL_3 = 0;

    private final static int RC_EDIT_ITEM = 1;

    private final static String KEY_ZOOM_LEVEL = "ZOOM_LEVEL";
    private final static String KEY_SELECTED_ITEMS = "KEY_SELECTED_ITEMS";

    private final static String TAG = DashBoardActivity.class.getSimpleName();

}
