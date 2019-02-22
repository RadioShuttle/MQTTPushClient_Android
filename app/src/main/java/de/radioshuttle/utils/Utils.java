/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.utils;

import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.UUID;
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

    public static byte[] randomUUID() {
        return randomBytes(16);
    }

    public static byte[] randomBytes(int size) {
        byte[] rand = new byte[size];
        Holder.numberGenerator.nextBytes(rand);
        return rand;
    }

    public static String byteArrayToHex(byte[] buf) {
        StringBuffer ret = new StringBuffer(buf.length << 1);
        for(byte b : buf) {
            ret.append(Character.forDigit((b >>> 4) & 0xF, 16));
            ret.append(Character.forDigit(b & 0xF, 16));
        }
        return ret.toString();
    }

    public static byte[] hexToByteArray(String hex) {
        if (hex == null)
            return new byte[0];
        int c = hex.length();
        byte[] ret = new byte[(c + 1) >>> 1];
        for (int i = ret.length; i-- > 0; ) {
            int left = (--c > 0)? Character.digit(hex.charAt(c - 1), 0x10) : 0;
            ret[i] = (byte) ((left << 4) + Character.digit(hex.charAt(c--), 16));
        }
        return ret;
    }

    public static final Charset UTF_8 = Charset.forName("UTF-8");


    private static class Holder {
        static final SecureRandom numberGenerator = new SecureRandom();
    }

}
