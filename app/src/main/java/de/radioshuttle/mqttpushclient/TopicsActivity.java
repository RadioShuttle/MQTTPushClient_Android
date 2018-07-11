/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import de.radioshuttle.net.BrokerRequest;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.TopicsRequest;

import static de.radioshuttle.mqttpushclient.EditBrokerActivity.PARAM_BROKER_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class TopicsActivity extends AppCompatActivity implements TopicsRecyclerViewAdapter.RowSelectionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topics);

        setTitle(getString(R.string.title_topics));

        mViewModel = ViewModelProviders.of(this).get(TopicsViewModel.class);
        boolean topicsLoaded = mViewModel.initialized;

        mViewModel.topicsRequest.observe(this, new Observer<BrokerRequest>() {
            @Override
            public void onChanged(@Nullable BrokerRequest brokerRequest) {
                if (brokerRequest != null && brokerRequest instanceof TopicsRequest) {
                    TopicsRequest topicsRequest = (TopicsRequest) brokerRequest;
                    PushAccount b = topicsRequest.getBroker();
                    if (b.status == 1) {
                        if (mTopicsRecyclerViewAdapter != null && mTopicsRecyclerViewAdapter.getItemCount() == 0) {
                            if (b.topics.size() > 0) {
                                // special case: configuration change and load in progress -> update view
                                mTopicsRecyclerViewAdapter.setData(b.topics);
                            }
                        }
                    } else {
                        if (mViewModel.isCurrentRequest(brokerRequest)) {
                            mViewModel.confirmResultDelivered();
                            mSwipeRefreshLayout.setRefreshing(false);
                            invalidateOptionsMenu();
                        }
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || b.requestStatus == Cmd.RC_NOT_AUTHORIZED) {
                                t = TopicsActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            if (topicsRequest.requestStatus != Cmd.RC_OK) { // topics add or delete result
                                String t = (topicsRequest.requestErrorTxt == null ? "" : topicsRequest.requestErrorTxt);
                                if (topicsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                    t = TopicsActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                    mSnackbar.dismiss();
                                }
                            }
                        }
                        if (mTopicsRecyclerViewAdapter != null) {
                            mTopicsRecyclerViewAdapter.setData(b.topics);
                        }
                    }
                }
            }
        });

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_BROKER_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);

        try {
            mViewModel.init(json);
            TextView server = findViewById(R.id.push_notification_server);
            TextView key = findViewById(R.id.broker_display_name);
            server.setText(mViewModel.pushAccount.pushserver);
            key.setText(mViewModel.pushAccount.getDisplayName());
            if (!hastMultipleServer) {
                server.setVisibility(View.GONE);
            }
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        mListView = findViewById(R.id.topicsListView);
        RecyclerView.ItemDecoration itemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mListView.addItemDecoration(itemDecoration);
        mListView.setItemAnimator(null);
        mListView.setLayoutManager(new LinearLayoutManager(this));

        if (mViewModel.selectedTopics != null && mViewModel.selectedTopics.size() > 0) {
            mActionMode = startSupportActionMode(mActionModeCallback);
        }

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

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });

        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());

        if (!topicsLoaded) {
            refresh();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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
                showAddDialog(mViewModel.lastEnteredTopic, null);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void showAddDialog(String topic, String errorMsg) {
        if (mViewModel.isRequestActive()) {
            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
        } else {
            AddTopicDlg dlg = new AddTopicDlg();
            Bundle args = new Bundle();
            if (topic != null) {
                args.putString(ARG_TOPIC, topic);
            }
            if (errorMsg != null) {
                args.putString(ARG_TOPIC_ERROR, errorMsg);
            }
            dlg.setArguments(args);

            dlg.show(getSupportFragmentManager(), AddTopicDlg.class.getSimpleName());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_topics, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.menu_refresh);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSelectionChange(int noOfSelectedItemsBefore, int noOfSelectedItems) {
    }

    protected void refresh() {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.getTopics(this);
        }
    }

    public void deleteTopics(List<String> topics) {
        if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.deleteTopics(this, topics);
        }
    }

    public void addTopic(String topic) {
        if (Utils.isEmpty(topic)) {
            showAddDialog(mViewModel.lastEnteredTopic, getString(R.string.dlg_add_error_empty_str));
        } else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            mViewModel.addTopic(this, topic);
        }
        if (!Utils.isEmpty(topic)) {
            mViewModel.lastEnteredTopic = topic;
        }
    }

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.activity_topics_action, menu);
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
                case R.id.action_delete_topics:
                    if (mViewModel.isRequestActive()) {
                        Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                    } else {
                        ConfirmDeleteDlg dlg = new ConfirmDeleteDlg();
                        Bundle args = new Bundle();
                        args.putStringArrayList(SEL_ROWS, new ArrayList<String>(mViewModel.selectedTopics));
                        dlg.setArguments(args);
                        dlg.show(getSupportFragmentManager(), ConfirmDeleteDlg.class.getSimpleName());
                    }

                    handled = true;
                    break;
                case R.id.action_edit_broker:
                    handled = true;
                    break;
            }

            return handled;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (mTopicsRecyclerViewAdapter != null)
                mTopicsRecyclerViewAdapter.clearSelection();
            mActionMode = null;
        }
    };

    public static class ConfirmDeleteDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();
            final ArrayList<String> topics = args.getStringArrayList(SEL_ROWS);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_delete_topic_title));
            if (topics.size() == 1) {
                builder.setMessage(getString(R.string.dlg_delete_topic_msg));
            } else {
                builder.setMessage(getString(R.string.dlg_delete_topic_msg_pl));
            }

            builder.setPositiveButton(R.string.action_delete_topics, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Activity a = getActivity();
                    if (a instanceof TopicsActivity) {
                        ((TopicsActivity) a).deleteTopics(topics);
                    }
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }
    }

    public static class AddTopicDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_add_topics_title));

            LayoutInflater inflater = (LayoutInflater) builder.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
            View body = inflater.inflate(R.layout.dlg_topics_body, null);
            EditText e = body.findViewById(R.id.topic);
            if (e != null) {
                if (args != null) {
                    String errorMsg = args.getString(ARG_TOPIC_ERROR);
                    String topic = args.getString(ARG_TOPIC);
                    e.setText(topic);
                    if (!Utils.isEmpty(errorMsg)) {
                        e.setError(errorMsg);
                    }
                }
            }
            builder.setView(body);

            builder.setPositiveButton(R.string.action_add_topic, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Activity a = getActivity();
                    if (a instanceof TopicsActivity) {
                        EditText v = getDialog().findViewById(R.id.topic);
                        if (v != null) {
                            ((TopicsActivity) a).addTopic(v.getText().toString());
                        }
                    }
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dlg = builder.create();
            /*
            dlg.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {

                    Button b = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
                    if (b != null) {
                        b.setText(R.string.action_add_topic);
                        b.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Log.d(TAG, "on click");
                            }
                        });
                    }
                }
            });
            */
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }

    }


    private TopicsViewModel mViewModel;

    private ActionMode mActionMode;
    private Snackbar mSnackbar;
    private TopicsRecyclerViewAdapter mTopicsRecyclerViewAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mListView;

    private final static String SEL_ROWS = "SEL_ROWS";
    private final static String ARG_TOPIC = "ARG_TOPIC";
    private final static String ARG_TOPIC_ERROR = "ARG_TOPIC_ERROR";

    private final static String TAG = TopicsActivity.class.getSimpleName();

}
