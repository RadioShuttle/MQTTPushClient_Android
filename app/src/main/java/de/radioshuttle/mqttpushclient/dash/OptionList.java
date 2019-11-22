/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;

import de.radioshuttle.utils.Utils;

public class OptionList extends Item {

    public OptionList() {
        optionList = new LinkedList<>();
    }

    @Override
    public String getType() {
        return "optionlist";
    }

    public LinkedList<Option> optionList;

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        JSONArray jsonArr = new JSONArray();
        if (optionList != null) {
            JSONObject jsonOption;
            for(Option option : optionList) {
                if (!Utils.isEmpty(option.value)) {
                    jsonOption = new JSONObject();
                    jsonOption.put("value", option.value);
                    jsonOption.put("displayvalue", option.displayValue == null ? "" : option.displayValue);
                    jsonArr.put(jsonOption);
                }
            }
        }
        o.putOpt("optionlist", jsonArr);
        return o;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        if (optionList == null) {
            optionList = new LinkedList<>();
        } else {
            optionList.clear();
        }
        JSONArray jsonArr = o.optJSONArray("optionlist");
        if (jsonArr != null) {
            Option option;
            JSONObject jsonOption;
            for(int i = 0; i < jsonArr.length(); i++) {
                jsonOption = jsonArr.optJSONObject(i);
                if (jsonOption != null) {
                    option = new Option();
                    option.value = jsonOption.optString("value");
                    option.displayValue = jsonOption.optString("displayvalue");
                    if (!Utils.isEmpty(option.value)) {
                        optionList.add(option);
                    }
                }
            }
        }
    }

    public String getDisplayValue() {
        String displayValue = null;
        String content = (String) data.get("content");
        if (optionList != null) {
            for(OptionList.Option o : optionList) {
                if (Utils.equals(content, o.value)) {
                    displayValue = o.displayValue;
                    break;
                }
            }
        }
        return displayValue;
    }

    public static class Option {
        public Option() {}
        public Option(String value, String displayValue) {
            this.value = value;
            this.displayValue = displayValue;
        }
        public String value;
        public String displayValue;
    }
}
