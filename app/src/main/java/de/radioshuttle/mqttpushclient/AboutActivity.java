/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelProviders;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import de.radioshuttle.net.Connection;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        setTitle(R.string.title_about);

        mViewModel = ViewModelProviders.of(this).get(AboutViewModel.class);

        TextView version = (TextView) findViewById(R.id.version);
        if (version != null) {
            String appVersion = "n/a";
            int appVersionCode = 0;
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                if (info != null) {
                    appVersion = info.versionName;
                    appVersionCode = info.versionCode;
                }
            } catch (PackageManager.NameNotFoundException e) {
            }
            String msg = String.format(getString(R.string.about_version), appVersion, appVersionCode);
            version.setText(msg);
        }

        Button homeButton = findViewById(R.id.homeURL);
        if (homeButton != null) {
            homeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Uri webpage = Uri.parse("https://www.radioshuttle.de");
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, webpage);
                    startActivity(webIntent);
                }
            });
        }

        Button helpButton = findViewById(R.id.helpURL);
        if (helpButton != null) {
            helpButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mActivityStarted) {
                        mActivityStarted = true;
                        Intent webIntent = new Intent(AboutActivity.this, HelpActivity.class);
                        startActivityForResult(webIntent, 0);
                    }
                }
            });
        }

        View logo = findViewById(R.id.logo);
        if (logo != null) {
            logo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mViewModel.cnt++;
                    if (mViewModel.cnt == 6) {
                        Connection.debugMode = true;

                        if (Build.VERSION.SDK_INT >= 26) {
                            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(150);
                        }
                    }
                }
            });
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                handleBackPressed();
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

    public static class AboutViewModel extends AndroidViewModel {
        public AboutViewModel(Application app) {
            super(app);
            cnt = 0;
        }
        public int cnt;
    }

    private boolean mActivityStarted;
    AboutViewModel mViewModel;

}
