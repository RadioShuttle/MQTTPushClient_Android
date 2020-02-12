/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import org.json.JSONException;
import org.json.JSONObject;

public class TextItem extends Item {

    @Override
    public String getType() {
        return "text";
    }

    @Override
    public JSONObject toJSONObject() throws JSONException {
        JSONObject o = super.toJSONObject();
        o.put("input_type", inputtype);
        return o;
    }

    protected void setJSONData(JSONObject o) {
        super.setJSONData(o);
        inputtype = o.optInt("input_type");
    }

    public final static int TYPE_STRING = 0;
    public final static int TYPE_NUMBER = 1;

    public int inputtype;

}
