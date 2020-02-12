/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.HashMap;

import de.radioshuttle.mqttpushclient.R;
import de.radioshuttle.utils.Utils;

public class ProgressItem extends Item {

    public double range_min;
    public double range_max;
    public int decimal;
    public long progresscolor;
    public boolean percent;

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        o.put("range_min", range_min);
        o.put("range_max", range_max);
        o.put("decimal", decimal);
        o.put("percent", percent);
        o.put("progresscolor", progresscolor);
        return o;
    }

    /** add properties which may be get/set in JS to update view. Also extend DashboarJavascript.ViewProperties */
    public HashMap<String, Object> getJSViewProperties(HashMap<String, Object> viewProperties) {
        viewProperties = super.getJSViewProperties(viewProperties);
        viewProperties.put("ctrl_color", data.containsKey("ctrl_color") ? (Long) data.get("ctrl_color") : progresscolor);

        return viewProperties;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        range_min = o.optDouble("range_min", 0d);
        range_max = o.optDouble("range_max", 0d);
        decimal = o.optInt("decimal", 0);
        percent = o.optBoolean("percent", false);
        progresscolor = o.optLong("progresscolor");
    }

    @Override
    protected void updateUIContent(Context context) {
        data.remove("content.progress");
        data.remove("error.item");
        if (!data.containsKey("error")) {
            String content = (String) data.get("content");
            double value, pc;
            if (!Utils.isEmpty(content)) {
                try {
                    value = Double.parseDouble(content);
                } catch(NumberFormatException e) {
                    data.put("error", context.getString(R.string.err_invalid_topic_format));
                    data.put("error.item", true);
                    return;
                }
                if (value < range_min || value > range_max) {
                    data.put("error", context.getString(R.string.error_out_of_range));
                    data.put("error.item", true);
                    return;
                }
                pc = calcProgessInPercent(value, range_min, range_max);
                if (percent) {
                    data.put("content.progress", (int) Math.floor(pc + .5d) + "%");
                } else {
                    NumberFormat f = NumberFormat.getInstance();
                    f.setMinimumFractionDigits(decimal);
                    f.setMaximumFractionDigits(decimal);
                    data.put("content.progress", f.format(value));
                }
            }
        }
    }

    public static double calcProgessInPercent(double v, double min, double max) {
        if (min < max) {
            return 100d / (max - min) * (v - min);
        } else {
            return 0;
        }
    }

    public ProgressItem() {
        range_max = 100d; // default
        progresscolor = DColor.OS_DEFAULT;
    }

    @Override
    public String getType() {
        return "progress";
    }
}
