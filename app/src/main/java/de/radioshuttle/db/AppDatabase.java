/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.db;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;

@Database(entities = {MqttMessage.class, Code.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {

    public abstract MqttMessageDao mqttMessageDao();

    public static synchronized AppDatabase getInstance(Context appContext) {
        if (db == null) {
            db = Room.databaseBuilder(appContext, AppDatabase.class, "mqtt_messages_db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return db;
    }

    private static AppDatabase db;
}

