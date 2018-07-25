/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.db;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

@Entity (tableName = "mqtt_messages",
        indices = {@Index(value = {"push_server_id", "mqtt_accont_id", "when"})})
public class MqttMessage {
    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "push_server_id")
    private int pushServerID;
    @ColumnInfo(name = "mqtt_accont_id")
    private int mqttAccountID;

    private long when;
    private String topic;
    private String msg;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPushServerID() {
        return pushServerID;
    }

    public void setPushServerID(int pushServerID) {
        this.pushServerID = pushServerID;
    }

    public int getMqttAccountID() {
        return mqttAccountID;
    }

    public void setMqttAccountID(int mqttAccountID) {
        this.mqttAccountID = mqttAccountID;
    }

    public long getWhen() {
        return when;
    }

    public void setWhen(long when) {
        this.when = when;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
