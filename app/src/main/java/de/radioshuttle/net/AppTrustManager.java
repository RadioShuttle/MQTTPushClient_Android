/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.net;

import android.util.Log;

import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class AppTrustManager implements X509TrustManager {

    private X509TrustManager defaultTrustManager;

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
            // if (true) return; //TODO: remove

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

            /* cert path: missing ta, ... */
            Throwable cause = e.getCause();
            if (!selfSigned && cause instanceof CertPathValidatorException) {
                CertPathValidatorException cpe = (CertPathValidatorException) cause;
                reason |= INVALID_CERT_PATH;
                Log.d(TAG, "cpe:" + cpe.toString());
            }

            /* expired? */
            try {
                cert.checkValidity();
            } catch(CertificateExpiredException | CertificateNotYetValidException e2) {
                ex = e2;
                reason |= EXPIRED;
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

    private final static String TAG = AppTrustManager.class.getSimpleName();
}
