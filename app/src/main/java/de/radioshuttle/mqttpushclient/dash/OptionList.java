/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import android.graphics.drawable.Drawable;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

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
                    jsonOption.put("value", option.value == null ? "" : option.value);
                    jsonOption.put("displayvalue", option.displayValue == null ? "" : option.displayValue);
                    jsonOption.put("uri", option.imageURI == null ? "" : option.imageURI);
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
                    option.imageURI = jsonOption.optString("uri");
                    optionList.add(option);
                }
            }
        }
    }

    public String getDisplayValue() {
        String displayValue = null;
        OptionList.Option option = getSelectedOption();
        if (option != null) {
            displayValue = option.displayValue;
        }
        return displayValue;
    }

    public Drawable getDisplayDrawable() { //TODO: detailView?
        Drawable drawable = null;
        OptionList.Option option = getSelectedOption();
        if (option != null) {
            drawable = option.uiImage;
        }
        return drawable;
    }

    public OptionList.Option getSelectedOption() {
        OptionList.Option option = null;
        String content = (String) data.get("content");
        if (optionList != null) {
            for(OptionList.Option o : optionList) {
                if (Utils.equals(content, o.value)) {
                    option = o;
                    break;
                }
            }
        }
        return option;
    }

    public static class Option {
        public Option() {}
        public Option(String value, String displayValue, String uri) {
            this.value = value;
            this.displayValue = displayValue;
            this.imageURI = uri;
        }
        public Option(Option o) {
            this(o.value, o.displayValue, o.imageURI);
        }
        public String value;
        public String displayValue;
        public String imageURI;

        // helper vars used in UI
        public String error; // valueError (e.g. value not unique)
        public String errorImage; // image resource not found
        int newPos = AdapterView.INVALID_POSITION; //
        long selected; // 0 == not selected, >=0 selection timestamp
        int temp;
        Drawable uiImage;
        Drawable uiImageDetail;
        String uiImageURL;
    }
}
