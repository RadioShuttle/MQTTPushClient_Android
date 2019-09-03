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
        INTENRAL_ICONS.put("res://internal/lock", R.drawable.xic_lock);
        INTENRAL_ICONS.put("res://internal/lock_open", R.drawable.xic_lock_open);
        INTENRAL_ICONS.put("res://internal/notifications_active", R.drawable.xic_notifications_active);
        INTENRAL_ICONS.put("res://internal/notifications_off", R.drawable.xic_notifications_off);
        INTENRAL_ICONS.put("res://internal/videocam", R.drawable.xic_videocam);
        INTENRAL_ICONS.put("res://internal/videocam_off", R.drawable.xic_videocam_off);
        INTENRAL_ICONS.put("res://internal/signal_wifi_4_bar", R.drawable.xic_signal_wifi_4_bar);
        INTENRAL_ICONS.put("res://internal/signal_wifi_off", R.drawable.xic_signal_wifi_off);
        INTENRAL_ICONS.put("res://internal/wifi", R.drawable.xic_wifi);
        INTENRAL_ICONS.put("res://internal/wifi_off", R.drawable.xic_wifi_off);
        INTENRAL_ICONS.put("res://internal/alarm_on", R.drawable.xic_alarm_on);
        INTENRAL_ICONS.put("res://internal/alarm_off", R.drawable.xic_alarm_off);
        INTENRAL_ICONS.put("res://internal/timer", R.drawable.xic_timer);
        INTENRAL_ICONS.put("res://internal/timer_off", R.drawable.xic_timer_off);
        INTENRAL_ICONS.put("res://internal/airplanemode_active", R.drawable.xic_airplanemode_active);
        INTENRAL_ICONS.put("res://internal/airplanemode_inactive", R.drawable.xic_airplanemode_inactive);
        INTENRAL_ICONS.put("res://internal/visibility", R.drawable.xic_visibility);
        INTENRAL_ICONS.put("res://internal/visibility_off", R.drawable.xic_visibility_off);
        INTENRAL_ICONS.put("res://internal/thumbs_down", R.drawable.xic_thumbs_down);
        INTENRAL_ICONS.put("res://internal/thumbs_up", R.drawable.xic_thumbs_up);

        // msic
        INTENRAL_ICONS.put("res://internal/check_circle", R.drawable.xic_check_circle);
        INTENRAL_ICONS.put("res://internal/check_circle_outline", R.drawable.xic_check_circle_outline);
        INTENRAL_ICONS.put("res://internal/ac_unit", R.drawable.xic_ac_unit);
        INTENRAL_ICONS.put("res://internal/emoji_objects", R.drawable.xic_emoji_objects);
        INTENRAL_ICONS.put("res://internal/error", R.drawable.xic_error);
        INTENRAL_ICONS.put("res://internal/error_outline", R.drawable.xic_error_outline);
        INTENRAL_ICONS.put("res://internal/house", R.drawable.xic_house);
        INTENRAL_ICONS.put("res://internal/warning", R.drawable.xic_warning);
        INTENRAL_ICONS.put("res://internal/not_interested", R.drawable.xic_not_interested);
        INTENRAL_ICONS.put("res://internal/update", R.drawable.xic_update);
        INTENRAL_ICONS.put("res://internal/access_alarms", R.drawable.xic_access_alarms);
        INTENRAL_ICONS.put("res://internal/access_time", R.drawable.xic_access_time);
        INTENRAL_ICONS.put("res://internal/battery_alert", R.drawable.xic_battery_alert);
        INTENRAL_ICONS.put("res://internal/battery_full", R.drawable.xic_battery_full);

        // alarm
        INTENRAL_ICONS.put("res://internal/notifications", R.drawable.xic_notifications);
        INTENRAL_ICONS.put("res://internal/notifications_none", R.drawable.xic_notifications_none);
        INTENRAL_ICONS.put("res://internal/notifications_paused", R.drawable.xic_notifications_paused);
        INTENRAL_ICONS.put("res://internal/add_alert", R.drawable.xic_add_alert);
        INTENRAL_ICONS.put("res://internal/notification_important", R.drawable.xic_notification_important);

        // AV
        INTENRAL_ICONS.put("res://internal/forward_10", R.drawable.xic_forward_10);
        INTENRAL_ICONS.put("res://internal/forward_30", R.drawable.xic_forward_30);
        INTENRAL_ICONS.put("res://internal/forward_5", R.drawable.xic_forward_5);
        INTENRAL_ICONS.put("res://internal/pause_circle_filled", R.drawable.xic_pause_circle_filled);
        INTENRAL_ICONS.put("res://internal/pause_circle_outline", R.drawable.xic_pause_circle_outline);
        INTENRAL_ICONS.put("res://internal/pause_play_arrow", R.drawable.xic_play_arrow);
        INTENRAL_ICONS.put("res://internal/play_circle_filled", R.drawable.xic_play_circle_filled);
        INTENRAL_ICONS.put("res://internal/play_circle_outline", R.drawable.xic_play_circle_outline);
        INTENRAL_ICONS.put("res://internal/replay", R.drawable.xic_replay);
        INTENRAL_ICONS.put("res://internal/replay_10", R.drawable.xic_replay_10);
        INTENRAL_ICONS.put("res://internal/replay_30", R.drawable.xic_replay_30);
        INTENRAL_ICONS.put("res://internal/replay_5", R.drawable.xic_replay_5);
        INTENRAL_ICONS.put("res://internal/volume_down", R.drawable.xic_volume_down);
        INTENRAL_ICONS.put("res://internal/volume_mute", R.drawable.xic_volume_mute);
        INTENRAL_ICONS.put("res://internal/volume_off", R.drawable.xic_volume_off);
        INTENRAL_ICONS.put("res://internal/volume_up", R.drawable.xic_volume_up);

        // emojis
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
