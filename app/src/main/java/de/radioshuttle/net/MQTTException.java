/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

public class MQTTException extends ServerError {

    public MQTTException(int code, String msg) {
        super(code, msg);
    }

    public MQTTException(int code, String msg, int loginAccountInfo) {
        super(code, msg);
        accountInfo = loginAccountInfo;
    }

    /** 1 = MQTT error performing login, but accout data matches local stored credntials */
    public int accountInfo; // set if MQTT exception occured on server while login

    public static final short REASON_CODE_SUBSCRIBE_FAILED				= 0x80;

}
