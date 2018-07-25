/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.db;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;

import java.util.List;

@Dao
public interface MqttMessageDao {

    @Query("SELECT id FROM codes WHERE name = :forName")
    Long getCode(String forName);

    @Insert
    Long insertCode(Code code);

    @Insert
    Long insertMqttMessage(MqttMessage m);

    @Query("SELECT name from codes where id = :code")
    String getNameForCode(int code);

    @Query("SELECT * FROM mqtt_messages WHERE push_server_id = :pushServerID and mqtt_accont_id = :accountID ORDER BY `when` DESC")
    List<MqttMessage> getReceivedMessages(int pushServerID, int accountID);

}
