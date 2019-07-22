/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
import android.util.Base64;
import android.util.Log;

import com.squareup.duktape.Duktape;

import java.io.IOException;
import java.util.HashMap;

import de.radioshuttle.utils.JavaScript;
import de.radioshuttle.utils.Utils;

public class DashBoardJavaScript extends JavaScript {

    private DashBoardJavaScript(Application app) {
        try {
            color_js = Utils.getRawStringResource(app, "javascript_color", true);
        } catch (IOException e) {
            Log.d(TAG, "Error loading raw resource: javascript_color.js", e);
        }
    }

    public static synchronized DashBoardJavaScript getInstance(Application app) {
        if (js == null) {
            js = new  DashBoardJavaScript(app);
        }
        return js;
    }

    public void initViewProperties(Context context, HashMap<String, Object> viewProps) {
        ViewPropertiesImpl viewProperties = new ViewPropertiesImpl(viewProps);
        ((Duktape) context.getInterpreter()).set("view", ViewProperties.class, viewProperties);
        if (!Utils.isEmpty(color_js)) {
            ((Duktape) context.getInterpreter()).evaluate(color_js);
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
        void setCtrlColor2(double color);
        void setBackgroundColor(double color);
        void setCtrlBackgroundColor(double color);
        void setCtrlBackgroundColor2(double color);
        void setTextColor(double color);
        void setTextSize(double size);
        void setTextFieldValue(String defaultInputValue);
        double getTextColor();
        double getBackgroundColor();
        double getCtrlBackgroundColor();
        double getCtrlBackgroundColor2();
        double getCtrlColor();
        double getCtrlColor2();
        String getTextFieldValue();
        double getTextSize();
    }

    private static class ViewPropertiesImpl implements ViewProperties {
        public ViewPropertiesImpl(HashMap<String, Object> props) {
            p = props;
        }

        public HashMap<String, Object> p;

        @Override
        public void setCtrlColor(double color) {
            p.put("ctrl_color", doubleToInt(color));
        }

        @Override
        public void setCtrlColor2(double color) {
            p.put("ctrl_color2", doubleToInt(color));
        }

        @Override
        public void setTextColor(double color) {
            p.put("textcolor", doubleToInt(color));
        }

        @Override
        public void setTextFieldValue(String defaultInputValue) {
            p.put("text.value", defaultInputValue == null ? "" : defaultInputValue);
        }

        @Override
        public void setBackgroundColor(double color) {
            // Log.d(TAG, "c: " + color);
            p.put("background", doubleToInt(color));
        }

        @Override
        public void setCtrlBackgroundColor(double color) {
            p.put("ctrl_background", doubleToInt(color));
        }

        @Override
        public void setCtrlBackgroundColor2(double color) {
            p.put("ctrl_background2", doubleToInt(color));
        }

        @Override
        public double getCtrlColor() {
            return intToDouble((int) (p.get("ctrl_color") == null ? 0 : p.get("ctrl_color")));
        }

        @Override
        public double getCtrlColor2() {
            return intToDouble((int) (p.get("ctrl_color2") == null ? 0 : p.get("ctrl_color2")));
        }

        @Override
        public double getBackgroundColor() {
            return intToDouble((int) (p.get("background") == null ? 0 : p.get("background")));
        }
        @Override
        public double getCtrlBackgroundColor() {
            return intToDouble((int) (p.get("ctrl_background") == null ? 0 : p.get("ctrl_background")));
        }

        @Override
        public double getCtrlBackgroundColor2() {
            return intToDouble((int) (p.get("ctrl_background2") == null ? 0 : p.get("ctrl_background2")));
        }

        @Override
        public double getTextColor() {
            return intToDouble((int) (p.get("textcolor") == null ? 0 : p.get("textcolor")));
        }

        @Override
        public String getTextFieldValue() {
            return p.get("text.value") instanceof String ? (String) p.get("text.value") : "";
        }

        @Override
        public void setTextSize(double size) {
            p.put("textsize", doubleToInt(size));
        }

        @Override
        public double getTextSize() {
            return intToDouble((int) (p.get("textsize") == null ? 0 : p.get("textsize")));
        }

        protected double intToDouble(int i) {
            return (long)i & 0xFFFFFFFFL;
        }

        protected int doubleToInt(double d) {
            return (int) ((long) d & 0xFFFFFFFFL);
        }
    }


    private String color_js;
    private static DashBoardJavaScript js;

    private final static String TAG = JavaScript.class.getSimpleName();

}
