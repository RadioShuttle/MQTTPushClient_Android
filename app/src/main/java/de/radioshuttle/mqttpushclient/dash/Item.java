/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;


import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public abstract class Item {
    public Item() {
        data = new HashMap<>();
        textcolor = DColor.OS_DEFAULT;
        background = DColor.OS_DEFAULT;
        liveData = new MutableLiveData<>();
        liveData.setValue(0);
    }

    // IMPORTANT: all members changeable via CutsomItem webview must be volatile or synchronized
    public int id;
    public volatile long textcolor; // flag, alpha, r, g, b
    public volatile long background; // flag, alpha, r, g, b
    public int textsize; // 0 - default, 1 - small, 2 - medium, 3 - large
    public volatile String topic_s;
    public volatile String script_f;
    public volatile String background_uri;

    public String topic_p;
    public String script_p;
    public boolean retain;
    public String label;
    public String outputScriptError;

    // transient
    public Drawable backgroundImage;
    public Drawable backgroundImageDetail;
    public String backgroundImageURI;

    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = new JSONObject();
        if (!(this instanceof GroupItem)) {
            o.put("topic_s", topic_s);
            o.put("script_f", (script_f == null ? "" : script_f));

            o.put("topic_p", topic_p);
            o.put("script_p", (script_p == null ? "" : script_p));

            o.put("retain", retain);
            o.put("background_uri", background_uri == null ? "" : background_uri);
        }
        o.put("type", getType());
        o.put("label", label);
        o.put("textcolor", textcolor);
        o.put("background", background);
        o.put("textsize", textsize);
        o.put("id", id);

        return o;
    }

    protected void setJSONData(JSONObject o) {
        label = o.optString("label");
        background = o.optLong("background");
        textcolor = o.optLong("textcolor");
        textsize = o.optInt("textsize");
        topic_s = o.optString("topic_s");
        script_f = o.optString("script_f");
        topic_p = o.optString("topic_p");
        script_p = o.optString("script_p");
        retain = o.optBoolean("retain");
        background_uri = o.optString("background_uri");
        id = o.optInt("id");
    }

    public static Item createItemFromJSONObject(JSONObject o) {
        Item item = null;
        String type = o.optString("type");
        switch (type) {
            case "text": item = new TextItem(); break;
            case "group": item = new GroupItem(); break;
            case "progress" : item = new ProgressItem(); break;
            case "switch" : item = new Switch(); break;
            case "custom" : item = new CustomItem(); break;
            case "optionlist" : item = new OptionList(); break;
        }
        if (item != null) {
            item.setJSONData(o);
        }
        return item;
    }

    /* helper for view component (used in adapter and detail dialog */

    public void setViewBackground(View v, int defalutBackgroundColor, boolean detailView) {
        if (v != null) {
            long bg = data.containsKey("background") ? (Long) data.get("background") : background;
            // int background = (bg == 0 ? defalutBackgroundColor : bg);
            int background;
            if (bg == DColor.OS_DEFAULT) {
                background = defalutBackgroundColor;
            } else if (bg == DColor.CLEAR) {
                background = defalutBackgroundColor; // chose default color as background TODO: consider transparent
            } else {
                background = (int) bg;
            }

            if (this instanceof GroupItem || this instanceof CustomItem || (this instanceof OptionList && detailView)) {
                //TODO: image may be shown if detailView and not publishEnabled
                /* no background images for groups and CustomItems (webviews) */
                v.setBackgroundColor(background);
            } else {
                Drawable drawable;
                drawable = (Drawable) data.get("background_image_blob");
                if (drawable != null) {
                    if (detailView) {
                        drawable = drawable.getConstantState().newDrawable(v.getResources());
                    }
                } else {
                    drawable = (detailView ? backgroundImageDetail : backgroundImage);
                }

                Drawable optionDrawable = null;
                if (this instanceof OptionList) {
                    optionDrawable = ((OptionList) this).getDisplayDrawable(); //TODO: what about detailView?
                }

                if ((!Utils.isEmpty(backgroundImageURI) && drawable != null)  || optionDrawable != null) {
                    ColorDrawable drawableBackground = new ColorDrawable(background);
                    ArrayList<Drawable> drawables = new ArrayList<>();
                    drawables.add(drawableBackground);
                    if (!Utils.isEmpty(backgroundImageURI) && drawable != null) {
                        drawables.add(drawable);
                    }
                    if (optionDrawable != null) {
                        drawables.add(optionDrawable);
                    }

                    LayerDrawable layerDrawable = new LayerDrawable(drawables.toArray(new Drawable[drawables.size()]));
                    ViewGroup.LayoutParams lp = v.getLayoutParams();
                    if (lp != null) {
                        for(int i = 1; i < drawables.size(); i++) {
                            /* user images (bitmaps) are downscaled*/
                            Drawable dr = drawables.get(i);
                            if (dr instanceof BitmapDrawable) {
                                BitmapDrawable bm = (BitmapDrawable) dr;
                                float w = bm.getBitmap().getWidth();
                                float h = bm.getBitmap().getHeight();
                                float diff, f = 1f;
                                if (w > h) {
                                    if (w > lp.width) {
                                        diff = w - lp.width;
                                        f = 1f - 1f / w * diff;
                                    }
                                } else {
                                    if (h > lp.height) {
                                        diff = h - lp.height;
                                        f = 1f - 1f / h * diff;
                                    }
                                }

                                // Log.d(TAG, "bitmap size: " + w + ", h: " + h + ". Item size: " + lp.width + ", " + lp.height);
                                w = (lp.width - w * f) / 2f;
                                h = (lp.height - h * f) / 2f;
                                // Log.d(TAG, "bitmap size (downscaled): " + w + ", h: " + h);
                                layerDrawable.setLayerInset(i, (int) w, (int) h, (int) w, (int) h );
                            }
                        }
                        /* vector drawables will be up/daownscaled */
                    }
                    v.setBackground(layerDrawable);
                } else {
                    v.setBackgroundColor(background);
                }
            }
        }
    }

    /** set text color and text appearance */
    public void setViewTextAppearance(TextView v, int defaultColor) {
        if (v != null) {
            int textsize = data.containsKey("textsize") ?  (Integer) data.get("textsize") : this.textsize;
            int textSizeIdx = (textsize <= 0 ? Item.DEFAULT_TEXTSIZE : textsize ) -1;
            if (textSizeIdx >= 0 && textSizeIdx < TEXTAPP.length) {
                TextViewCompat.setTextAppearance(v, TEXTAPP[textSizeIdx]);
            }

            long color = getTextcolor();
            if (color == DColor.OS_DEFAULT || color == DColor.CLEAR) { // clear is inavalid, treat as DEFAULT
                v.setTextColor(defaultColor);
            } else {
                v.setTextColor((int) color);
            }

        }
    }

    /** add properties which may be get/set in JS to update view. Also extend DashboarJavascript.ViewProperties */
    public HashMap<String, Object> getJSViewProperties(HashMap<String, Object> viewProperties) {
        if (viewProperties == null) {
            viewProperties = new HashMap<>();
        }
        viewProperties.put("textcolor", data.containsKey("textcolor") ? (Long) data.get("textcolor") : textcolor);
        viewProperties.put("textsize", data.containsKey("textsize") ?  (Integer) data.get("textsize") : textsize);
        viewProperties.put("background", data.containsKey("background") ? (Long) data.get("background") : background);
        viewProperties.put("background_image", data.containsKey("background_image") ? (String) data.get("background_image") : background_uri);
        viewProperties.put("topic_s", topic_s);
        viewProperties.put("topic_p", topic_p);
        if (data.containsKey("userdata")) {
            viewProperties.put("userdata", data.get("userdata") == null ? "" : data.get("userdata"));
        }

        return viewProperties;
    }

    @MainThread
    public void notifyDataChanged() {
        liveDataTimestamp = System.currentTimeMillis();
        liveData.setValue(id);
    }

    public void notifyDataChangedNonUIThread() {
        liveDataTimestamp = System.currentTimeMillis();
        liveData.postValue(id);
    }

    protected void updateUIContent(Context context) {
    }

    protected long getTextcolor() {
        return data.containsKey("textcolor") ? (Long) data.get("textcolor") : textcolor;
    }

    protected long getBackgroundColor() {
        return data.containsKey("background") ? (Long) data.get("background") : background;
    }


    final static int[] TEXTAPP = new int[] {android.R.style.TextAppearance_Small, android.R.style.TextAppearance_Medium, android.R.style.TextAppearance_Large};

    public Map<String, Object> data;

    public abstract String getType();

    public MutableLiveData<Integer> liveData;
    public volatile long liveDataTimestamp;

    public final static int DEFAULT_TEXTSIZE = 1;

    /** Dashboard version */
    public final static int DASHBOARD_VERSION = 0;

    protected static String TAG = Item.class.getSimpleName();
}
