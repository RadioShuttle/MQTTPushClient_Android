/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

public class MQTTException extends ServerError {

    public MQTTException(int code, String msg) {
        super(code, msg);
    }

    public static final short REASON_CODE_SUBSCRIBE_FAILED				= 0x80;

    public MQTTException(int code, String msg, int loginAccountInfo) {
        super(code, msg);
        accountInfo = loginAccountInfo;
    }

    /** 1 = MQTT error performing login, but accout data matches local stored credntials */
    public int accountInfo; // set if MQTT exception occured on server while login
}
