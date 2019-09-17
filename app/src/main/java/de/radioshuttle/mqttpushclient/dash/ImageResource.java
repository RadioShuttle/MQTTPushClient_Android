/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract;
import android.util.Log;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Comparator;

import de.radioshuttle.utils.Utils;

public final class ImageResource {
    public Drawable drawable;
    /* resource id for internal images */
    public int id;
    public String uri;
    public String label;


    public static class LabelComparator implements Comparator<ImageResource> {

        @Override
        public int compare(ImageResource r1, ImageResource r2) {
            String s1, s2;
            if (r1 != null && r1.label != null) {
                s1 = r1.label;
            } else {
                s1 = "";
            }
            if (r2 != null && r2.label != null) {
                s2 = r2.label;
            } else {
                s2 = "";
            }
            return s1.compareToIgnoreCase(s2);
        }
    }

    public static class IDComparator implements Comparator<ImageResource> {

        @Override
        public int compare(ImageResource r1, ImageResource r2) {
            int i1, i2, r = 0;
            if (r1 != null) {
                i1 = r1.id;
            } else {
                i1 = Integer.MIN_VALUE;
            }
            if (r2 != null) {
                i2 = r2.id;
            } else {
                i2 = Integer.MIN_VALUE;
            }
            if (i1 < i2) {
                r = -1;
            } else if (i1 > i2) {
                r = 1;
            }
            return r;
        }
    }


    public static boolean isInternalResource(String uri) {
        return !Utils.isEmpty(uri) && IconHelper.INTENRAL_ICONS.containsKey(uri);
    }

    public static boolean isImportedResource(String uri) {
        return !Utils.isEmpty(uri) && uri.toLowerCase().startsWith("res://imported/");
    }

    public static boolean isExternalResource(String uri) {
        return (!Utils.isEmpty(uri) && uri.toLowerCase().startsWith("res://user/")) || isImportedResource(uri);
    }

    public static BitmapDrawable loadExternalImage(Context context, String uri) throws URISyntaxException, UnsupportedEncodingException {
        URI u;
        u = new URI(uri);
        File dir = null;
        if (u.getAuthority().equals("imported")) {
            dir = ImportFiles.getImportedFilesDir(context);
        } else if (u.getAuthority().equals("user")) {
            //TODO
            throw new RuntimeException("Not supported yet");
        }
        Bitmap bm = BitmapFactory.decodeFile(dir.getAbsolutePath() + Utils.urlDecode(u.getPath()));
        BitmapDrawable bd = null;
        if (bm != null) {
            bd = new BitmapDrawable(context.getResources(), bm);
        }
        return bd;
    }

    private final static String TAG = ImageResource.class.getSimpleName();
}
