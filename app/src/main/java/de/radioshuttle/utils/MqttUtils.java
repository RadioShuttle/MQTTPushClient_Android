
/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.utils;

import java.util.ArrayList;

public class MqttUtils {
    
    public static void topicValidate(String topic, boolean wildcardAllowed) throws IllegalArgumentException {
        int len = 0;
        try {
            len = topic.getBytes("UTF-8").length;
        } catch(Exception e) {
            throw new IllegalArgumentException("Invalid topic length");
        }
        if (len < 1 || len > 65535) {
            throw new IllegalArgumentException("Invalid topic length");
        }

        if (!wildcardAllowed) {
            if (topic.indexOf('+') != -1 || topic.indexOf('#') != -1) {
                throw new IllegalArgumentException("Wildcards are not allowed");
            }
        } else {
            if (topic.equals("#") || topic.equals("+")) {
                ;
            } else {
                int idx = topic.indexOf('#');
                if (idx != -1) {
                    int idx2 = topic.lastIndexOf('#');
                    if (idx2 != -1) {
                        if (idx2 != idx) {
                            /* only one occurrence allowed */
                            throw new IllegalArgumentException("Only one wildcard # allowed");
                        }
                    }
                    if (!topic.endsWith("/#")) {
                        throw new IllegalArgumentException("Invalid usage of wildcard #");
                    }
                }

                for(int i = 0; i < topic.length(); i++) {
                    if (topic.charAt(i) == '+') {
                        if (i > 0) {
                            if (topic.charAt(i - 1) != '/') {
                                throw new IllegalArgumentException("Invalid usage of wildcard +");
                            }
                        }
                        if (i + 1 < topic.length()) {
                            if (topic.charAt(i + 1) != '/') {
                                throw new IllegalArgumentException("Invalid usage of wildcard +");
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean topicIsMatched(String filter, String topic) throws IllegalArgumentException {
        topicValidate(filter, true);
        topicValidate(topic, false);

        ArrayList<String> filterNodes = new ArrayList<>();
        int i, pos = 0;
        for(i = 0; i < filter.length(); i++) {
            if (filter.charAt(i) == '/') {
                filterNodes.add(filter.substring(pos, i));
                pos = i + 1;
            }
        }
        filterNodes.add(filter.substring(pos, i));

        ArrayList<String> topicNodes = new ArrayList<>();
        pos = 0;
        for(i = 0; i < topic.length(); i++) {
            if (topic.charAt(i) == '/') {
                topicNodes.add(topic.substring(pos, i));
                pos = i + 1;
            }
        }
        topicNodes.add(topic.substring(pos, i));

        int j = 0;
        for(i = 0; i < topicNodes.size() && j < filterNodes.size(); i++, j++) {
            if (filterNodes.get(i).equals("#")) {
                return true;
            } else if (filterNodes.get(i).equals("+")) {
                continue;
            } else if (!topicNodes.get(i).equals(filterNodes.get(j))) {
                return false;
            }
        }
        return (i == topicNodes.size() && j == filterNodes.size());
    }

}
