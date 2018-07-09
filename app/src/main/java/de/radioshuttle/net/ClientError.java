/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

public class ClientError extends Exception {
    public ClientError(String msg) {
        super(msg);
    }

    public ClientError(Throwable t) {
        super(t);

    }

}
