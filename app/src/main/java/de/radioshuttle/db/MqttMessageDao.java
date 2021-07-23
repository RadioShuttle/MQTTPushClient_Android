/*
 * Copyright (c) 2018 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.db;

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

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

    @Query("SELECT * FROM mqtt_messages, codes c1, codes c2 WHERE c2.name = :pushServer and c2.id = push_server_id and c1.name = :account and mqtt_accont_id = c1.id and (:topicFilter is null or topic like '%' || :topicFilter || '%') ORDER BY `when` DESC")
    public abstract DataSource.Factory<Integer, MqttMessage> getReceivedMessages(String pushServer, String account, String topicFilter);

    @Query("SELECT * FROM mqtt_messages WHERE push_server_id = :pushID and mqtt_accont_id = :accountID  ORDER BY `when` DESC LIMIT 10")
    public List<MqttMessage> loadReceivedMessages(long pushID, long accountID);

    @Query("SELECT * FROM mqtt_messages WHERE push_server_id = :pushID and mqtt_accont_id = :accountID and topic = :topic  ORDER BY `when` DESC LIMIT 10")
    public List<MqttMessage> loadReceivedMessagesForTopic(long pushID, long accountID, String topic);

    @Query("SELECT * FROM mqtt_messages WHERE push_server_id = :pushID and mqtt_accont_id = :accountID AND `when` < :before ORDER BY `when` DESC LIMIT 10")
    public List<MqttMessage> loadReceivedMessagesBefore(long pushID, long accountID, long before);

    @Query("DELETE FROM mqtt_messages WHERE push_server_id = :pushServerID AND mqtt_accont_id = :mqttAccountID")
    public void deleteMessagesForAccount(long pushServerID, long mqttAccountID);

    @Query("DELETE FROM mqtt_messages WHERE push_server_id = :pushServerID AND mqtt_accont_id = :mqttAccountID AND `when` < :before")
    public void deleteMessagesForAccountBefore(long pushServerID, long mqttAccountID, long before);

    @Query("DELETE FROM mqtt_messages WHERE `when` < :before")
    public void deleteMessagesBefore(long before);
}
