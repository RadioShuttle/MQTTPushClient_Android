/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
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
