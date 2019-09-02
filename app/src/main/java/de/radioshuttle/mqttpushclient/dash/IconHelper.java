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
        INTENRAL_ICONS.put("res://internal/notifications", R.drawable.xic_notifications);
        INTENRAL_ICONS.put("res://internal/notifications_active", R.drawable.xic_notifications_active);
        INTENRAL_ICONS.put("res://internal/notifications_none", R.drawable.xic_notifications_none);
        INTENRAL_ICONS.put("res://internal/notifications_off", R.drawable.xic_notifications_off);
        INTENRAL_ICONS.put("res://internal/notifications_paused", R.drawable.xic_notifications_paused);

        INTENRAL_ICONS.put("res://internal/emoji_objects", R.drawable.xic_emoji_objects);

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
