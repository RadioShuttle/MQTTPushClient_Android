package de.radioshuttle.net;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLHandshakeException;

public class HostVerificationError extends SSLHandshakeException {

    public HostVerificationError(String reason, X509Certificate[] chain) {
        super(reason);
        this.chain = chain;
    }

    public X509Certificate[] chain;
}
