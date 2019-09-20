/*
 * $Id$
 * This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen, Germany
 */

package de.radioshuttle.utils;

import java.text.Format;

public class HeliosUTF8Encoder extends Format {

    /* HELIOS UTF-8 special chars */
    private static final String HELIOS_SPECIAL_CHARS = "/\\^*?<>|:\"";
    private static final String[] HELIOS_REPLACE_CHARS = { "^2f", "^5c", "^5e", "^2a", "^3f", "^3c", "^3e", "^7c", "^3a", "^22" };

    public StringBuffer format(Object obj, StringBuffer toAppendTo, java.text.FieldPosition pos) {
        if (obj != null)
            return toAppendTo.append(encodeHeliosUTF8(obj.toString()));
        return toAppendTo;
    }

    public Object parseObject(String source, java.text.ParsePosition pos) {
        return encodeHeliosUTF8(source.substring(pos.getIndex()));
    }

    public java.text.AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        return null;
    }

    private static String encodeHeliosUTF8(String fname) {
        if (fname == null)
            return null;
        StringBuffer b = new StringBuffer(fname);
        int pos, l = b.length();
        for (int i = 0; i < l; i++) {
            if ((pos = HELIOS_SPECIAL_CHARS.indexOf(b.charAt(i))) > -1) {
                b.replace(i++, i++, HELIOS_REPLACE_CHARS[pos]);
                l += 2;
            }
        }
        return b.toString();
    }
}