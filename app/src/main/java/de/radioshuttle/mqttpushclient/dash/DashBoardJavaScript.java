/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.appcompat.content.res.AppCompatResources;

import com.squareup.duktape.Duktape;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.HeliosUTF8Decoder;
import de.radioshuttle.utils.JavaScript;
import de.radioshuttle.utils.Utils;

public class DashBoardJavaScript extends JavaScript {

    private DashBoardJavaScript(Application app) {
        try {
            color_js = Utils.getRawStringResource(app, "javascript_color", true);
            wrapper_filterscript_js = Utils.getRawStringResource(app, "javascript_wrapper_filterscript", true);
        } catch (IOException e) {
            Log.d(TAG, "Error loading raw resource: javascript_color.js", e);
        }
        this.app = app;
    }

    public static synchronized DashBoardJavaScript getInstance(Application app) {
        if (js == null) {
            js = new  DashBoardJavaScript(app);
        }
        return js;
    }

    public void initViewProperties(Context context, HashMap<String, Object> viewProps) {
        ViewPropertiesImpl viewProperties = new ViewPropertiesImpl(viewProps, app);
        ((Duktape) context.getInterpreter()).set("view", ViewProperties.class, viewProperties);
        if (!Utils.isEmpty(color_js)) {
            ((Duktape) context.getInterpreter()).evaluate(color_js);
        }
        if (!Utils.isEmpty(wrapper_filterscript_js)) {
            ((Duktape) context.getInterpreter()).evaluate(wrapper_filterscript_js);
        }
    }

    public Context initSetContent(String jsBody, String accUser, String accMqttServer, String accPushServer) throws Exception {
        if (jsBody == null) {
            jsBody = "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  var input = payloadStr; ");
        sb.append(jsBody);
        sb.append(" \n  if (msg.raw.byteLength > 0) { content = msg.raw; }"); // binary data takes precedence over msg.text
        sb.append("  else if (typeof msg.text === 'string') { content = new TextEncoder().encode(msg.text); } ");
        sb.append("  else { content = null; } ");
        sb.append("  if (content != null) content = Duktape.enc('base64', content);");

        return initFormatter(sb.toString(), accUser, accPushServer, accMqttServer);
    }

    public String setContent(Context context, String input, String topic) {
        String result = null;
        if (context instanceof DuktapeContext) {
            result = ((DuktapeContext) context).formatter.formatMsg(
                    String.valueOf(System.currentTimeMillis()),
                    topic,
                    null,
                    input,
                    0
            );
        }
        return result;
    }

    private interface ViewProperties {
        void setCtrlColor(double color);

        void setCtrlColorOff(double color);
        void setBackgroundColor(double color);
        void setCtrlBackgroundColor(double color);
        void setCtrlBackgroundColorOff(double color);
        void setTextColor(double color);
        void setTextSize(double size);
        void setTextFieldDefaultValue(String defaultInputValue);
        void setCtrlImage(String resourceName);
        void setCtrlImageOff(String resourceName);
        void _setUserData(String name);

        double getTextColor();
        double getBackgroundColor();
        double getCtrlBackgroundColor();
        double getCtrlBackgroundColorOff();
        double getCtrlColor();
        double getCtrlColorOff();
        String getTextFieldDefaultValue();
        double getTextSize();
        String getCtrlImage();
        String getCtrlImageOff();
        String _getUserData();
    }

    private static class ViewPropertiesImpl implements ViewProperties {
        public ViewPropertiesImpl(HashMap<String, Object> props, Application app) {
            p = props;
            this.app = app;
            unknownImgtxt = app.getString(R.string.error_javascript_img_resource_not_found);
            tempResoure = app.getString(R.string.error_javascript_tmp_img_resource);
        }

        public HashMap<String, Object> p;

        @Override
        public void setCtrlColor(double color) {
            p.put("ctrl_color", doubleToLong(color));
        }

        @Override
        public void setCtrlColorOff(double color) {
            p.put("ctrl_color_off", doubleToLong(color));
        }

        @Override
        public void setCtrlImage(String resourceName) {
            setCtrlImage("ctrl_image", resourceName);
        }

        @Override
        public void setCtrlImageOff(String resourceName) {
            setCtrlImage("ctrl_image_off", resourceName);
        }

