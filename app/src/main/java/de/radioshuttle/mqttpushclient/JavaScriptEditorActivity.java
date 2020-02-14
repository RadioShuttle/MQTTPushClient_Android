/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import de.radioshuttle.mqttpushclient.dash.Item;
import de.radioshuttle.utils.Utils;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
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

        mViewModel = ViewModelProviders.of(
                this, new JavaScriptViewModel.Factory(getApplication()))
                .get(JavaScriptViewModel.class);

        Bundle args = getIntent().getExtras();
        if (args != null) {

            mComponentType = args.getInt(ARG_COMPONENT);
            mViewModel.setComponentType(mComponentType);

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

            if (!Utils.isEmpty(args.getString(ARG_ITEM))) {
                String jsonStr = args.getString(ARG_ITEM);
                try {
                    JSONObject jsonObject = new JSONObject(jsonStr);
                    mViewModel.mItem = Item.createItemFromJSONObject(jsonObject);
                } catch (Exception e) {
                    Log.e(TAG,"Error parsing arg ARG_ITEM", e);
                }
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
            mTestDataMsgContent = findViewById(R.id.testDataEditText);
            String jsSrc = args.getString(ARG_JAVASCRIPT); // mJavaScriptSource
            if (savedInstanceState == null) {
                mEditor.setText(jsSrc);
                mEditor.requestFocus();
            } else {
                mTestDataLoaded = savedInstanceState.getBoolean("mTestDataLoaded", mTestDataLoaded);
            }
            mResultTextView = findViewById(R.id.headerText);
            mViewModel.javaScriptResult.observe(this, new Observer<JavaScriptViewModel.JSResult>() {
                @Override
                public void onChanged(JavaScriptViewModel.JSResult jsResult) {
                    if (jsResult != null) {
                        if (jsResult.code == 0) {
                            String val = "";
                            if (mComponentType == CONTENT_OUTPUT_DASHBOARD && !Utils.isEmpty(jsResult.result)) {
                                val = new String(Base64.decode(jsResult.result, Base64.DEFAULT));
                            } else {
                                val = jsResult.result;
                            }
                            mResultTextView.setText(getString(R.string.javascript_result) + "\n" + val);

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
            // testDataLabel
            if (mComponentType == CONTENT_OUTPUT_DASHBOARD) {
                TextView v = findViewById(R.id.testDataLabel);
                if (v != null) {
                    v.setText(R.string.test_data_label_outputscript);
                }
            }
            if (!mTestDataLoaded) {
                mViewModel.latestMessage.observe(this, new Observer<JavaScriptViewModel.Request>() {
                    @Override
                    public void onChanged(JavaScriptViewModel.Request request) {
                        if (!mTestDataLoaded) {
                            mTestDataLoaded = true;
                            if (request != null && request.result != null) {
                                mTestDataMsgContent.setText(new String(request.result.getPayload(), Utils.UTF_8));
                            }
                        }
                    }
                });
                if (mComponentType == CONTENT_FILTER || mComponentType == CONTENT_FILTER_DASHBOARD) {
                    mViewModel.loadLastReceivedMsg(mViewModel.mContentFilterCache.get("msg.topic"));
                }
            }
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
            Intent data = new Intent();
            data.putExtra(ARG_JAVASCRIPT, mEditor.getText().toString());
            setResult(AppCompatActivity.RESULT_OK, data);
            finish();
        } else {
            setResult(AppCompatActivity.RESULT_CANCELED);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_java_script_editor, menu);
        if (mComponentType == CONTENT_FILTER) {
            MenuItem mi = menu.findItem(R.id.menu_topicfilter);
            if (mi != null) {
                mi.setVisible(true);
            }
        }
        return true;
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
            /* filter topic examples */
            case R.id.menu_topicfilter_json:
                insertExample(EditTopicActivity.JS_EXAMPLE_JSON);
                handled = true;
                break;
            case R.id.menu_topicfilter_regexp:
                insertExample(EditTopicActivity.JS_EXAMPLE_REGEX);
                handled = true;
                break;
            case R.id.menu_topicfilter_split:
                insertExample(EditTopicActivity.JS_EXAMPLE_SPLIT);
                handled = true;
                break;
            case R.id.menu_topicfilter_binary:
                insertExample(EditTopicActivity.JS_EXAMPLE_BINARY);
                handled = true;
                break;
            case R.id.menu_topicfilter_hexdump:
                insertExample(EditTopicActivity.JS_EXAMPLE_HEXDUMP);
                handled = true;
                break;
            default:
                handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void insertExample(String code) {
        if (!Utils.isEmpty(code)) {
            mEditor.requestFocus();
            String content = mEditor.getText().toString();
            int setStart = content.length();
            if (!Utils.isEmpty(content) && !content.endsWith("\n")) {
                content += "\n";
            }
            content += code;
            mEditor.setText(content);
            mEditor.setSelection(setStart, content.length());
        }
        // Log.d(TAG, "sel start: " + s);
    }

    protected void clear() {
        ConfirmClearDialog dlg = new ConfirmClearDialog();
        dlg.show(getSupportFragmentManager(), ConfirmClearDialog.class.getSimpleName());
    }

    protected void help() {
        if (!mActivityStarted) {
            mActivityStarted = true;
            if (mComponentType == CONTENT_FILTER) {
                Intent webIntent = new Intent(JavaScriptEditorActivity.this, HelpActivity.class);
                webIntent.putExtra(HelpActivity.CONTEXT_HELP, HelpActivity.HELP_TOPIC_FILTER_SCRIPTS);
                startActivityForResult(webIntent, 0);
            } else if (mComponentType == CONTENT_FILTER_DASHBOARD) {
                Toast.makeText(getApplicationContext(), "Not implemented yet", Toast.LENGTH_LONG).show();
                Intent webIntent = new Intent(JavaScriptEditorActivity.this, HelpActivity.class);
                webIntent.putExtra(HelpActivity.CONTEXT_HELP, HelpActivity.HELP_DASH_FILTER_SCRIPT);
                startActivityForResult(webIntent, 0);
            } else if (mComponentType == CONTENT_OUTPUT_DASHBOARD) {
                Toast.makeText(getApplicationContext(), "Not implemented yet", Toast.LENGTH_LONG).show();
                Intent webIntent = new Intent(JavaScriptEditorActivity.this, HelpActivity.class);
                webIntent.putExtra(HelpActivity.CONTEXT_HELP, HelpActivity.HELP_DASH_OUTPUT_SCRIPT);
                startActivityForResult(webIntent, 0);
            }
        }
    }

    protected void runJS() {
        if (mEditor != null) {
            if (mTestDataMsgContent != null) {
                mViewModel.mContentFilterCache.put("msg.text", mTestDataMsgContent.getText().toString());
            }
            mViewModel.runJavaScript(mEditor.getText().toString());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("mTestDataLoaded", mTestDataLoaded);
    }

    protected boolean hasDataChanged() {
        boolean changed = false;
        if (mEditor != null) {
            Bundle args = getIntent().getExtras();
            String org = null;
            if (args != null) {
                org = args.getString(ARG_JAVASCRIPT);
            }
            String current = mEditor.getText().toString();
            changed = !Utils.equals(org, current);
        }
        return changed;
    }

    protected boolean mActivityStarted;
    protected boolean mTestDataLoaded;
    protected JavaScriptViewModel mViewModel;
    protected int mComponentType;
    protected EditText mEditor;
    protected EditText mTestDataMsgContent;
    protected TextView mResultTextView;
    protected ProgressBar mProgressBar;

    public final static String ARG_TITLE = "ARG_TITLE";
    public final static String ARG_HEADER = "ARG_HEADER";
    public final static String ARG_TOPIC = "ARG_TOPIC";
    public final static String ARG_JSPREFIX = "ARG_JSPREFIX";
    public final static String ARG_JSSUFFIX = "ARG_JSSUFFIX";
    public final static String ARG_JAVASCRIPT = "ARG_JAVASCRIPT";
    public final static String ARG_ACCOUNT = "ARG_ACCOUNT";
    public final static String ARG_COMPONENT = "ARG_COMPONENT";
    public final static String ARG_ITEM = "ARG_ITEM";

    public final static int CONTENT_FILTER = 1;
    public final static int CONTENT_FILTER_DASHBOARD = 2;
    public final static int CONTENT_OUTPUT_DASHBOARD = 3;

    private final static String TAG = JavaScriptEditorActivity.class.getSimpleName();
}
