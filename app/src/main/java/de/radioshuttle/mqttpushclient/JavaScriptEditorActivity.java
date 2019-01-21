/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import de.radioshuttle.utils.Utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;


public class JavaScriptEditorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_java_script_editor);

        setTitle("JavaScript Editor"); //Default will be overriden

        mViewModel = ViewModelProviders.of(this).get(JavaScriptViewModel.class);

        Bundle args = getIntent().getExtras();
        if (args != null) {

            mComponentType = args.getInt(ARG_COMPONENT);

            if (!Utils.isEmpty(args.getString(ARG_TITLE))) {
                setTitle(args.getString(ARG_TITLE));
            }

            if (!Utils.isEmpty(args.getString(ARG_HEADER))) {
                TextView header = findViewById(R.id.headerText);
                header.setText(args.getString(ARG_HEADER));
            }
            if (!Utils.isEmpty(args.getString(ARG_JSPREFIX))) {
                TextView pref = findViewById(R.id.functionPrefix);
                pref.setText(args.getString(ARG_JSPREFIX));
            }
            if (!Utils.isEmpty(args.getString(ARG_JSSUFFIX))) {
                TextView pref = findViewById(R.id.functionSuffix);
                pref.setText(args.getString(ARG_JSSUFFIX));
            }

            if (!Utils.isEmpty(args.getString(ARG_ACCOUNT))) {
                try {
                    if (mViewModel.mAccount == null) { // savedInstanceState == null
                        mViewModel.mAccount = PushAccount.createAccountFormJSON(new JSONObject(args.getString(ARG_ACCOUNT)));

                        if (!Utils.isEmpty(args.getString(ARG_TOPIC))) {
                            mViewModel.mContentFilterCache.put("msg.topic", args.getString(ARG_TOPIC));
                        }
                        mViewModel.mContentFilterCache.put("acc.user", mViewModel.mAccount.user);
                        try {
                            URI u = new URI(mViewModel.mAccount.uri);
                            mViewModel.mContentFilterCache.put("acc.mqttServer", u.getAuthority());
                        } catch (Exception e) {
                            Log.d(TAG, "URI parse error: ", e);
                        }
                        mViewModel.mContentFilterCache.put("acc.pushServer", mViewModel.mAccount.pushserver);
                    }
                } catch (JSONException e) {
                    Log.d(TAG, "Error creating account from json: " + e.getMessage());
                }
            }
            mEditor = findViewById(R.id.functionEditText);
            String jsSrc = args.getString(ARG_JAVASCRIPT); // mJavaScriptSource
            if (savedInstanceState == null) {
                mJavaScriptSource = jsSrc;
                if (!Utils.isEmpty(mJavaScriptSource)) {
                    mEditor.setText(mJavaScriptSource);
                }
                mEditor.requestFocus();
            } else {
                mJavaScriptSource = savedInstanceState.getString(ARG_JAVASCRIPT);
            }
            mResultTextView = findViewById(R.id.headerText);
            mViewModel.javaScriptResult.observe(this, new Observer<JavaScriptViewModel.JSResult>() {
                @Override
                public void onChanged(JavaScriptViewModel.JSResult jsResult) {
                    if (jsResult != null) {
                        if (jsResult.code == 0) {
                            mResultTextView.setText(getString(R.string.javascript_result) + "\n" + jsResult.result);
                        } else if (jsResult.code == 1) {
                            mResultTextView.setText(getString(R.string.javascript_err) + "\n" + getString(R.string.javascript_err_timeout));
                        } else {
                            mResultTextView.setText(getString(R.string.javascript_err) + "\n" + jsResult.errorMsg);
                        }
                    }
                }
            });
            mProgressBar = findViewById(R.id.runProgressBar);
            mViewModel.runState.observe(this, new Observer<Boolean>() {
                @Override
                public void onChanged(Boolean aBoolean) {
                    if (aBoolean != null && aBoolean) {
                        if (mProgressBar.getVisibility() != View.VISIBLE) {
                            mProgressBar.setVisibility(View.VISIBLE);
                        }
                    } else {
                        if (mProgressBar.getVisibility() != View.GONE) {
                            mProgressBar.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }

        Button saveButton = findViewById(R.id.saveJSButton);
        if (saveButton != null) {
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    save();
                }
            });
        }
        Button testButton = findViewById(R.id.testJSButton);
        if (testButton != null) {
            testButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    test();
                }
            });
        }
        Button runButton = findViewById(R.id.runJSButton);
        if (runButton != null) {
            runButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runJS();
                }
            });
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
        // super.onBackPressed();
    }

    protected void handleBackPressed() {
        if (hasDataChanged()) {
            QuitWithoutSaveDlg dlg = new QuitWithoutSaveDlg();
            dlg.show(getSupportFragmentManager(), QuitWithoutSaveDlg.class.getSimpleName());
        } else {
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_java_script_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        /*
        MenuItem m = menu.findItem(R.id.menu_refresh);
        if (m != null) {
            m.setEnabled(!mViewModel.isRequestActive());
        }
        */

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled;
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                handled = true;
                break;
            case R.id.menu_save:
                save();
                handled = true;
                break;
            case R.id.menu_test:
                test();
                handled = true;
                break;
            case R.id.menu_run:
                runJS();
                handled = true;
                break;
            case R.id.menu_clear:
                clear();
                handled = true;
                break;
            case R.id.menu_help:
                help();
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }


    protected void test() {
        if (mComponentType == CONTENT_FILTER) {
            TestDataDialog dlg = new TestDataDialog();
            dlg.show(getSupportFragmentManager(), TestDataDialog.class.getSimpleName());
        }
    }

    protected void clear() {
        ConfirmClearDlg dlg = new ConfirmClearDlg();
        dlg.show(getSupportFragmentManager(), ConfirmClearDlg.class.getSimpleName());
    }

    protected void help() {
        if (!mActivityStarted) {
            mActivityStarted = true;
            if (mComponentType == CONTENT_FILTER) {
                Intent webIntent = new Intent(JavaScriptEditorActivity.this, HelpActivity.class);
                webIntent.putExtra(HelpActivity.CONTEXT_HELP, HelpActivity.HELP_TOPIC_FILTER_SCRIPTS);
                startActivityForResult(webIntent, 0);
            }
        }
    }

    protected void runJS() {
        if (mEditor != null) {
            mViewModel.runJavaScript( mEditor.getText().toString());
        }
    }

    protected void save() {
        if (checkData()) {
            if (!hasDataChanged()) {
                Toast.makeText(getApplicationContext(), R.string.error_js_unmodified, Toast.LENGTH_LONG).show();
            } else {
                Intent data = new Intent();
                mJavaScriptSource = mEditor.getText().toString();
                data.putExtra(ARG_JAVASCRIPT, mJavaScriptSource);
                setResult(AppCompatActivity.RESULT_OK, data);
                finish();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mJavaScriptSource != null) {
            outState.putString(ARG_JAVASCRIPT, mJavaScriptSource);
        }
    }

    protected boolean hasDataChanged() {
        boolean changed = false;
        if (mEditor != null) {
            String prev = (mJavaScriptSource == null ? "" : mJavaScriptSource);
            changed = !mEditor.getText().toString().equals(prev);
        }
        return changed;
    }

    protected boolean checkData() {
        return true;
    }

    public static class QuitWithoutSaveDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_back_without_save_title));
            builder.setMessage(getString(R.string.dlg_back_without_save_js_msg));

            builder.setPositiveButton(R.string.action_back, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getActivity().setResult(AppCompatActivity.RESULT_CANCELED);
                    getActivity().finish();
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

    public static class ConfirmClearDlg extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_confirm_clear_title));
            // builder.setMessage(getString(R.string.dlg_back_without_save_js_msg));

            builder.setPositiveButton(R.string.action_clear, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    FragmentActivity a = getActivity();
                    if (a instanceof JavaScriptEditorActivity) {
                        if (((JavaScriptEditorActivity) a).mEditor != null) {
                            ((JavaScriptEditorActivity) a).mEditor.setText(null);
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
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }
    }

    public static class TestDataDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            Bundle args = getArguments();

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getString(R.string.dlg_testdata_title));
            // builder.setMessage(getString(R.string.dlg_back_without_save_js_msg));

            LayoutInflater inflater = (LayoutInflater) builder.getContext().getSystemService(LAYOUT_INFLATER_SERVICE);

            model = ViewModelProviders.of(getActivity()).get(JavaScriptViewModel.class);

            View body = inflater.inflate(R.layout.dlg_testdata_body, null);
            mContent = body.findViewById(R.id.msgContent);
            mTopic = body.findViewById(R.id.msgTopic);
            mUser = body.findViewById(R.id.accUser);
            mMqttServer = body.findViewById(R.id.accMqttServer);
            mPushServer = body.findViewById(R.id.accPushServer);

            Button importButton = body.findViewById(R.id.importButton);
            if (importButton != null && mTopic != null) {
                importButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        model.loadLastReceivedMsg(getActivity().getApplication(), mTopic.getText().toString());
                    }
                });

                model.latestMessage.observe(this, new Observer<JavaScriptViewModel.Request>() {
                    @Override
                    public void onChanged(JavaScriptViewModel.Request request) {
                        if (model.isCurrentRequest(request)) {
                            model.confirmResultDelivered();
                            if (request.result != null && mContent != null) {
                                // model.mContentFilterCache.put("msg.content", request.result.getMsg());
                                mContent.setText(request.result.getMsg());
                            } else {
                                if (mTopic != null) {
                                    mTopic.setError(getString((R.string.dlg_testdata_err_not_found)));
                                }
                            }
                        }
                    }
                });
            }

            if (savedInstanceState == null) {
                mContent.setText(model.mContentFilterCache.get("msg.content"));
                mTopic.setText(model.mContentFilterCache.get("msg.topic"));
                mUser.setText(model.mContentFilterCache.get("acc.user"));
                mMqttServer.setText(model.mContentFilterCache.get("acc.mqttServer"));
                mPushServer.setText(model.mContentFilterCache.get("acc.pushServer"));
            }

            builder.setView(body);
            builder.setPositiveButton(R.string.action_set, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    model.mContentFilterCache.put("msg.content", mContent.getText().toString());
                    model.mContentFilterCache.put("msg.topic", mTopic.getText().toString());
                    model.mContentFilterCache.put("acc.user", mUser.getText().toString());
                    model.mContentFilterCache.put("acc.mqttServer", mMqttServer.getText().toString());
                    model.mContentFilterCache.put("acc.pushServer", mPushServer.getText().toString());
                    /*
                    FragmentActivity a = getActivity();
                    if (a instanceof JavaScriptEditorActivity) {
                        ((JavaScriptEditorActivity) a).runJS();
                    }
                    */
                }
            });

            builder.setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            builder.setNeutralButton(R.string.action_clear, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    model.mContentFilterCache.put("msg.content", "");
                    model.mContentFilterCache.put("msg.topic", "");
                    model.mContentFilterCache.put("acc.user", "");
                    model.mContentFilterCache.put("acc.mqttServer", "");
                    model.mContentFilterCache.put("acc.pushServer", "");
                    FragmentActivity a = getActivity();
                    if (a instanceof JavaScriptEditorActivity) {
                        ((JavaScriptEditorActivity) a).test();
                    }

                }
            });

            AlertDialog dlg = builder.create();
            dlg.setCanceledOnTouchOutside(false);

            return dlg;
        }
        EditText mContent;
        EditText mTopic;
        EditText mUser;
        EditText mMqttServer;
        EditText mPushServer;
        JavaScriptViewModel model;
    }

    private boolean mActivityStarted;
    protected JavaScriptViewModel mViewModel;
    protected int mComponentType;
    protected EditText mEditor;
    protected TextView mResultTextView;
    protected ProgressBar mProgressBar;
    private String mJavaScriptSource;

    public final static String ARG_TITLE = "ARG_TITLE";
    public final static String ARG_HEADER = "ARG_HEADER";
    public final static String ARG_TOPIC = "ARG_TOPIC";
    public final static String ARG_JSPREFIX = "ARG_JSPREFIX";
    public final static String ARG_JSSUFFIX = "ARG_JSSUFFIX";
    public final static String ARG_JAVASCRIPT = "ARG_JAVASCRIPT";
    public final static String ARG_ACCOUNT = "ARG_ACCOUNT";
    public final static String ARG_COMPONENT = "ARG_COMPONENT";

    public final static int CONTENT_FILTER = 1;

    private final static String TAG = JavaScriptEditorActivity.class.getSimpleName();
}
