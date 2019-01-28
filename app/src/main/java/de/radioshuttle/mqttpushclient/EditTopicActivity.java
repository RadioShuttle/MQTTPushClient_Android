/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.net.Connection;
import de.radioshuttle.net.Request;
import de.radioshuttle.net.TopicsRequest;
import de.radioshuttle.utils.Utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.PushAccount.Topic.NOTIFICATION_HIGH;

public class EditTopicActivity extends AppCompatActivity implements CertificateErrorDialog.Callback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_topic);

        Bundle args = getIntent().getExtras();

        mViewModel = ViewModelProviders.of(this).get(TopicsViewModel.class);
        try {
            String json = args.getString(PARAM_ACCOUNT_JSON);
            mViewModel.init(json);
        } catch (JSONException e) {
            Log.e(TAG, "parse error", e);
        }

        mNotificationsSpinner = findViewById(R.id.notificationtype_spinner);
        if (mNotificationsSpinner != null) {
            mNotificationsSpinner.setAdapter(new NotificationTypeAdapter(this));
            mNotificationsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
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

        if (savedInstanceState == null) {
            mMode = (args != null ? args.getInt(MODE, MODE_ADD) : MODE_ADD);
            mTopic = (args != null ? args.getString(ARG_TOPIC) : null);
            mNotificationType = args.getInt(ARG_TOPIC_NTYPE, NOTIFICATION_HIGH);
            mJavascript = args.getString(ARG_JAVASCRIPT);
            mEditedJavascript = mJavascript;
            mSaved = false;
            mSavedTopics = null;

            if (mNotificationType != -1) {
                NotificationTypeAdapter a = (NotificationTypeAdapter) mNotificationsSpinner.getAdapter();
                for (int i = 0; i < mNotificationsSpinner.getAdapter().getCount(); i++) {
                    if (a.getItem(i).getKey() == mNotificationType) {
                        mNotificationsSpinner.setSelection(i);
                        break;
                    }
                }
            }

        } else {
            mMode = savedInstanceState.getInt(MODE, MODE_ADD);
            mEditedJavascript = savedInstanceState.getString(ARG_JAVASCRIPT);

            mTopic = savedInstanceState.getString(IS_TOPIC);
            mNotificationType = savedInstanceState.getInt(IS_TOPIC_NTYPE, -1);
            mJavascript = savedInstanceState.getString(IS_JAVASCRIPT);
            mSaved = savedInstanceState.getBoolean(IS_SAVED, false);
            mSavedTopics = savedInstanceState.getString(RESULT_TOPICS);
        }

        mTopicsEditText = findViewById(R.id.topic);
        mTopicsEditText.setText(mTopic);
        mScriptButton = findViewById(R.id.filterButton);
        mScriptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openJavaScriptEditor();
            }
        });

        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mViewModel.topicsRequest.observe(this, new Observer<Request>() {
            @Override
            public void onChanged(@Nullable Request request) {
                if (request != null && request instanceof TopicsRequest) {
                    TopicsRequest topicsRequest = (TopicsRequest) request;
                    PushAccount b = topicsRequest.getAccount();
                    if (b.status == 1) {
                        mSwipeRefreshLayout.setRefreshing(true);
                    } else {
                        boolean isNew = false;
                        if (mViewModel.isCurrentRequest(request)) {
                            isNew = mViewModel.isRequestActive(); // result already processed/displayed?
                            mViewModel.confirmResultDelivered();
                            mSwipeRefreshLayout.setRefreshing(false);
                            updateUI(true);

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
                                            if (cmd == Cmd.CMD_ADD_TOPICS || cmd == Cmd.CMD_UPD_TOPICS) {
                                                Cmd.Topic val;
                                                Iterator<Map.Entry<String, Cmd.Topic>> it = topicsRequest.mTopics.entrySet().iterator();
                                                if (it.hasNext()) {
                                                    Map.Entry<String, Cmd.Topic> e = it.next();
                                                    args.putString("topic_name", e.getKey());
                                                    val = e.getValue();
                                                    args.putInt("topic_prio", val.type);
                                                    args.putString("topic_script", val.script);
                                                }
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
                                            if (cmd == Cmd.CMD_ADD_TOPICS || cmd == Cmd.CMD_UPD_TOPICS) {
                                                Cmd.Topic val;
                                                Iterator<Map.Entry<String, Cmd.Topic>> it = topicsRequest.mTopics.entrySet().iterator();
                                                if (it.hasNext()) {
                                                    Map.Entry<String, Cmd.Topic> e = it.next();
                                                    args.putString("topic_name", e.getKey());
                                                    val = e.getValue();
                                                    args.putInt("topic_prio", val.type);
                                                    args.putString("topic_script", val.script);
                                                }
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
                                t = EditTopicActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                            }
                            showErrorMsg(t);
                        } else {
                            if (topicsRequest.requestStatus != Cmd.RC_OK) { // topics add or delete result
                                String t = (topicsRequest.requestErrorTxt == null ? "" : topicsRequest.requestErrorTxt);
                                if (topicsRequest.requestStatus == Cmd.RC_MQTT_ERROR) {
                                    t = EditTopicActivity.this.getString(R.string.errormsg_mqtt_prefix) + " " + t;
                                }
                                showErrorMsg(t);
                            } else {
                                if (mSnackbar != null && mSnackbar.isShownOrQueued()) {
                                    mSnackbar.dismiss();
                                }
                                // set new "saved" state
                                if (topicsRequest.mTopics != null) { // should always be the case

                                    Iterator<Map.Entry<String, Cmd.Topic>> it = topicsRequest.mTopics.entrySet().iterator();
                                    if (it.hasNext()) {
                                        if (mMode == MODE_ADD) {
                                            mMode = MODE_EDIT;
                                        }
                                        Map.Entry<String, Cmd.Topic> e = it.next();
                                        Cmd.Topic ct = e.getValue();
                                        mJavascript = ct.script;
                                        // mEditedJavascript = ct.script;
                                        mNotificationType = ct.type;
                                        mTopic = e.getKey();
                                        updateUI(true);
                                        try {
                                            JSONArray resultTopics = new JSONArray();
                                            JSONObject te;
                                            for(PushAccount.Topic t : b.topics) {
                                                te = new JSONObject();
                                                te.put("topic", t.name);
                                                te.put("prio", t.prio);
                                                te.put("jsSrc", t.jsSrc == null ? "" : t.jsSrc);
                                                resultTopics.put(te);
                                            }
                                            mSavedTopics = resultTopics.toString();
                                        } catch (Exception e1) {
                                            Log.d(TAG, "onChanged(): Error parsing json object", e1);
                                        }
                                        mSaved = true;
                                        if (isNew) {
                                            Toast.makeText(getApplicationContext(), R.string.info_data_saved, Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        mSaveButton = findViewById(R.id.save_button);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setEnabled(false);
        mSwipeRefreshLayout.setRefreshing(mViewModel.isRequestActive());
        updateUI(!mViewModel.isRequestActive());
    }

    protected void updateUI(boolean enableFields) {
        if (mMode == MODE_ADD) {
            setTitle(R.string.dlg_add_topic_title);
        } else {
            setTitle(R.string.dlg_update_topic_title);
        }
        if (mScriptButton != null) {
            if (!Utils.isEmpty(mEditedJavascript)) {
                if (Utils.equals(mJavascript, mEditedJavascript)) {
                    mScriptButton.setText(R.string.dlg_filter_button_edit);
                } else {
                    mScriptButton.setText(R.string.dlg_filter_button_edit_modified);
                }
            } else {
                mScriptButton.setText(R.string.dlg_filter_button_add);
            }
        }
        mTopicsEditText.setEnabled(mMode == MODE_ADD && enableFields);
        mScriptButton.setEnabled(mMode == MODE_EDIT && enableFields);
        mNotificationsSpinner.setEnabled(enableFields);
        mSaveButton.setEnabled(enableFields);
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_edit_topic, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem m = menu.findItem(R.id.action_save);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive());
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(MODE, mMode);

        if (!Utils.isEmpty(mTopic)) {
            outState.putString(IS_TOPIC, mTopic);
        }

        outState.putInt(IS_TOPIC_NTYPE, mNotificationType);


        if (!Utils.isEmpty(mEditedJavascript)) {
            outState.putString(ARG_JAVASCRIPT, mEditedJavascript);
        }
        if (!Utils.isEmpty(mJavascript)) {
            outState.putString(IS_JAVASCRIPT, mJavascript);
        }
        outState.putBoolean(IS_SAVED, mSaved);

        if (!Utils.isEmpty(mSavedTopics)) {
            outState.putString(RESULT_TOPICS, mSavedTopics);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home :
                handleBackPressed();
                return true;
            case R.id.action_save:
                save();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void save() {
        if (checkData()) {
            if (hasDataChanged()) {
                updateUI(false);
                PushAccount.Topic t = new PushAccount.Topic();
                t.name = mTopicsEditText.getText().toString();
                t.prio = getSelectedNotificationType(mNotificationsSpinner);
                t.jsSrc = mEditedJavascript;
                if (mMode == MODE_ADD) {
                    mViewModel.addTopic(getApplication(), t);
                } /* else if (mMode == MODE_EDIT) */ {
                    mViewModel.updateTopic(getApplication(), t);
                }
                if (mSnackbar != null) {
                    mSnackbar.dismiss();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.error_data_unmodified, Toast.LENGTH_LONG).show();
            }
        }
    }

    protected boolean checkData() {
        boolean ok = true;
        if (Utils.isEmpty(mTopicsEditText.getText().toString())) {
            mTopicsEditText.setError(getString(R.string.error_empty_field));
            ok = false;
        }
        return ok;
    }

    protected boolean hasDataChanged() {
        boolean changed = false;
        String topic = mTopicsEditText.getText().toString();
        int notificationType = getSelectedNotificationType(mNotificationsSpinner);
        if (!Utils.equals(topic, mTopic)) {
            changed = true;
        } else if (mNotificationType != notificationType) {
            changed = true;
        } else if (!Utils.equals(mJavascript, mEditedJavascript)) {
            changed = true;
        }
        return changed;
    }

    protected void handleBackPressed() {
        if (hasDataChanged()) {
            QuitWithoutSaveDlg dlg = new QuitWithoutSaveDlg();
            if (mSaved) {
                Bundle args = new Bundle();
                args.putString(RESULT_TOPICS, mSavedTopics);
                dlg.setArguments(args);
            }
            dlg.show(getSupportFragmentManager(), QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            if (mSaved) {
                Intent data = new Intent();
                data.putExtra(RESULT_TOPICS, mSavedTopics);
                setResult(AppCompatActivity.RESULT_OK, data);
                finish();
            }
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
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

    protected void showErrorMsg(String msg) {
        View v = findViewById(R.id.rView);
        if (v != null) {
            mSnackbar = Snackbar.make(v, msg, Snackbar.LENGTH_INDEFINITE);
            mSnackbar.show();
        }
    }

    @Override
    public void retry(Bundle args) {
        if (args != null) {
            int cmd = args.getInt("cmd", 0);
            if (cmd == Cmd.CMD_ADD_TOPICS || cmd == Cmd.CMD_UPD_TOPICS) {
                PushAccount.Topic t = new PushAccount.Topic();
                t.name = args.getString("topic_name");
                t.prio = args.getInt("topic_prio");
                t.jsSrc = args.getString("topic_script");

                if (!mViewModel.isRequestActive()) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    if (cmd == Cmd.CMD_ADD_TOPICS) {
                        mViewModel.addTopic(getApplicationContext(), t);
                    } else {
                        mViewModel.updateTopic(getApplicationContext(), t);
                    }
                }
            }
        }
    }

    protected void openJavaScriptEditor() {
        if (!mActivityStarted) {
            mActivityStarted = true;

            Intent intent = new Intent(this, JavaScriptEditorActivity.class);
            if (Utils.isEmpty(mEditedJavascript)) {
                intent.putExtra(JavaScriptEditorActivity.ARG_TITLE, getString(R.string.title_add_javascript));
            } else {
                intent.putExtra(JavaScriptEditorActivity.ARG_TITLE, getString(R.string.title_edit_javascript));
                intent.putExtra(JavaScriptEditorActivity.ARG_JAVASCRIPT, mEditedJavascript);
            }

            String header = getString(R.string.dlg_filter_header);
            if (!Utils.isEmpty(mTopic)) {
                intent.putExtra(JavaScriptEditorActivity.ARG_TOPIC, mTopic);
                header += " " + mTopic;
            }
            header += ":";
            intent.putExtra(JavaScriptEditorActivity.ARG_HEADER, header);
            intent.putExtra(JavaScriptEditorActivity.ARG_JSPREFIX, "function filterMsg(msg, acc) {\n var content = msg.content;");
            intent.putExtra(JavaScriptEditorActivity.ARG_JSSUFFIX, " return content;\n}");
            intent.putExtra(JavaScriptEditorActivity.ARG_COMPONENT, JavaScriptEditorActivity.CONTENT_FILTER);

            Bundle args = getIntent().getExtras();
            String acc = args.getString(PARAM_ACCOUNT_JSON);
            if (!Utils.isEmpty(acc)) {
                intent.putExtra(JavaScriptEditorActivity.ARG_ACCOUNT, acc);
            }
            startActivityForResult(intent, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
        // requestCode == 1 for JavaScriptEditor
        if (requestCode == 1 && resultCode == AppCompatActivity.RESULT_OK) {
            mEditedJavascript = data.getStringExtra(JavaScriptEditorActivity.ARG_JAVASCRIPT);
            updateUI(true);
        }
    }

    public static class QuitWithoutSaveDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_back_without_save_title));
            builder.setMessage(getString(R.string.dlg_back_without_save_msg));

            builder.setPositiveButton(R.string.action_back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Bundle args = getArguments();
                    if (args != null && !Utils.isEmpty(args.getString(RESULT_TOPICS))) {
                        Intent data = new Intent();
                        data.putExtra(RESULT_TOPICS, args.getString(RESULT_TOPICS));
                        getActivity().setResult(AppCompatActivity.RESULT_OK, data);
                        getActivity().finish();
                    } else {
                        getActivity().setResult(AppCompatActivity.RESULT_CANCELED);
                        getActivity().finish();
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

    private boolean mActivityStarted;

    private Snackbar mSnackbar;
    protected TopicsViewModel mViewModel;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    protected int mMode;
    protected String mEditedJavascript; // filter script of last edit operaion

    protected boolean mSaved;
    protected String mSavedTopics; // json of saved topics
    protected String mJavascript; // last saved script code
    protected int mNotificationType; // last saved notification type
    protected String mTopic;

    protected EditText mTopicsEditText;
    protected Spinner mNotificationsSpinner;
    protected Button mScriptButton;
    protected Button mSaveButton;

    public final static String MODE = "MODE";
    public final static int MODE_ADD = 1;
    public final static int MODE_EDIT = 2;

    public final static String ARG_TOPIC = "ARG_TOPIC";
    public final static String ARG_JAVASCRIPT = "ARG_JAVASCRIPT";
    public final static String ARG_TOPIC_NTYPE = "ARG_NOTIFICATION_TYPE";
    public final static String RESULT_TOPICS = "RESULT_TOPICS";

    private final static String IS_TOPIC = "IS_TOPIC";
    private final static String IS_JAVASCRIPT = "IS_JAVASCRIPT";
    private final static String IS_TOPIC_NTYPE = "IS_NOTIFICATION_TYPE";
    private final static String IS_SAVED = "IS_SAVED";


    private final static String TAG = EditTopicActivity.class.getSimpleName();

}
