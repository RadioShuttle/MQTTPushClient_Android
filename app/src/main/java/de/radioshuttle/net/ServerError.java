/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.net;

public class ServerError extends Exception {
    public ServerError(int code, String msg) {
        super(msg);
        errorCode = code;
    }

    public int errorCode;
}
