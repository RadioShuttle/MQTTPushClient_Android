/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.utils;

import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Utils {
    
    public static ExecutorService executor = Executors.newCachedThreadPool();

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static boolean equals(String a, String b) {
        return equals((Object) a, b) || (isEmpty(a) && isEmpty(b));
    }

    public static final Charset UTF_8 = Charset.forName("UTF-8");

}
