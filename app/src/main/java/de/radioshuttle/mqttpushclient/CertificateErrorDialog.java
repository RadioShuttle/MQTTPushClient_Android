/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;
import de.radioshuttle.net.AppTrustManager;
import de.radioshuttle.net.CertException;

public class CertificateErrorDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setTitle(R.string.dlg_certerr_title);

            StringBuilder sb = new StringBuilder();
            Bundle args = getArguments();

            String host = args.getString("host", "host");
            sb.append(getString(R.string.dlg_certerr_intro, host));
            // sb.append('\n');

            final String key = args.getString("cert_key", "");
            CertException t = AppTrustManager.mRequestMap.get(key);
            if (t != null) {
                if ((t.reason & AppTrustManager.EXPIRED) > 0) {
                    sb.append("\n- ");
                    sb.append(getString(R.string.dlg_certerr_expired));
                }
                if ((t.reason & AppTrustManager.SELF_SIGNED) > 0) {
                    sb.append("\n- ");
                    sb.append(getString(R.string.dlg_certerr_selfsigned));
                }
                if ((t.reason & AppTrustManager.HOST_NOT_MATCHING) > 0) {
                    sb.append("\n- ");
                    sb.append(getString(R.string.dlg_certerr_invhost));
                }
                if ((t.reason & AppTrustManager.INVALID_CERT_PATH) > 0) {
                    sb.append("\n- ");
                    sb.append(getString(R.string.dlg_certerr_unknown_issuer));
                }
                if ((t.reason & AppTrustManager.OTHER) > 0) {
                    sb.append("\n- ");
                    if (t.getCause() != null && t.getCause().getMessage() != null)
                    sb.append(t.getCause().getMessage());
                }

            }


            // builder.setMessage("The connection to %s uses an invalid certificate.\n\n");

            builder.setMessage(sb);

            builder.setPositiveButton("Add Exception", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CertException t = AppTrustManager.mRequestMap.remove(key);
                    if (t != null && t.chain != null && t.chain.length > 0) {
                        AppTrustManager.addCertificate(t.chain[0], null, true);
                        //TODO: save
                        //TODO: consider calling callback function to refresh last action
                    }
                }
            });

            builder.setNegativeButton(R.string.action_deny, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CertException t = AppTrustManager.mRequestMap.remove(key);
                    if (t != null && t.chain != null && t.chain.length > 0) {
                        AppTrustManager.addCertificate(t.chain[0], null, false);
                    }
                }
            });

            builder.setNeutralButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    CertException t = AppTrustManager.mRequestMap.remove(key);
                }
            });


            return builder.create();
        }


        public static Bundle createArgsFromEx(CertException ex, String host)  {
            Bundle b = null;
            if (ex != null && ex.chain != null && ex.chain.length > 0) {
                b = new Bundle();
                String key = AppTrustManager.getUniqueKey(ex.chain[0]);
                if (host != null)
                    b.putString("host", host);
                b.putString("cert_key", key);
                AppTrustManager.mRequestMap.put(key, ex);
            }
            return b;
        }


}
