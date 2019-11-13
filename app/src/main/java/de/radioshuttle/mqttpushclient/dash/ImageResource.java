/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.webkit.WebResourceResponse;

import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.net.Cmd;
import de.radioshuttle.utils.HeliosUTF8Decoder;
import de.radioshuttle.utils.HeliosUTF8Encoder;
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

    public static boolean isUserResource(String uri) {
        return !Utils.isEmpty(uri) && uri.toLowerCase().startsWith("res://user/");
    }

    public static String buildUserResourceURI(String resourceName){
        if (resourceName != null) {
            try {
                resourceName = "res://user/" + Utils.urlEncode(resourceName);
            } catch(UnsupportedEncodingException e) {
            }
        }
        return resourceName;
    }

    public static String getURIPath(String uri) {
        String uriPath = null;
        URI u = null;
        try {
            if (uri != null) {
                u = new URI(uri);
                uriPath = u.getPath();
                if (uriPath.startsWith("/")) {
                    uriPath = uriPath.substring(1);
                }
            }
        } catch(Exception e) {}
        return uriPath;
    }

    public static String getLabel(String uri) {
        String label = null;
        URI u = null;
        try {
            if (uri != null) {
                u = new URI(uri);
                String a = u.getAuthority();
                if ("imported".equals(a)) {
                    label = getURIPath(uri);
                    label = removeImportedFilePrefix(label);
                    label = removeExtension(label);
                    label = "tmp/" + label;

                } else {
                    label = getURIPath(uri);
                }
            }
        } catch(Exception e) {}
        return label;
    }

    public static String decodeFilename(String filename) {
        if (filename != null) {
            HeliosUTF8Decoder dec = new HeliosUTF8Decoder();
            filename = dec.format(filename);
        }
        return filename;
    }

    public static String encodeFilename(String filename) {
        if (filename != null) {
            HeliosUTF8Encoder enc = new HeliosUTF8Encoder();
            filename = enc.format(filename);
        }
        return filename;
    }

    public static String removeExtension(String file) {
        if (file != null) {
            int idx = file.lastIndexOf('.');
            if (idx != -1 && idx > 0) {
                file = file.substring(0, idx);
            }
        }
        return file;
    }

    public static String removeImportedFilePrefix(String file) {
        if (file != null) {
            int idx = file.indexOf('_');
            if (idx != -1) {
                file = file.substring(idx + 1);
            }
        }
        return file;
    }

    public static boolean isExternalResource(String uri) {
        return isUserResource(uri) || isImportedResource(uri);
    }

    public static WebResourceResponse handleWebResource(Context context, Uri u) {
        WebResourceResponse r = null;
        try {
            if (u != null && CustomItem.BASE_URI.getAuthority().equalsIgnoreCase(u.getAuthority())) {
                String path = u.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                // Log.d(TAG, "uri: " + u);

                InputStream is = null;
                String res = ImageResource.getResourceURI(context, path);
                String mimeType = "image/png"; // default
                if (res != null) {
                    if (ImageResource.isUserResource(res)) {
                        String filePath = getImageFilePath(context, res);
                        File f = new File(filePath);
                        is = new FileInputStream(f);
                        // mimeType = "image/png";
                    } else {
                        if (ImageResource.isInternalResource(res)) {
                            /* return corresponding svg file (instead of creating a png out of vector drawable) */
                            AssetManager am = context.getResources().getAssets();
                            String resFileName = new URI(res).getPath().substring(1) + ".svg";
                            is = am.open("svg/" + resFileName);
                            Log.d(TAG, resFileName);
                            mimeType = "image/svg+xml";

                            /*
                            VectorDrawableCompat d = VectorDrawableCompat.create(
                                    context.getResources(), IconHelper.INTENRAL_ICONS.get(res), null);

                            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                            Canvas canvas = new Canvas(bitmap);
                            d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                            d.draw(canvas);

                            ByteArrayOutputStream bao = new ByteArrayOutputStream();
                            boolean ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, bao);
                            Log.d(TAG, " res " + ok + ", size: " + bao);
                            is = new ByteArrayInputStream(bao.toByteArray());
                             */
                        }
                    }
                }
                r = new WebResourceResponse(
                        mimeType,
                        null,
                        is == null ? new ByteArrayInputStream(new byte[0]) : is);

                if (Build.VERSION.SDK_INT >= 21 && is == null) {
                    r.setStatusCodeAndReasonPhrase(404, "Resource not found");
                }

            }
        } catch(Exception e) {
            Log.d(TAG, "Error handling web resource; ", e);
        }
        return r;
    }

    public static String getResourceURI(Context context, String resourceName) {
        String uri = null;
        try {
            boolean checkUserRes;
            if (resourceName.toLowerCase().startsWith("int/")) {
                resourceName = resourceName.substring(4);
                checkUserRes = false;
            } else {
                if (resourceName.toLowerCase().startsWith("user/")) {
                    resourceName = resourceName.substring(5);
                }
                checkUserRes = true;
            }
            if (checkUserRes) {
                File userImagesDir = ImportFiles.getUserFilesDir(context);
                String[] userFiles = userImagesDir.list();
                if (userFiles.length > 0) {
                    HeliosUTF8Decoder dec = new HeliosUTF8Decoder();
                    for(String u : userFiles) {
                        u = ImageResource.removeExtension(u);
                        u = dec.format(u);
                        if (resourceName.equals(u)) {
                            uri = ImageResource.buildUserResourceURI(u);
                            break;
                        }
                    }
                }
            }
            if (Utils.isEmpty(uri)) { // not found, check if internal
                String key = "res://internal/" + resourceName;
                if (IconHelper.INTENRAL_ICONS.containsKey(key)) {
                    uri = key;
                }
            }
        } catch(Exception e) {
            Log.d(TAG, "Error checking resource name passed by script: " + resourceName); //TODO
        }
        return uri;
    }

    public static BitmapDrawable loadExternalImage(Context context, String uri) throws URISyntaxException, UnsupportedEncodingException {
        String path = getImageFilePath(context, uri);

        Bitmap bm = BitmapFactory.decodeFile(path);
        BitmapDrawable bd = null;
        if (bm != null) {
            bd = new BitmapDrawable(context.getResources(), bm);
        }
        return bd;
    }

    protected static String getImageFilePath(Context context, String uri) {
        File dir = null;
        String name = null;
        if (isImportedResource(uri)) {
            dir = ImportFiles.getImportedFilesDir(context);
            name = getURIPath(uri);
        } else if (isUserResource(uri)) {
            dir = ImportFiles.getUserFilesDir(context);
            name = getURIPath(uri) + '.' + Cmd.DASH512_PNG;
        }
        String path = dir.getAbsolutePath();
        if (!path.endsWith("/")) {
            path += "/";
        }
        path += name;
        return path;
    }

    /** removes all image resources which are not referenced */
    public static void removeUnreferencedImageResources(Context context, List<PushAccount> accounts) {
        try {
            if (context == null) {
                return;
            }
            /* delete all file in imported files dir */
            ImportFiles.deleteImportedFilesDir(context);

            /* delete all files in user dir which are unreferenced (not included in any dashbard) */
            if (accounts != null) {
                final HashSet<String> referencedFiles = new HashSet<>();
                ViewState vs = ViewState.getInstance(context);
                for(PushAccount p : accounts) {
                    String jsonDash = vs.getDashBoardContent(p.getKey());
                    if (!Utils.isEmpty(jsonDash)) {
                        JSONObject jo = new JSONObject(jsonDash);

                        JSONArray groupArray = jo.getJSONArray("groups");
                        JSONObject groupJSON, itemJSON;
                        String uri, resourceName, internalFileName;
                        HeliosUTF8Encoder enc = new HeliosUTF8Encoder();
                        for (int i = 0; i < groupArray.length(); i++) {
                            groupJSON = groupArray.getJSONObject(i);
                            JSONArray itemArray = groupJSON.getJSONArray("items");

                            for (int j = 0; j < itemArray.length(); j++) {
                                try {
                                    itemJSON = itemArray.getJSONObject(j);
                                    uri = itemJSON.optString("uri");
                                    if (ImageResource.isUserResource(uri)) {
                                        resourceName = ImageResource.getURIPath(uri);
                                        internalFileName = enc.format(resourceName) + '.' + Cmd.DASH512_PNG;
                                        referencedFiles.add(internalFileName);
                                    }
                                    uri = itemJSON.optString("uri_off");
                                    if (ImageResource.isUserResource(uri)) {
                                        resourceName = ImageResource.getURIPath(uri);
                                        internalFileName = enc.format(resourceName) + '.' + Cmd.DASH512_PNG;
                                        referencedFiles.add(internalFileName);
                                    }
                                } catch(Exception e) {
                                    Log.d(TAG, "removeUnreferencedImageResources: error checking resource names: ", e);
                                }
                            }
                        }
                    }
                }
                File dir = ImportFiles.getUserFilesDir(context);
                if (dir.isDirectory() && dir.exists()) {
                    File[] unreferencedFiles = dir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File file, String name) {
                            return !referencedFiles.contains(name);
                        }
                    });
                    for(File f : unreferencedFiles) {
                        Log.d(TAG, "removing unreferenced file: " + f.getName());
                        f.delete();
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "removeUnreferencedImageResources error: ", e);
        }
    }


    private final static String TAG = ImageResource.class.getSimpleName();
}
