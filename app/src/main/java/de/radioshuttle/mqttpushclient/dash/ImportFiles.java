/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.DragAndDropPermissions;
import android.webkit.MimeTypeMap;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class ImportFiles implements Runnable {

    public ImportFiles(Context context, Uri file) {
        mAppContext = context.getApplicationContext();
        mFileUris = new ArrayList<>();
        if (file != null)
            mFileUris.add(file);
        mClipData = null;
    }

    public ImportFiles(Context context, ArrayList<Uri> files) {
        mAppContext = context.getApplicationContext();
        mFileUris = files;
        mClipData = null;
    }

    @TargetApi(24)
    public ImportFiles(Context context, ArrayList<Uri> files, DragAndDropPermissions permissions) {
        this(context, files);
        mDragAndDropPermissions = permissions;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    public ImportFiles(Context context, ClipData clipData) {
        mAppContext = context.getApplicationContext();
        mClipData = clipData;
        mFileUris = null;
    }

    @Override
    public void run() {
        // android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        JSONArray fileArray = new JSONArray();

        if (mClipData != null && mFileUris == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                if (mClipData instanceof ClipData) {
                    ClipData clipData = (ClipData) mClipData;
                    mFileUris = new ArrayList<>();
                    for(int i=0; i<((ClipData) mClipData).getItemCount(); i++) {
                        Uri u = clipData.getItemAt(i).getUri();
                        if (u != null) {
                            mFileUris.add(u);
                        }
                    }
                }
            }
        }

        if (mFileUris != null) {
            ContentResolver contentResolver = mAppContext.getContentResolver();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();

            int maxID = 0;
            File importedFilesDir = getImportedFilesDir();
            if (!importedFilesDir.exists()) {
                importedFilesDir.mkdirs();
            } else {
                String[] files = importedFilesDir.list();
                int p, idx;
                if (files != null) {
                    /* get max prefix */
                    for(int i = 0; i < files.length; i++) {
                        try {
                            idx = files[i].indexOf('_');
                            if (idx != -1) {
                                p = Integer.valueOf(files[i].substring(0, idx));
                                if (p > maxID) {
                                    maxID = p;
                                }
                            }
                        } catch(Exception e) {
                            Log.d(TAG, "error parsing file: " + files[i]);
                        }
                    }
                }
            }

            uriLoop:
            for (Uri fileUri : mFileUris) {
                if (fileUri == null)
                    continue;
                JSONObject fileInfo = new JSONObject();
                fileArray.put(fileInfo);
                int status = 0;

                Cursor c = null;
                String name = null;
                boolean isFileScheme = false;
                long filesize;
                try {

                    if (hasContentScheme(fileUri)) {
                        String auth = fileUri.getAuthority();
                        String[] projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
                        c = contentResolver.query(fileUri, projection, null, null, null);
                    } else {
                        isFileScheme = hasFileScheme(fileUri);
                    }
                    if ((c != null && c.moveToNext()) || isFileScheme) {
                        if (isFileScheme) {
                            File f = new File(fileUri.getPath());
                            filesize = f.length();
                            name = fileUri.getLastPathSegment();
                            Log.d(TAG, "file:// " + name + " " + filesize);
                        } else {
                            if (name == null && c.getColumnIndex(OpenableColumns.DISPLAY_NAME) != -1) {
                                name = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                            }
                            if (c.getColumnIndex(OpenableColumns.SIZE) != -1)
                                filesize = c.getLong(c.getColumnIndex(OpenableColumns.SIZE));
                            else
                                filesize = -1l;

                            if (name == null)
                                name = fileUri.getLastPathSegment();

                            if (name == null || name.trim().length() == 0) {// still null?
                                name = "unknown";
                                String mime = contentResolver.getType(fileUri);
                                if (mime != null) {
                                    String ext = mimeTypeMap.getExtensionFromMimeType(mime);
                                    if (ext != null)
                                        name += "." + ext;
                                }
                            }
                        }
                        /* a document provider may have retruned an invalid file name */
                        name = replaceInvalidChars(name);
                        fileInfo.put("name", name);

                        // filesize
                        boolean fileSizeKnown = filesize > -1L;

                        if (fileSizeKnown && lowInternalMemory(filesize)) {
                            throw new ImportException(STATUS_LOWMEM_ERROR);
                        }


                        maxID++;
                        String tmpFileName = TMP_PREFIX + maxID + "_" + System.currentTimeMillis();
                        File tmpFile = new File(getTempFileDir(), tmpFileName);
                        BufferedOutputStream bo = null;
                        InputStream is = null;
                        boolean complete = false;
                        byte[] buff = new byte[BUFFER_SIZE];
                        try {
                            bo = new BufferedOutputStream(new FileOutputStream(tmpFile));
                            is = contentResolver.openInputStream(fileUri);
                            int bytesRead;
                            while ((bytesRead = is.read(buff)) != -1) {
                                if (!fileSizeKnown && lowInternalMemory(bytesRead)) {
                                    if (tmpFile != null && tmpFile.exists())
                                        tmpFile.delete();
                                    throw new ImportException(STATUS_LOWMEM_ERROR);
                                }
                                bo.write(buff, 0, bytesRead);
                            }
                            complete = true;
                        } finally {
                            if (bo != null)
                                try {
                                    bo.close();
                                } catch (IOException e) {
                                }
                            if (is != null)
                                try {
                                    is.close();
                                } catch (IOException e) {
                                }
                            if (!complete) {
                                if (tmpFile != null && tmpFile.exists())
                                    tmpFile.delete();
                                continue;
                            }
                        }

                        Bitmap bm = decodeFile(tmpFile, MAX_IMAGE_SIZE_PX);
                        if (bm == null) {
                            throw new ImportException(STATUS_FORMAT_ERROR);
                        }

                        name = String.format("%04d", maxID) + '_' + name;
                        File convertedImage = new File(importedFilesDir, name);
                        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(convertedImage));
                        try {
                            if (!bm.compress(Bitmap.CompressFormat.PNG, 100, bos)) {
                                throw new ImportException(STATUS_FORMAT_ERROR);
                            }
                        } finally {
                            if (bos != null) {
                                try {bos.close();} catch (IOException io) {}
                            }
                            tmpFile.delete();
                        }

                        Log.d(TAG, "received file: " + (name != null ? name : "") + ", "
                                + convertedImage.length()
                                + ", width: " + bm.getWidth() + ", height: " + bm.getHeight()
                        );
                    }
                } catch(SecurityException se) {
                    status = STATUS_SECURITY_ERROR;
                } catch (ImportException ie) {
                    status = ie.errorCode;
                } catch (Exception e) {
                    status = STATUS_ERROR;
                    Log.d(TAG, "importing files failed", e);
                } finally {
                    if (c != null) {
                        c.close();
                    }
                    try {
                        fileInfo.put("status", status);
                    } catch(JSONException e) {}
                }
            } // end for
        }

        if (fileArray.length() > 0) {
            // notify observer
            Intent result = new Intent(INTENT_ACTION);
            result.putExtra(ARG_RESULT_JSON, fileArray.toString());
            LocalBroadcastManager.getInstance(mAppContext).sendBroadcast(result);
        }
    }

    protected File getTempFileDir() {
        return mAppContext.getCacheDir(); //TODO: check
    }

    protected File getImportedFilesDir() {
        return getImportedFilesDir(mAppContext);
    }

    public static void deleteImportedFilesDir(Context context) {
        File importedFilesDir = getImportedFilesDir(context);
        String files[] = importedFilesDir.list();
        File td;
        if (files != null) {
            for(String f : files) {
                td = new File(importedFilesDir, f);
                if (td.delete()) {
                    Log.d(TAG, "file " + f + " deleted");
                } else {
                    Log.d(TAG, "could not delete file " + f);
                }
            }
        }
    }

    public static File getImportedFilesDir(Context context) {
        return new File(context.getFilesDir(), LOCAL_IMPORTED_FILES_DIR);
    }

    private long getFreeSpace() {
        return mAppContext.getFilesDir().getFreeSpace();
    }
    /** returns true if internal memory less than 100 MB  space are free */
    public boolean lowInternalMemory(long datasize) {
        return lowMemory(datasize);
    }

    public boolean lowMemory(long datasize) {
        return getFreeSpace() - datasize  < MIN_FREE_SPACE_INT;
    }

    public static ArrayList<Uri> getUriListFromClipData(ClipData data) {
        ArrayList<Uri> list = new ArrayList<>();
        for(int i = 0; i < data.getItemCount(); i++) {
            Uri u = data.getItemAt(i).getUri();
            if (u != null) {
                list.add(u);
            }
        }
        return list;
    }

    public static boolean hasFileScheme(Uri u) {
        return u != null && u.getScheme() != null && u.getScheme().equals("file");
    }

    public static boolean hasFileScheme(ArrayList<Uri> uList) {
        boolean hasFileScheme = false;
        if (uList != null) {
            for (Uri s : uList) {
                if (hasFileScheme(s)) {
                    hasFileScheme = true;
                    break;
                }
            }
        }
        return hasFileScheme;
    }

    public static Bitmap decodeFile(File file, int reqWidth){
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getPath(), options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqWidth || width > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            int heightRatio = Math.round((float) width / (float) reqWidth);
            int widthRatio = Math.round((float) height / (float) reqWidth);

            // Choose the largest ratio as inSampleSize value
            inSampleSize = heightRatio > widthRatio ? heightRatio : widthRatio;
        }
        Log.d(TAG, "height: " + height + ", width: " + width +
                ", output height: " + options.outHeight +
                ", output width: " + options.outWidth  +
                ", samplesize: " +inSampleSize );

        return inSampleSize;
    }

    public static boolean hasContentScheme(Uri u) {
        return u != null && u.getScheme() != null && u.getScheme().equals("content");
    }

    private static class ImportException extends Exception {
        private ImportException(int code) {
            errorCode = code;
        }
        int errorCode;
    }

    public static String replaceInvalidChars(String filename) {
        String f = null;
        if (filename != null) {
            char[] fc = filename.toCharArray();
            boolean replaced = false;
            for(int i = 0; i < fc.length; i++) {
                if (FILE_RESERVED_CHARS.contains(fc[i])) {
                    fc[i] = FILE_CHAR_REPL;
                    replaced = true;
                }
            }
            if (replaced) {
                f = new String(fc);
            } else {
                f = filename;
            }
        }
        return f;
    }

    private DragAndDropPermissions mDragAndDropPermissions;
    private Object mClipData;
    private Context mAppContext;
    private ArrayList<Uri> mFileUris;

    private final static HashSet<Character> FILE_RESERVED_CHARS = new HashSet(
            Arrays.asList(new char[] {'|', '\\', '?', '*', '<', '\"', ':', '>', '/'}));
    private final static char FILE_CHAR_REPL = '�';

    private final static int BUFFER_SIZE = 16384;
    private final static String TMP_PREFIX = "tmp_";
    private final static String LOCAL_IMAGE_DIR = "images";
    private final static String LOCAL_IMPORTED_FILES_DIR = LOCAL_IMAGE_DIR + "/" + "imported";

    public final static long MIN_FREE_SPACE_INT = 100L * 1024L * 1024L;
    public final static int MAX_IMAGE_SIZE_PX = 512;
    private final static String TAG = ImportFiles.class.getSimpleName();

    public final static String INTENT_ACTION = TAG;
    public final static String ARG_RESULT_JSON = "ARG_RESULT_JSON";
    public final static int STATUS_OK = 0;
    public final static int STATUS_SECURITY_ERROR = 4;
    public final static int STATUS_FORMAT_ERROR = 3;
    public final static int STATUS_LOWMEM_ERROR = 2;
    public final static int STATUS_ERROR = 1;
}
