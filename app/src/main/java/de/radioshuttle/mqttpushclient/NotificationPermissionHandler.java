package de.radioshuttle.mqttpushclient;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.snackbar.Snackbar;

@RequiresApi(33)
public class NotificationPermissionHandler {
    public NotificationPermissionHandler(AccountListActivity activity) {
        mActivity = activity;

        mLauncherPostNotfications = mActivity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            SharedPreferences settings = activity.getSharedPreferences(PREFS_NOTIF_PERM, Activity.MODE_PRIVATE);
            if (!settings.contains(ALLOW_NOTIFICATIONS_REQUESTED)) {
                SharedPreferences.Editor ed = settings.edit();
                ed.putBoolean(ALLOW_NOTIFICATIONS_REQUESTED, true);
                ed.apply();
            }
            checkPermission();
        });
    }

    public void  checkPermission() {
        boolean showWarning = false;

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(mActivity);

        if (!notificationManager.areNotificationsEnabled()) {
            // Only request permissions if this is a fresh install on Android 13 or later
            SharedPreferences settings = mActivity.getSharedPreferences(PREFS_NOTIF_PERM, Activity.MODE_PRIVATE);
            if (!settings.contains(ALLOW_NOTIFICATIONS_REQUESTED)) {
                if (mActivity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    showWarning = false;
                    mLauncherPostNotfications.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                }
            } else if (mActivity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                showWarning = true;
            } // else: the user has denied notifications at least twice. give up.
        }
        if (showWarning) {
            if (mSnackbarPostNotifications == null) {
                mSnackbarPostNotifications = Snackbar.make(mActivity.findViewById(R.id.root_view), mActivity.getString(R.string.warning_no_notification_perm), Snackbar.LENGTH_INDEFINITE);

                mSnackbarPostNotifications.setAction(mActivity.getString(R.string.action_allow), view -> {
                    mLauncherPostNotfications.launch(Manifest.permission.POST_NOTIFICATIONS);
                });
            }
            mSnackbarPostNotifications.show();
        } else if (mSnackbarPostNotifications !=null && mSnackbarPostNotifications.isShownOrQueued()) {
            mSnackbarPostNotifications.dismiss();
        }
    }

    private AccountListActivity mActivity;
    private ActivityResultLauncher<String> mLauncherPostNotfications;
    private Snackbar mSnackbarPostNotifications;

    private final static String PREFS_NOTIF_PERM = "notif_perm_prefs";
    private final static String ALLOW_NOTIFICATIONS_REQUESTED = "allow_notif_requested";

}
