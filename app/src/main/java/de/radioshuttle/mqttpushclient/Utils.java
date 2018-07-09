/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

public class Utils {
    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }

    public static boolean equals(String a, String b) {
        return equals((Object) a, b) || (isEmpty(a) && isEmpty(b));
    }

}
