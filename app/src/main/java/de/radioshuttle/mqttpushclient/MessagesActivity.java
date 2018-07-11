/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.arch.lifecycle.ViewModel;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import static de.radioshuttle.mqttpushclient.AccountListActivity.RC_SUBSCRIPTIONS;
import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_BROKER_JSON;

public class MessagesActivity extends AppCompatActivity {

    static class MessagesViewModel extends ViewModel {
        public PushAccount pushAccount;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        setTitle(getString(R.string.title_messages));

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_BROKER_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);
        try {
            PushAccount b = PushAccount.createBrokerFormJSON(new JSONObject(json));
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

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_messages, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home :
                handleBackPressed();
                return true;
            case R.id.action_subscriptions :
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    Bundle args = getIntent().getExtras();
                    Intent intent = new Intent(this, TopicsActivity.class);
                    intent.putExtra(PARAM_BROKER_JSON, args.getString(PARAM_BROKER_JSON));
                    intent.putExtra(PARAM_MULTIPLE_PUSHSERVERS, args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS));
                    startActivityForResult(intent, RC_SUBSCRIPTIONS);
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mActivityStarted = false;
    }

    public final static String PARAM_MULTIPLE_PUSHSERVERS = "PARAM_MULTIPLE_PUSHSERVERS";

    private boolean mActivityStarted;

    private final static String TAG = MessagesActivity.class.getSimpleName();
}
