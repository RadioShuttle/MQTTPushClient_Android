/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2019 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient.dash;

import java.util.LinkedHashMap;

import de.radioshuttle.mqttpushclient.R;

public class IconHelper {

    public static LinkedHashMap<String, Integer> INTENRAL_ICONS;

    static {
        INTENRAL_ICONS = new LinkedHashMap<>();
        INTENRAL_ICONS.put("res://internal/notifications_active", R.drawable.xic_notifications_active);
        INTENRAL_ICONS.put("res://internal/notifications_off", R.drawable.xic_notifications_off);
    }

}
