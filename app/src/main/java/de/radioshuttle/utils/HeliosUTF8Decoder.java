/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.utils;

import java.text.Format;

public class HeliosUTF8Decoder extends Format {

    /* HELIOS UTF-8 special chars */
    private static final String HELIOS_UTF8_FIRST = "2357";
    private static final String[] HELIOS_UTF8_SECOND = {"2af", "acef", "ce", "c" };
    private static final String[] HELIOS_UTF8_REPLACE = {"\"*/", ":<>?", "\\^", "|" };


    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, java.text.FieldPosition pos) {
        if (obj != null)
            return toAppendTo.append(decodeHeliosUTF8(obj.toString()));
        return toAppendTo;
    }

    @Override
    public Object parseObject(String source, java.text.ParsePosition pos) {
        return decodeHeliosUTF8(source.substring(pos.getIndex()));
    }

    @Override
    public java.text.AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        return null;
    }

    private static String decodeHeliosUTF8(String fname) {
        int l = fname.length();
        if (l < 3)
            return fname;

        int i = fname.indexOf('^');
        if (i < 0)
            return fname;

        StringBuffer b = new StringBuffer(l);
        b.append(fname.substring(0, i));
        int pos, cpos;
        while (l - i >= 3) {
            switch (fname.charAt(i)) {
                case '^':
                    if ( (pos = HELIOS_UTF8_FIRST.indexOf(fname.charAt(++i))) > -1
                            && (cpos = HELIOS_UTF8_SECOND[pos].indexOf(fname.charAt(i + 1))) > -1) {
                        b.append(HELIOS_UTF8_REPLACE[pos].charAt(cpos));
                        i += 2;
                        continue;
                    } else
                        b.append(fname.charAt(i - 1));
                    break;
                default:
                    b.append(fname.charAt(i++));
                    break;
            }
        }
        return b.append(fname.substring(i)).toString();
    }
}