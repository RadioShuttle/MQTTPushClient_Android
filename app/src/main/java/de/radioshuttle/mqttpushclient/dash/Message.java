/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import de.radioshuttle.db.MqttMessage;

public class Message extends MqttMessage {
    public int status;
    public String filter;
}
