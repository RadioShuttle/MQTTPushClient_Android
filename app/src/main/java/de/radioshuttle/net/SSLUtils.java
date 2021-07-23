/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;


import java.security.SecureRandom;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class SSLUtils {

    public static SSLSocketFactory getPushServerSSLSocketFactory() throws Exception {
        if (pushServerSocketFactory == null) {
            synchronized (lock) {
                if (pushServerSocketFactory == null) {
                    pushServerSocketFactory = createSslSocketFactory();
                }
            }
        }
        return pushServerSocketFactory;
    }

    public static HostnameVerifier getPushServeHostVerifier() {
        // return HttpsURLConnection.getDefaultHostnameVerifier(); //TODO: see fix for document hub
        return OkHostnameVerifier.INSTANCE;
    }

    private static SSLSocketFactory createSslSocketFactory() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {new AppTrustManager()}, new SecureRandom());
        return sslContext.getSocketFactory();
    }

    private static SSLSocketFactory pushServerSocketFactory = null;
    private static Object lock = new Object();
}
