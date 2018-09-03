/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import de.radioshuttle.fcm.Notifications;

import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_ACTIONS;
import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_SUBSCRIPTIONS;
import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class ActionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_actions);
        setTitle(getString(R.string.title_actions));

        mViewModel = ViewModelProviders.of(this).get(ActionsViewModel.class);
        boolean actionsLoaded = mViewModel.initialized;

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);

        try {
            mViewModel.init(json);
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.account_display_name);
            server.setText(mViewModel.pushAccount.pushserver);
            key.setText(mViewModel.pushAccount.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        } finally {

        }

        mListView = findViewById(R.id.actionsListView);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        if (mViewModel.selectedActions != null && mViewModel.selectedActions.size() > 0) {
            // mActionMode = startSupportActionMode(mActionModeCallback);
        }

        /*
        mTopicsRecyclerViewAdapter = new TopicsRecyclerViewAdapter(this, mViewModel.selectedTopics, new TopicsRecyclerViewAdapter.RowSelectionListener() {
            @Override
            public void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems) {
                if (noOfSelectedItemsBefore == 0 && noOfSelectedItems > 0) {
                    mActionMode = startSupportActionMode(mActionModeCallback);
                } else if (noOfSelectedItemsBefore > 0 && noOfSelectedItems == 0) {
                    if (mActionMode != null)
                        mActionMode.finish();
                }
            }
        });
        mListView.setAdapter(mTopicsRecyclerViewAdapter);
        */

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        // mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());

        if (!actionsLoaded) {
            refresh();
        }

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
            case R.id.menu_refresh:
                refresh();
                handled = true;
                break;
            case R.id.action_add:
                // showEditDialog(mViewModel.lastEnteredTopic, MODE_ADD,null, null);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void handleBackPressed() {
        setResult(AppCompatActivity.RESULT_CANCELED); //TODO:
        finish();
    }

    protected void refresh() {
            /*
            if (!mViewModel.isRequestActive()) {
                mSwipeRefreshLayout.setRefreshing(true);
                mViewModel.getTopics(this);
            }
            */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.menu_refresh);
        if (m != null) {
            // m.setEnabled(!mViewModel.isRequestActive());
        }

        return super.onPrepareOptionsMenu(menu);
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
    }

    private SwipeRefreshLayout mSwipeRefreshLayout;
    ActionsViewModel mViewModel;
    private RecyclerView mListView;
    private boolean mActivityStarted;

    private final static String TAG = ActionsActivity.class.getSimpleName();
}
