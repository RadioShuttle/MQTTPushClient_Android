/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.db;

import android.arch.paging.DataSource;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
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
    String getNameForCode(long code);

    @Query("SELECT * FROM codes")
    List<Code> getCodes();

    @Query("SELECT * FROM mqtt_messages, codes c1, codes c2 WHERE c2.name = :pushServer and c2.id = push_server_id and c1.name = :account and mqtt_accont_id = c1.id  ORDER BY `when` DESC")
    public abstract DataSource.Factory<Integer, MqttMessage> getReceivedMessages(String pushServer, String account);

    @Query("SELECT * FROM mqtt_messages ORDER BY `when` DESC")
    public List<MqttMessage> loadReceivedMessages();

    @Query("DELETE FROM mqtt_messages WHERE push_server_id = :pushServerID AND mqtt_accont_id = :mqttAccountID")
    public void deleteMessagesForAccount(long pushServerID, long mqttAccountID);

    @Query("DELETE FROM mqtt_messages WHERE push_server_id = :pushServerID AND mqtt_accont_id = :mqttAccountID AND `when` < :before")
    public void deleteMessagesForAccountBefore(long pushServerID, long mqttAccountID, long before);

    @Query("DELETE FROM mqtt_messages WHERE `when` < :before")
    public void deleteMessagesBefore(long before);
}
