/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.DialogFragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.TopicsRequest;
import de.radioshuttle.utils.Utils;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class TopicsActivity extends AppCompatActivity
        implements TopicsRecyclerViewAdapter.RowSelectionListener,
        CertificateErrorDialog.Callback {

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
                                            int cmd = ((TopicsRequest) request).mCmd;
                                            if (cmd == Cmd.CMD_DEL_TOPICS) {
                                                args.putStringArrayList("topics_del", new ArrayList<>(topicsRequest.mDelTopics));
                                            }
                                            args.putInt("cmd", cmd);

                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                        }
                                    }
                                }
                            } /* end dialog already showing */
                            b.setCertificateExeption(null); // mark es "processed"
                            /* end handle cerificate exception */

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
                                            int cmd = ((TopicsRequest) request).mCmd;
                                            if (cmd == Cmd.CMD_DEL_TOPICS) {
                                                args.putStringArrayList("topics_del", new ArrayList<>(topicsRequest.mDelTopics));
                                            }
                                            args.putInt("cmd", cmd);
                                            dialog.setArguments(args);
                                            dialog.show(getSupportFragmentManager(), DLG_TAG);
                                        }
                                    }
                                }
                            }
                            b.inSecureConnectionAsk = false; // mark as "processed"

                        }
                        if (b.requestStatus != Cmd.RC_OK) {
                            String t = (b.requestErrorTxt == null ? "" : b.requestErrorTxt);
                            if (b.requestStatus == Cmd.RC_MQTT_ERROR || (b.requestStatus == Cmd.RC_NOT_AUTHORIZED && b.requestErrorCode != 0)) {
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
                showEditTopicActivity(EditTopicActivity.MODE_ADD, null, 0, null);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void showEditTopicActivity(int mode, String topic, int prio, String javaScript) {
        if (!mActivityStarted) {
            mActivityStarted = true;
            Bundle topicArgs = getIntent().getExtras();

            Bundle args = new Bundle();
            args.putString(PARAM_ACCOUNT_JSON, topicArgs.getString(PARAM_ACCOUNT_JSON));
            args.putInt(EditTopicActivity.MODE, mode);
            if (mode == EditTopicActivity.MODE_EDIT) {
                args.putString(EditTopicActivity.ARG_TOPIC, topic);
                args.putInt(EditTopicActivity.ARG_TOPIC_NTYPE, prio);
                if (!Utils.isEmpty(javaScript)) {
                    args.putString(EditTopicActivity.ARG_JAVASCRIPT, javaScript);
                }
            }
            Intent intent = new Intent(this, EditTopicActivity.class);
            intent.putExtras(args);
            startActivityForResult(intent, 0);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
            if (!mViewModel.isRequestActive()) {
                try {
                    String resultTopics = data.getStringExtra(EditTopicActivity.RESULT_TOPICS);
                    if (Utils.isEmpty(resultTopics)) {
                        return;
                    }
                    JSONArray resultArr = new JSONArray(resultTopics);
                    ArrayList<PushAccount.Topic> topicList = new ArrayList<>();
                    PushAccount.Topic e;
                    JSONObject jt;
                    for(int i = 0; i < resultArr.length(); i++) {
                        e = new PushAccount.Topic();
                        jt = resultArr.getJSONObject(i);
                        e.name = jt.optString("topic");
                        e.prio = jt.optInt("prio");
                        e.jsSrc = jt.optString("jsSrc");
                        topicList.add(e);
                    }
                    Collections.sort(topicList, new PushAccount.TopicComparator());

                    TopicsRequest request = (TopicsRequest) mViewModel.topicsRequest.getValue();
                    if (request == null) {
                        request = new TopicsRequest(getApplication(), mViewModel.pushAccount, mViewModel.topicsRequest);
                    }
                    request.getAccount().topics = topicList;
                    mViewModel.topicsRequest.setValue(request);
                } catch(Exception e) {
                    Log.d(TAG, "onActivityResult(): error: ", e);
                }
            }
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
                                                showEditTopicActivity(EditTopicActivity.MODE_EDIT, topic.name, topic.prio, topic.jsSrc);
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

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd", 0);
            if (cmd == 0) {
                refresh();
            } else if (cmd == Cmd.CMD_DEL_TOPICS && args.getStringArrayList("topics_del") != null) {
                deleteTopics(args.getStringArrayList("topics_del"));
            }
        }

    }

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

    private TopicsViewModel mViewModel;
    private boolean mActivityStarted;

    private ActionMode mActionMode;
    private Snackbar mSnackbar;
    private TopicsRecyclerViewAdapter mTopicsRecyclerViewAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mListView;

    private final static String SEL_ROWS = "SEL_ROWS";

    private final static String TAG = TopicsActivity.class.getSimpleName();

}
