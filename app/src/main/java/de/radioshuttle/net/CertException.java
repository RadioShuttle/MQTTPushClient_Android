/*
 * $Id$
 * This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.net;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class CertException extends CertificateException {

    public CertException(Throwable orgCause, int reason, X509Certificate[] chain) {
        super(orgCause);
        this.reason = reason;
        this.chain = chain;
    }

    public X509Certificate[] chain;
    public int reason;
}
