/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.utils;

import android.content.Context;
import android.content.res.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Utils {
    
    public static ExecutorService executor = Executors.newCachedThreadPool();

    public static ThreadPoolExecutor newSingleThreadPool() {
        return new ThreadPoolExecutor(1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
    }

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

    public static String getRawStringResource(Context context, String filenameWithoutExt, boolean skipNewLine) throws IOException {
        InputStream ins = context.getResources().openRawResource(
                context.getResources().getIdentifier(filenameWithoutExt,
                        "raw", context.getPackageName()));
        StringWriter sw = new StringWriter();
        BufferedReader reader = new BufferedReader(new InputStreamReader(ins));
        try {
            String line = null;
            while ((line = reader.readLine()) != null) {
                sw.append(line);
                if (!skipNewLine) {
                    sw.append("\n");
                }
            }
        } finally {
            if (reader != null) {
                try {reader.close();} catch(IOException io) {}
            }
        }
        return sw.toString();
    }

    public static boolean isNightMode(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        int currentNightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static String urlEncode(String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    public static String urlDecode(String s)  throws UnsupportedEncodingException {
        return URLDecoder.decode(s, "UTF-8");
    }

    public static final Charset UTF_8 = Charset.forName("UTF-8");


    private static class Holder {
        static final SecureRandom numberGenerator = new SecureRandom();
    }

}
