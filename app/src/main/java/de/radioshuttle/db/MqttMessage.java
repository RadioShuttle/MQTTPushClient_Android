/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity (tableName = "mqtt_messages",
        indices = {@Index(value = {"push_server_id", "mqtt_accont_id", "when", "seqno"}, unique = true)})
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
    private int seqno;

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

    public void setSeqno(int seqNo) {
        seqno = seqNo;
    }

    public int getSeqno() {
        return seqno;
    }

    public final static String UPDATE_INTENT = "MQTT_MSG_UPDATE";
    public final static String DELETE_INTENT = "MQTT_MSG_DELETE";
    public final static String MSG_CNT_INTENT = "MSG_CNT_INTENT";

    public final static String ARG_CHANNELNAME = "MQTT_DEL_CHANNELNAME";
    public final static String ARG_PUSHSERVER_ADDR = "MQTT_MSG_UPDATE_PUSHSERVER";
    public final static String ARG_MQTT_ACCOUNT = "MQTT_MSG_UPDATE_ACC";
    public final static String ARG_CNT = "MQTT_MSG_CNT";
    public final static String ARG_IDS = "MQTT_MSG_UPDATE_IDS";

    public static long MESSAGE_EXPIRE_MS = 30L * 24L * 1000L * 3600L;
}