        private void setCtrlImage(String propKey, String resourceName) {
            /* resource names differ from internal representation */
            String uri = null;
            if (!Utils.isEmpty(resourceName)) {
                if (resourceName.toLowerCase().startsWith("tmp/")) { // imported but not saved images are not allowed
                    throw new RuntimeException(tempResoure + " " + resourceName);
                }
                uri = ImageResource.getResourceURI(app, resourceName);
                if (Utils.isEmpty(uri)) {
                    throw new RuntimeException(unknownImgtxt + " " + resourceName);
                }
            }
            String propKeyBlob = propKey + "_blob";
            String prevURI = (String) p.get(propKey);

            if (Utils.isEmpty(uri)) {
                p.remove(propKeyBlob); // remove prev blob, if any
                p.put(propKey, uri);
                // Log.d(TAG, "setCtrlImage: cleared");
            } else {
                /* load image (if not already exists) */
                if (!uri.equals(prevURI)) {
                    Drawable img = null;
                    try {
                        if (ImageResource.isInternalResource(uri)) {
                            img = AppCompatResources.getDrawable(app, IconHelper.INTENRAL_ICONS.get(uri));
                            // Log.d(TAG, "setCtrlImage: loaded internal " + uri);
                        } else { // user
                            img = ImageResource.loadExternalImage(app, uri);
                            // Log.d(TAG, "setCtrlImseage: loaded user " + uri);
                        }
                        p.put(propKeyBlob, img);
                        p.put(propKey, uri);
                    } catch (Exception e) {
                        Log.d(TAG, "Error loading image (javascript): " + e.getMessage());
                    }
                } else {
                    // Log.d(TAG, "setCtrlImaage: image already exists. skipping load " + uri);
                }
            }

        }

        @Override
        public void _setUserData(String name) {
            p.put("userdata", name);
        }

        @Override
        public String getCtrlImage() {
            return convertToJSResourceName((String) p.get("ctrl_image"));
        }

        @Override
        public String getCtrlImageOff() {
            return convertToJSResourceName((String) p.get("ctrl_image_off"));
        }

        private String convertToJSResourceName(String uri) {
            String jsResource = "";
            if (!Utils.isEmpty(uri)) {
                if (uri.startsWith("res://internal/")) {
                    jsResource = "int/" + uri.substring(15);
                } else if (uri.startsWith("res://user/")) {
                    jsResource = "user/" + uri.substring(11);
                }
            }
            return jsResource;
        }

        @Override
        public void setTextColor(double color) {
            p.put("textcolor", doubleToLong(color));
        }

        @Override
        public void setTextFieldDefaultValue(String defaultInputValue) {
            p.put("text.default", defaultInputValue == null ? "" : defaultInputValue);
        }

        @Override
        public void setBackgroundColor(double color) {
            // Log.d(TAG, "c: " + color);
            p.put("background", doubleToLong(color));
        }

        @Override
        public void setCtrlBackgroundColor(double color) {
            p.put("ctrl_background", doubleToLong(color));
        }

        @Override
        public void setCtrlBackgroundColorOff(double color) {
            p.put("ctrl_background_off", doubleToLong(color));
        }

        @Override
        public double getCtrlColor() {
            return longToDouble((long) (p.get("ctrl_color") == null ? 0L : p.get("ctrl_color")));
        }

        @Override
        public double getCtrlColorOff() {
            return longToDouble((long) (p.get("ctrl_color_off") == null ? 0L : p.get("ctrl_color_off")));
        }

        @Override
        public double getBackgroundColor() {
            return longToDouble((long) (p.get("background") == null ? 0L : p.get("background")));
        }
        @Override
        public double getCtrlBackgroundColor() {
            return longToDouble((long) (p.get("ctrl_background") == null ? 0L : p.get("ctrl_background")));
        }

        @Override
        public double getCtrlBackgroundColorOff() {
            return longToDouble((long) (p.get("ctrl_background_off") == null ? 0L : p.get("ctrl_background_off")));
        }

        @Override
        public double getTextColor() {
            return longToDouble((long) (p.get("textcolor") == null ? 0L : p.get("textcolor")));
        }

        @Override
        public String getTextFieldDefaultValue() {
            return p.get("text.default") instanceof String ? (String) p.get("text.default") : "";
        }

        @Override
        public void setTextSize(double size) {
            p.put("textsize", doubleToInt(size));
        }

        @Override
        public String _getUserData() {
            return  p.get("userdata") == null ? "" : (String) p.get("userdata");
        }

        @Override
        public double getTextSize() {
            return intToDouble((int) (p.get("textsize") == null ? 0 : p.get("textsize")));
        }

        protected double longToDouble(long i) {
            double v;
            if (i == DColor.CLEAR) {
                v = DColor.CLEAR;
            } else if (i == DColor.OS_DEFAULT) {
                v = DColor.OS_DEFAULT;
            } else {
                v = i & 0xFFFFFFFFL;
            }
            return v;
        }

        protected long doubleToLong(double d) {
            long v;
            if (d == (double) DColor.CLEAR) {
                v = DColor.CLEAR;
            } else if (d == (double) DColor.OS_DEFAULT) {
                v = DColor.OS_DEFAULT;
            } else {
                v = (long) d & 0xFFFFFFFFL;
            }
            return v;
        }

        protected double intToDouble(int i) {
            return (long)i & 0xFFFFFFFFL;
        }

        protected int doubleToInt(double d) {
            return (int) ((long) d & 0xFFFFFFFFL);
        }

        String unknownImgtxt;
        String tempResoure;
        Application app;
    }

    private Application app;
    private String color_js;
    private String wrapper_filterscript_js;
    private static DashBoardJavaScript js;

    private final static String TAG = JavaScript.class.getSimpleName();

}
