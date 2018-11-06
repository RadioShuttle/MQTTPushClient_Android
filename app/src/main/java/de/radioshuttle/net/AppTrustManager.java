/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.net;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class AppTrustManager implements X509TrustManager {

    private X509TrustManager defaultTrustManager;
    private X509TrustManager myTrustManager;

    public AppTrustManager() throws Exception {

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        TrustManager tms [] = tmf.getTrustManagers();
        boolean found = false;
        for(int i = 0; i < tms.length; i++) {
            if (tms[i] instanceof X509TrustManager) {
                defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[i];

                found = true;
                break;
            }
        }

        if (!found)
            throw new Exception("Could not initialize default trust manager");

        if (trustedCAs != null) {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustedCAs);
            tms = tmf.getTrustManagers();
            myTrustManager = (X509TrustManager) tms[0];
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        defaultTrustManager.checkClientTrusted(chain ,authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            defaultTrustManager.checkServerTrusted(chain ,authType);
        } catch(CertificateException e) {

            if (myTrustManager != null) {
                try {
                    myTrustManager.checkServerTrusted(chain, authType);
                    return;
                } catch(CertificateException e2) {
                    e = e2;
                }
            }

            /*
            try {
                for (int i = 0; i < chain.length; i++) {
                    // Log.d(TAG, "self signed: " + isSelfSigned(chain[i]));
                    Log.d(TAG, "Server certificate " + (i + 1) + ":");
                    Log.d(TAG, "Subject DN: " + chain[i].getSubjectX500Principal());
                    Log.d(TAG, "Issuer DN: " + chain[i].getIssuerX500Principal());
                    Log.d(TAG, "Signature Algorithm: " + chain[i].getSigAlgName());
                    Log.d(TAG, "Valid from: " + chain[i].getNotBefore());
                    Log.d(TAG, "Valid until: " + chain[i].getNotAfter());
                    Log.d(TAG, "Serial #: " + chain[i].getSerialNumber().toString(16));

                    // Log.d(TAG, getUniqueKey(chain[i])); //TODO: remove
                    // chain[i].checkValidity();

                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            */

            Log.d(TAG, "checkServerTrusted", e);

            boolean ok = false;
            boolean selfSigned = false;
            X509Certificate cert = chain[0];
            CertificateException ex = null;
            int reason = 0;

            /* already accepted? */
            String key = getUniqueKey(cert);

            if (mCertMap.containsKey(key)) {
                TrustedCert cachedCert = mCertMap.get(key);
                if (!cachedCert.allow) {
                    // marked as denied
                    throw e;
                }

                if (cachedCert != null && cachedCert.expires != null && cachedCert.expires.after(new Date())) {
                    ok = cert.equals(cachedCert.cert);
                    if (ok) {
                        return;
                    }
                }
            }

            /* self signed? */
            try {
                selfSigned = isSelfSigned(cert);

                if (selfSigned) {
                    reason |= SELF_SIGNED;
                }

            } catch(Exception e2) {
                Log.d(TAG, "self signed test failed: " , e2);
            }

            /* expired? */
            try {
                cert.checkValidity();
            } catch(CertificateExpiredException | CertificateNotYetValidException e2) {
                ex = e2;
                reason |= EXPIRED;
            }

            /* cert path: missing ta, ... */
            Throwable cause = e.getCause();
            if (!selfSigned && cause instanceof CertPathValidatorException) {
                CertPathValidatorException cpe = (CertPathValidatorException) cause;
                reason |= INVALID_CERT_PATH;
                Log.d(TAG, "cpe:" + cpe.toString());
            }

            if (reason == 0) {
                reason |= OTHER;
            }

            CertException pe = new CertException(e, reason, chain);
            throw pe;
        }
    }

    public static boolean isDenied(X509Certificate cert) {
        boolean denied = false;
        if (cert != null) {
            String key = getUniqueKey(cert);
            if (key != null) {
                TrustedCert c = mCertMap.get(key);
                if (c != null && !c.allow) {
                    denied = true;
                }
            }
        }
        return denied;
    };

    public static void addCertificate(X509Certificate cert, Date expires, boolean allow) {
        if (cert != null) {
            TrustedCert tc = new TrustedCert();
            if (expires == null) {
                tc.expires = new Date(System.currentTimeMillis() + CERT_EXPIRES_AFTER_MS);
            }
            tc.allow = allow;
            tc.cert = cert;

            mCertMap.put(getUniqueKey(cert), tc);
        }
    }

    public static boolean isValidException(X509Certificate cert) {
        boolean added = false;
        if (cert != null) {
            TrustedCert t = mCertMap.get(getUniqueKey(cert));
            if (t != null && t.allow) {
                added = t.expires.after(new Date());
            }
        }
        return added;
    }

    public static void saveTrustedCerts(Context context) throws JSONException, CertificateEncodingException {
        Date now = new Date();
        JSONArray jsonCerts = new JSONArray();

        for(Iterator<Map.Entry<String, TrustedCert>> it = mCertMap.entrySet().iterator(); it.hasNext(); ) {
            TrustedCert e = it.next().getValue();
            if (e.allow && e.expires != null && e.expires.after(now) && e.cert != null) {
                JSONObject cert = new JSONObject();
                cert.put("expires", e.expires.getTime());
                cert.put("certificate", Base64.encodeToString(e.cert.getEncoded(), Base64.NO_WRAP));
                jsonCerts.put(cert);
            }
        }

        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

        /* save message info */
        SharedPreferences.Editor editor = settings.edit();
        // Log.d(TAG, jsonCerts.toString());
        editor.putString(PREFS_EXCEPTIONS, jsonCerts.toString());
        editor.commit();

    }

    public static void readTrustedCerts(Context context) throws JSONException {
        Date now = new Date();

        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        String certs = settings.getString(PREFS_EXCEPTIONS, null);
        if (certs != null) {
            JSONArray jsonCerts = new JSONArray(certs);
            for(int i = 0; i < jsonCerts.length(); i++) {
                JSONObject cert = jsonCerts.getJSONObject(i);

                try {
                    Date expires = new Date(cert.getLong("expires"));
                    if (expires.after(now)) {
                        String b64 = cert.getString("certificate");
                        byte[] decoded = Base64.decode(b64, Base64.NO_WRAP);
                        ByteArrayInputStream bis = new ByteArrayInputStream(decoded);
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        X509Certificate xcert = (X509Certificate) cf.generateCertificate(bis);
                        TrustedCert trustedCert = new TrustedCert();
                        trustedCert.allow = true;
                        trustedCert.expires = expires;
                        trustedCert.cert = xcert;
                        mCertMap.put(getUniqueKey(xcert), trustedCert);

                    }
                } catch(Exception e) {
                    Log.d(TAG, "Error reading certificate: ", e);
                }

            }
        }

        if (Build.VERSION.SDK_INT <= 23) {
            try {
                InputStream ins = context.getResources().openRawResource(
                        context.getResources().getIdentifier("radioshuttle_ca",
                                "raw", context.getPackageName()));
                if (ins != null) {
                    Log.d(TAG, "found ca");
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    X509Certificate xcert = (X509Certificate) cf.generateCertificate(ins);
                    if (xcert != null) {

                        // Create a KeyStore containing our trusted CAs
                        String keyStoreType = KeyStore.getDefaultType();
                        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                        keyStore.load(null, null);
                        keyStore.setCertificateEntry("ca", xcert);
                        trustedCAs = keyStore;
                    }

                }

            } catch(Exception e) {
                Log.d(TAG, "Error reading Radioshuttle CA", e);
            }
        }



    }


    public static void removeCertificateFromRequest(X509Certificate cert) {
        if (cert != null) {
            mRequestMap.remove(getUniqueKey(cert));
        }
    }

    /*
    public static X509Certificate[] getCertFromRequestQueue(String key) {
        X509Certificate chain[];
        if (key != null)
            chain = mRequestMap.get(key);
        else
            chain = null;
        return chain;
    }
    */

    /**
     * Checks whether given X.509 certificate is self-signed.
     */
    public static boolean isSelfSigned(X509Certificate cert) throws CertificateException,
            NoSuchAlgorithmException, NoSuchProviderException {
        try {
            // Try to verify certificate signature with its own public key
            PublicKey key = cert.getPublicKey();
            cert.verify(key);
            return true;
        } catch (SignatureException sigEx) {
            // Invalid signature --> not self-signed
            return false;
        } catch (InvalidKeyException keyEx) {
            // Invalid key --> not self-signed
            return false;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }

    public static String getUniqueKey(X509Certificate cert) {
        String key;
        if (cert != null) {
            String name = cert.getIssuerX500Principal().getName();
            String serial = cert.getSerialNumber().toString();
            key = name + "_" + serial;
        } else
            key = "";
        return key;
    }

    private static final ConcurrentHashMap<String, TrustedCert> mCertMap = new ConcurrentHashMap();
    public static final ConcurrentHashMap<String, CertException> mRequestMap = new ConcurrentHashMap();

    public static class TrustedCert {
        public Date expires;
        public boolean allow;
        public X509Certificate cert;
    }

    /* codes used in PcCertificateExeption, which allows more detailed error handling afger hanshakre*/
    public static final int EXPIRED = 1;
    public static final int SELF_SIGNED = 2;
    public static final int INVALID_CERT_PATH = 4;
    public static final int HOST_NOT_MATCHING = 8;
    public static final int OTHER = 256;

    public final static long  CERT_EXPIRES_AFTER_MS = 24L * 1000l * 60l * 60l;

    public final static String PREFS_NAME = "certificates";
    public final static String PREFS_EXCEPTIONS = "exceptions";

    private static KeyStore trustedCAs = null;

    private final static String TAG = AppTrustManager.class.getSimpleName();
}
