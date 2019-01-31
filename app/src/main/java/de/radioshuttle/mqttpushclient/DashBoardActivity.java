/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import androidx.appcompat.app.AppCompatActivity;
import de.radioshuttle.mqttpushclient.dash.ViewState;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import static de.radioshuttle.mqttpushclient.EditAccountActivity.PARAM_ACCOUNT_JSON;
import static de.radioshuttle.mqttpushclient.MessagesActivity.PARAM_MULTIPLE_PUSHSERVERS;

public class DashBoardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dash_board);

        Bundle args = getIntent().getExtras();
        String json = args.getString(PARAM_ACCOUNT_JSON);
        boolean hastMultipleServer = args.getBoolean(PARAM_MULTIPLE_PUSHSERVERS);

        PushAccount b = null;
        try {
            b = PushAccount.createAccountFormJSON(new JSONObject(json));
            if (savedInstanceState == null) {
                ViewState.getInstance(getApplication()).setLastState(b.getKey(), ViewState.VIEWSTATE_DASHBOARD);
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

        setTitle(getString(R.string.title_dashboard));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
                return true;
            case R.id.menu_change_view :
                if (!mActivityStarted) {
                    mActivityStarted = true;
                    switchToMessagesActivity();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.activity_dash_board, menu);
        return true;
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

    protected void handleBackPressed() {

        Intent intent = new Intent();
        intent.putExtra(AccountListActivity.ARG_NOTIFSTART, getIntent().getBooleanExtra(AccountListActivity.ARG_NOTIFSTART, false));
        setResult(AppCompatActivity.RESULT_OK, intent);
        finish();
    }
    boolean mActivityStarted;

    private final static String TAG = DashBoardActivity.class.getSimpleName();
}
