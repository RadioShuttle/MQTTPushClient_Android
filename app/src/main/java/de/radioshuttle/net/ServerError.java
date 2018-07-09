/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

public class ServerError extends Exception {
    public ServerError(int code, String msg) {
        super(msg);
        errorCode = code;
    }

    public int errorCode;
}
