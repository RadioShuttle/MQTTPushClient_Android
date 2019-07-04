/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.app.Application;
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

    private interface ViewProperties {
        void setColor(double color);
        void setBackgroundColor(double color);
        void setTextColor(double color);
        void setTextFieldValue(String defaultInputValue);
        double getTextColor();
        double getBackgroundColor();
        double getColor();
        String getTextFieldValue();
    }

    private static class ViewPropertiesImpl implements ViewProperties {
        public ViewPropertiesImpl(HashMap<String, Object> props) {
            p = props;
        }

        public HashMap<String, Object> p;

        @Override
        public void setColor(double color) {
            p.put("color", doubleToInt(color));
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
        public double getColor() {
            return intToDouble((int) (p.get("color") == null ? 0 : p.get("color")));
        }

        @Override
        public double getBackgroundColor() {
            return intToDouble((int) (p.get("background") == null ? 0 : p.get("background")));
        }

        @Override
        public double getTextColor() {
            return intToDouble((int) (p.get("textcolor") == null ? 0 : p.get("textcolor")));
        }

        @Override
        public String getTextFieldValue() {
            return p.get("text.value") instanceof String ? (String) p.get("text.value") : "";
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
