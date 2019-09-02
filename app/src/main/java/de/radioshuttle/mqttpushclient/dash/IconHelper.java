/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import de.radioshuttle.mqttpushclient.R;

public class IconHelper {

    public static LinkedHashMap<String, Integer> INTENRAL_ICONS;

    static {
        INTENRAL_ICONS = new LinkedHashMap<>();

        // toggle
        INTENRAL_ICONS.put("res://internal/toggle_on", R.drawable.xic_toggle_on);
        INTENRAL_ICONS.put("res://internal/toggle_off", R.drawable.xic_toggle_off);
        INTENRAL_ICONS.put("res://internal/check_box", R.drawable.xic_check_box);
        INTENRAL_ICONS.put("res://internal/check_box_outline_blank", R.drawable.xic_check_box_outline_blank);
        INTENRAL_ICONS.put("res://internal/indeterminate_check_box", R.drawable.xic_indeterminate_check_box);

        INTENRAL_ICONS.put("res://internal/radio_button_checked", R.drawable.xic_radio_button_checked);
        INTENRAL_ICONS.put("res://internal/radio_button_unchecked", R.drawable.xic_radio_button_unchecked);

        // alarm
        INTENRAL_ICONS.put("res://internal/notifications", R.drawable.xic_notifications);
        INTENRAL_ICONS.put("res://internal/notifications_active", R.drawable.xic_notifications_active);
        INTENRAL_ICONS.put("res://internal/notifications_none", R.drawable.xic_notifications_none);
        INTENRAL_ICONS.put("res://internal/notifications_off", R.drawable.xic_notifications_off);
        INTENRAL_ICONS.put("res://internal/notifications_paused", R.drawable.xic_notifications_paused);

        // msic
        INTENRAL_ICONS.put("res://internal/emoji_objects", R.drawable.xic_emoji_objects);

        INTENRAL_ICONS.put("res://internal/sentiment_dissatisfied", R.drawable.xic_sentiment_dissatisfied);
        INTENRAL_ICONS.put("res://internal/sentiment_satisfied", R.drawable.xic_sentiment_satisfied);
        INTENRAL_ICONS.put("res://internal/sentiment_very_dissatisfied", R.drawable.xic_sentiment_very_dissatisfied);
        INTENRAL_ICONS.put("res://internal/sentiment_very_satisfied", R.drawable.xic_sentiment_very_satisfied);


    }

    public static String getURIForResourceID(int resourceID) {
        String uri = null;
        Set<Map.Entry<String, Integer>> es = IconHelper.INTENRAL_ICONS.entrySet();
        for(Map.Entry<String, Integer> e : es) {
            if (resourceID == e.getValue()) {
                uri = e.getKey();
                break;
            }
        }
        return uri;
    }
}
