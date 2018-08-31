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
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.TopicsRequest;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.MODE;
import static de.radioshuttle.mqttpushclient.PushAccount.Topic.*;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class TopicsActivity extends AppCompatActivity implements TopicsRecyclerViewAdapter.RowSelectionListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topics);

        setTitle(getString(R.string.title_topics));

        mViewModel = ViewModelProviders.of(this).get(TopicsViewModel.class);
        boolean topicsLoaded = mViewModel.initialized;

        mViewModel.topicsRequest.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null && request instanceof TopicsRequest) {
                    TopicsRequest topicsRequest = (TopicsRequest) request;
                    PushAccount b = topicsRequest.getAccount();
                    if (b.status == 1) {
                        if (mTopicsRecyclerViewAdapter != null && mTopicsRecyclerViewAdapter.getItemCount() == 0) {
                            if (b.topics.size() > 0) {
                                // special case: configuration change and load in progress -> update view
                                mTopicsRecyclerViewAdapter.setData(b.topics);
                            }
                        }
                    } else {
                        if (mViewModel.isCurrentRequest(request)) {
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
                showEditDialog(mViewModel.lastEnteredTopic, MODE_ADD,null, null);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void showEditDialog(String topic, int mode, String errorMsg, Integer notificationType) {
        if (mViewModel.isRequestActive()) {
            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
        } else {
            EditTopicDlg dlg = new EditTopicDlg();
            Bundle args = new Bundle();
            args.putInt(ARG_EDIT_MODE, mode);
            if (topic != null) {
                args.putString(ARG_TOPIC, topic);
            }
            if (errorMsg != null) {
                args.putString(ARG_TOPIC_ERROR, errorMsg);
            }
            if (notificationType != null) {
                args.putInt(ARG_TOPIC_NTYPE, notificationType);
            }

            dlg.setArguments(args);

            dlg.show(getSupportFragmentManager(), EditTopicDlg.class.getSimpleName());
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

    public void addTopic(String topic, int prio) {
        if (Utils.isEmpty(topic)) {
            showEditDialog(mViewModel.lastEnteredTopic,MODE_ADD, getString(R.string.dlg_add_error_empty_str), prio);
        } else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            PushAccount.Topic t = new PushAccount.Topic();
            t.name = topic;
            t.prio = prio;
            mViewModel.addTopic(this, t);
        }
        if (!Utils.isEmpty(topic)) {
            mViewModel.lastEnteredTopic = topic;
        }
    }

    public void updateTopic(String topic, int prio) {
        if (Utils.isEmpty(topic)) {
            showEditDialog(mViewModel.lastEnteredTopic,MODE_EDIT, getString(R.string.dlg_add_error_empty_str), prio);
        } else if (!mViewModel.isRequestActive()) {
            mSwipeRefreshLayout.setRefreshing(true);
            PushAccount.Topic t = new PushAccount.Topic();
            t.name = topic;
            t.prio = prio;
            mViewModel.updateTopic(this, t);
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
                case R.id.action_edit_topic:
                    if (mViewModel.selectedTopics.size() != 1) {
                        Toast.makeText(getApplicationContext(), R.string.select_single_topic, Toast.LENGTH_LONG).show();
                    } else {
                        if (mViewModel.isRequestActive()) {
                            Toast.makeText(getApplicationContext(), R.string.op_in_progress, Toast.LENGTH_LONG).show();
                        } else {
                            if (mViewModel.selectedTopics.size() == 1 && mViewModel != null) {
                                String t = mViewModel.selectedTopics.iterator().next();
                                TopicsRecyclerViewAdapter a = (TopicsRecyclerViewAdapter) mListView.getAdapter();
                                if (a != null) {
                                    ArrayList<PushAccount.Topic> list = a.getTopics();
                                    if (list != null) {
                                        for(PushAccount.Topic topic : list) {
                                            if (topic.name.equals(t)) {
                                                showEditDialog(topic.name, MODE_EDIT, null, topic.prio);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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

    public static class EditTopicDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = getArguments();

            final int mode = args.getInt(ARG_EDIT_MODE);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            LayoutInflater inflater = (LayoutInflater) builder.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);
            View body = inflater.inflate(R.layout.dlg_topics_body, null);

            final Spinner s = body.findViewById(R.id.notificationtype_spinner);
            if (s != null) {
                s.setAdapter(new NotificationTypeAdapter(getContext()) {
                });
                s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        Map.Entry<Integer, String> sel =
                                (Map.Entry<Integer, String>) parent.getItemAtPosition(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {

                    }
                });
            }

            builder.setView(body);
            EditText e = body.findViewById(R.id.topic);
            String errorMsg = args.getString(ARG_TOPIC_ERROR);
            String topic = args.getString(ARG_TOPIC);

            Integer notificationType = args.getInt(ARG_TOPIC_NTYPE, -1);
            if (notificationType != -1) {
                NotificationTypeAdapter a = (NotificationTypeAdapter) s.getAdapter();
                for (int i = 0; i < s.getAdapter().getCount(); i++) {
                    if (a.getItem(i).getKey() == notificationType.intValue()) {
                        s.setSelection(i);
                        break;
                    }
                }
            }

            if (mode == MODE_ADD) {
                builder.setTitle(getString(R.string.dlg_add_topic_title));
                e.setText(topic);
                if (!Utils.isEmpty(errorMsg)) {
                    e.setError(errorMsg);
                }

                builder.setPositiveButton(R.string.action_add_topic, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Activity a = getActivity();
                        if (a instanceof TopicsActivity) {
                            EditText v = getDialog().findViewById(R.id.topic);
                            if (v != null) {
                                int prio = getSelectedNotificationType(s);
                                ((TopicsActivity) a).addTopic(v.getText().toString(), prio);
                            }
                        }
                    }
                });
            } else { // mode == MODE_EDIT
                builder.setTitle(getString(R.string.dlg_update_topic_title));
                if (e != null) {
                    e.setVisibility(View.GONE);
                }
                TextView label = body.findViewById(R.id.topicLabel);
                label.setText(R.string.dlg_edit_topic_label2);

                TextView e2 = body.findViewById(R.id.topic2);
                e2.setVisibility(View.VISIBLE);
                e2.setText(topic);

                builder.setPositiveButton(R.string.action_update_topic, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Activity a = getActivity();
                        if (a instanceof TopicsActivity) {
                            int prio = getSelectedNotificationType(s);
                            ((TopicsActivity) a).updateTopic(args.getString(ARG_TOPIC), prio);
                        }
                    }
                });
            }

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }

        protected int getSelectedNotificationType(Spinner s) {
            Object o = s.getSelectedItem();
            int prio = NOTIFICATION_HIGH; // default
            if (o != null && o instanceof Map.Entry<?, ?>) {
                Map.Entry<Integer, String> m = (Map.Entry<Integer, String>) s.getSelectedItem();
                Log.d(TAG, "selection: " + m.getKey() + " " + m.getValue());
                prio = m.getKey();
            }
            return prio;
        }


    }


    private TopicsViewModel mViewModel;

    private ActionMode mActionMode;
    private Snackbar mSnackbar;
    private TopicsRecyclerViewAdapter mTopicsRecyclerViewAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mListView;

    private final static String SEL_ROWS = "SEL_ROWS";
    private final static String ARG_EDIT_MODE = "ARG_EDIT_MODE";
    private final static String ARG_TOPIC = "ARG_TOPIC";
    private final static int MODE_ADD = 0;
    private final static int MODE_EDIT = 2;
    private final static String ARG_TOPIC_ERROR = "ARG_TOPIC_ERROR";
    private final static String ARG_TOPIC_NTYPE = "ARG_NOTIFICATION_TYPE";

    private final static String TAG = TopicsActivity.class.getSimpleName();

}
