/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.mqttpushclient.dash;

import java.util.Comparator;

import de.radioshuttle.db.MqttMessage;

public class Message extends MqttMessage {
    public int status;

    public static class AscComparator implements Comparator<Message> {
        @Override
        public int compare(Message o1, Message o2) {
            int cmp = 0;
            long w1 = (o1 == null ? 0L : o1.getWhen());
            long w2 = (o2 == null ? 0L : o2.getWhen());
            int s1 = (o1 == null ? 0 : o1.getSeqno());
            int s2 = (o2 == null ? 0 : o2.getSeqno());

            if (w1 < w2) {
                cmp = -1;
            } else if (w1 > w2) {
                cmp = 1;
            } else if (s1 < s2) {
                cmp = -1;
            } else if (s1 > s2) {
                cmp = 1;
            }
            return cmp;
        }
    }
}
