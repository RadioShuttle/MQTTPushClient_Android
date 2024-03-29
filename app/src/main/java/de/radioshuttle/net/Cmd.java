/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Cmd {

    public final static int CMD_HELLO = 1;
    public final static int CMD_LOGIN = 2;
    public final static int CMD_GET_FCM_DATA = 3;
    public final static int CMD_GET_TOPICS = 4;
    public final static int CMD_ADD_TOPICS = 5;
    public final static int CMD_DEL_TOPICS = 6;
    public final static int CMD_UPD_TOPICS = 7;
    public final static int CMD_SET_DEVICE_INFO = 8;
    public final static int CMD_REMOVE_DEVICE = 9;
    public final static int CMD_GET_ACTIONS = 10;
    public final static int CMD_ADD_ACTION = 11;
    public final static int CMD_UPD_ACTION = 12;
    public final static int CMD_DEL_ACTIONS = 13;
    public final static int CMD_LOGOUT = 14; //TODO: unused?
    public final static int CMD_DISCONNECT = 15;
    public final static int CMD_MQTT_PUBLISH = 17;
    public final static int CMD_GET_FCM_DATA_IOS = 18;
    public final static int CMD_GET_MESSAGES = 19;
    public final static int CMD_ADMIN = 20;
    public final static int CMD_BACKUP = 21;
    public final static int CMD_SET_DASHBOARD = 22;
    public final static int CMD_GET_DASHBOARD = 23;
    public final static int CMD_GET_MESSAGES_DASH = 24;
    public final static int CMD_BACKUP_DASH = 25;
    public final static int CMD_SAVE_RESOURCE = 26;
    public final static int CMD_GET_RESOURCE = 27;
    public final static int CMD_DEL_RESOURCE = 28;
    public final static int CMD_ENUM_RESOURCES = 29;

    public RawCmd helloRequest(int seqNo, boolean ssl) throws IOException {
        int flags = FLAG_REQUEST;
        if (ssl) {
            flags |= FLAG_SSL;
        }
        return helloRequest(seqNo, flags);
    }

    public RawCmd helloRequest(int seqNo, int flags) throws IOException {
        writeCommand(CMD_HELLO, seqNo, flags, 0, new byte[] { PROTOCOL_MAJOR, PROTOCOL_MINOR });
        return readCommand();
    }

    public RawCmd saveResourceRequest(int seqNo, int mode, String name, String type, File resource) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.write(mode);
        writeString(name, os);
        writeString(type, os);
        os.writeLong(resource.lastModified() / 1000L);
        long s = resource.length();
        if (s <= 0 || s > MAX_PAYLOAD_RESOURCE) {
            throw new RuntimeException("Invalid size");
        }
        os.writeInt((int) s); // blob size
        byte[] args = ba.toByteArray();
        writeHeader(CMD_SAVE_RESOURCE, seqNo, FLAG_REQUEST, 0, args.length + (int) s);
        bos.write(args);
        bos.flush();
        /* attach file */
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(resource));
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        try {
            while((read = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            bos.flush();
        } finally {
            if (bis != null) {
                try { bis.close();} catch(Exception  io) {}
            }
        }
        return readCommand();
    }

    public RawCmd saveResourceRequest(int seqNo, int mode, String name, String type, byte[] resource) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.write(mode);
        writeString(name, os);
        writeString(type, os);
        os.writeLong(System.currentTimeMillis() / 1000L);
        long s = resource.length;
        if (s <= 0 || s > MAX_PAYLOAD_RESOURCE) {
            throw new RuntimeException("Invalid size");
        }
        os.writeInt((int) s); // blob size
        byte[] args = ba.toByteArray();
        writeHeader(CMD_SAVE_RESOURCE, seqNo, FLAG_REQUEST, 0, args.length + (int) s);
        bos.write(args);
        bos.flush();
        bos.write(resource);
        bos.flush();
        return readCommand();
    }

    public void saveResourceResponse(RawCmd request, String resourceName, String type) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(resourceName, os);
        writeString(type, os);
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    /* reads and returns args of request until blob */
    public Map<String, Object> readSaveResourceArgs() throws IOException {
        Map<String, Object> args = new HashMap<>();
        args.put("mode", dis.read());
        args.put("name", readString(dis));
        args.put("type", readString(dis));
        args.put("mdate", dis.readLong() * 1000L);
        args.put("bsize", dis.readInt());
        return args;
    }

    public RawCmd getResourceRequest(int seqNo, String name, String type) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(name, os);
        writeString(type, os);
        writeCommand(CMD_GET_RESOURCE, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public Map<String, Object> readGetResourceArgs(byte[] data) throws IOException {
        DataInputStream is = getDataInputStream(data);
        Map<String, Object> args = new HashMap<>();
        args.put("name", readString(is));
        args.put("type", readString(is));
        return args;
    }

    public void getResourceResponse(RawCmd request, File resource) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(resource.lastModified() / 1000L);

        long s = resource.length();
        if (s <= 0 || s > MAX_PAYLOAD_RESOURCE) {
            throw new RuntimeException("Invalid size");
        }
        os.writeInt((int) s); // blob size
        byte[] args = ba.toByteArray();
        writeHeader(request.command, request.seqNo, FLAG_RESPONSE, 0, args.length + (int) s);
        bos.write(args);
        bos.flush();

        /* attach file */
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(resource));
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        try {
            while((read = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            bos.flush();
        } finally {
            if (bis != null) {
                try { bis.close();} catch(Exception  io) {}
            }
        }
    }

    public RawCmd enumResourcesRequest(int seqNo, String type) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(type, os);
        writeCommand(CMD_ENUM_RESOURCES, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public void enumResourcesResponse(RawCmd request, List<FileInfo> resources) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (resources == null || resources.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(resources.size());
            for (FileInfo fi : resources) {
                writeString(fi.name, os);
                os.writeLong(fi.mdate / 1000L);
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public List<FileInfo> readEnumResourcesData(byte[] data) throws IOException {
        ArrayList<FileInfo> resources = new ArrayList<>();
        DataInputStream is = getDataInputStream(data);
        int len = is.readUnsignedShort();
        FileInfo fi;
        for(int i = 0; i < len; i++) {
            fi = new FileInfo();
            fi.name = readString(is);
            fi.mdate = is.readLong() * 1000L;
            resources.add(fi);
        }
        return resources;
    }

    public RawCmd deleteResourcesRequest(int seqNo, List<String> resourceNames, String type) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(type, os);
        if (resourceNames == null || resourceNames.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(resourceNames.size());
            for(String r : resourceNames) {
                writeString(r, os);
            }
        }
        writeCommand(CMD_DEL_RESOURCE, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public List<String> readDeleteResourcesData(byte[] data, Cmd.Ref<String> type) throws IOException {
        DataInputStream is = getDataInputStream(data);
        String ts = readString(is);
        if (type != null) {
            type.value = ts;
        }
        int len = is.readUnsignedShort();
        ArrayList<String> resourceNames = new ArrayList<>();
        for(int i = 0; i < len; i++) {
            resourceNames.add(readString(is));
        }
        return resourceNames;
    }

    public RawCmd loginRequest(int seqNo, String uri, String user, char[] password, String uuid) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(uri, os);
        writeString(user, os);
        if (password == null)
            password = new char[0];
        byte[] buf = toByteArray(password); // convert to UTF-8 bytes
        os.writeShort(buf.length);
        os.write(buf);
        writeString(uuid, os);
        writeCommand(CMD_LOGIN, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd adminCommand(int seqNo, String command) throws IOException {
        writeCommandStrPara(CMD_ADMIN, seqNo, command);
        return readCommand();
    }

    public Map<String, Object> readLoginData(byte[] data) throws IOException {
        HashMap<String, Object> m = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        m.put("uri", readString(is));
        m.put("user", readString(is));
        int n = is.readUnsignedShort();
        char[] pwd = new char[n];
        if (PROTOCOL_MAJOR == 1 && clientProtocolMinor < 2) { // pre 1.2 password is utf-16
            for (int i = 0; i < n; i++) {
                pwd[i] = is.readChar();
            }
        } else {
            byte[] buf = new byte[n];
            is.readFully(buf);
            pwd = toCharArray(buf); // convert to UTF-16 chars
        }
        m.put("password", pwd);

        byte[] uuid = null;
        if (is.available() >= 2) { // extended in 1.5
            n = is.readUnsignedShort();
            uuid = new byte[n];
            is.readFully(uuid);
        } else {
            uuid = new byte[0];
        }
        m.put("uuid", new String(uuid, "UTF-8"));
        return m;
    }

    public void helloResponse(RawCmd requestHeader, int rc) throws IOException {
        writeCommand(CMD_HELLO, requestHeader.seqNo, FLAG_RESPONSE, rc, new byte[] { PROTOCOL_MAJOR, PROTOCOL_MINOR});
    }

    /* rc should be RC_SERVER_ERROR or RC_MQTT_ERROR */
    public void errorResponse(RawCmd request, int rc, int errorCode, String errorMsg) throws IOException {
        errorResponse(request, rc, errorCode, errorMsg, null);
    }

    public void errorResponse(RawCmd request, int rc, int errorCode, String errorMsg, byte[] extra) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeShort(errorCode);
        writeString(errorMsg, os);
        if (extra != null) {
            os.write(extra);
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, rc, ba.toByteArray());
    }

    public Map<String, Object> readErrorData(byte[] data) throws IOException {
        HashMap<String, Object> m = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        m.put("err_code", is.readShort());
        m.put("err_msg", readString(is));
        return m;
    }

    public void fcmDataResponse(RawCmd request, String app_id, String senderID, String pushServerID, String project_id, String api_key) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(app_id, os);
        writeString(senderID, os);
        writeString(pushServerID, os);
        writeString(project_id, os);
        writeString(api_key, os);
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public Map<String, String> readFCMData(byte[] data) throws IOException {
        HashMap<String, String> m = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        m.put("app_id", readString(is));
        m.put("sender_id", readString(is));
        m.put("pushserverid", readString(is));
        if (is.available() > 0) { // TODO: remove after all push servers have been updated
            m.put("project_id", readString(is));
            m.put("api_key", readString(is));
        }
        return m;
    }

    public RawCmd setDeviceInfo(int seqNo, String clientOS, String osver, String device, String fcmToken, String extra,
                                String country, String lang, int utcOffset)
            throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(clientOS, os);
        writeString(osver, os);
        writeString(device, os);
        writeString(fcmToken, os);
        writeString(country, os);
        writeString(lang, os);
        os.writeInt(utcOffset);
        writeString(extra, os);
        writeCommand(CMD_SET_DEVICE_INFO, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public HashMap<String, String> readDeviceInfo(byte[] data) throws IOException {
        HashMap<String, String> m = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        m.put("os", readString(is));
        m.put("os_ver", readString(is));
        m.put("device", readString(is));
        m.put("token", readString(is));
        if (PROTOCOL_MAJOR != 1 || clientProtocolMinor >= 4) { // since 1.4 lang
            m.put("country", readString(is));
            m.put("lang", readString(is));
            m.put("utc_offset", String.valueOf(is.readInt()));
        }
        m.put("extra", readString(is));

        return m;
    }

    public void getActionsReponse(RawCmd request, LinkedHashMap<String, Action> actions) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (actions == null || actions.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(actions.size());
            for (Iterator<Entry<String, Action>> it = actions.entrySet().iterator(); it.hasNext();) {
                Entry<String, Action> e = it.next();
                writeString(e.getKey(), os);
                writeString(e.getValue().topic, os);
                writeString(e.getValue().content, os);
                os.writeBoolean(e.getValue().retain);
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public LinkedHashMap<String, Action> readActions(byte[] data) throws IOException {
        LinkedHashMap<String, Action> actions = new LinkedHashMap<>();
        DataInputStream is = getDataInputStream(data);
        int size = is.readUnsignedShort();
        String key;
        Action a;
        for (int i = 0; i < size; i++) {
            a = new Action();
            key = readString(is);
            a.topic = readString(is);
            a.content = readString(is);
            a.retain = is.readBoolean();
            actions.put(key, a);
        }
        return actions;
    }

    public Map<String, String> readActionData(int cmd, byte[] data, Ref<byte[]> rawContent) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        if (cmd == CMD_UPD_ACTION) {
            map.put("prev_actionname", readString(is));
        }
        if (cmd != CMD_MQTT_PUBLISH) {
            map.put("actionname", readString(is));
        }
        map.put("topic", readString(is));
        rawContent.value = readByteArray(is);
        map.put("retain", is.readBoolean() ? "true" : "false");
        return map;
    }

    public RawCmd addActionRequest(int seqNo, String actioName, Action a) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(actioName, os);
        writeString(a.topic, os);
        writeString(a.content, os);
        os.writeBoolean(a.retain);
        writeCommand(CMD_ADD_ACTION, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd setDashboardRequest(int seqNo, long version, int itemID, String dashboardJson) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(version);
        os.writeInt(itemID);
        writeString(dashboardJson, os, false);
        writeCommand(CMD_SET_DASHBOARD, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public void setDashboardResonse(Cmd.RawCmd cmd, long version) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(version);
        writeCommand(cmd.command, cmd.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());

    }

    public void getDashBoardResponse(Cmd.RawCmd cmd, long version, String dashboard) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(version);
        if (PROTOCOL_MAJOR != 1 || clientProtocolMinor >= 6) { // since 1.6 write p_string32
            writeLongString(dashboard, os);
        } else {
            writeString(dashboard, os);
        }
        writeCommand(cmd.command, cmd.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public Object[] readDashboardData(byte[] data) throws IOException {
        Object[] result  = new Object[2];
        DataInputStream is = getDataInputStream(data);
        result[0] = is.readLong();
        if (PROTOCOL_MAJOR != 1 || clientProtocolMinor >= 6) { // since 1.6 read p_string32
            result[1] = readLongString(is);
        } else {
            result[1] = readString(is);
        }
        return result;
    }

    public RawCmd mqttPublishRequest(int seqNo, String topic, byte[] content, boolean retain) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(topic, os);
        writeByteArray(content, os);
        os.writeBoolean(retain);
        writeCommand(CMD_MQTT_PUBLISH, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd updateActionRequest(int seqNo, String oldActionName, String actioName, Action a) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(oldActionName, os);
        writeString(actioName, os);
        writeString(a.topic, os);
        writeString(a.content, os);
        os.writeBoolean(a.retain);
        writeCommand(CMD_UPD_ACTION, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd deleteActionsRequest(int seqNo, List<String> actions) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (actions == null || actions.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(actions.size());
            for (int i = 0; i < actions.size(); i++) {
                writeString(actions.get(i), os);
            }
        }
        writeCommand(CMD_DEL_ACTIONS, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public LinkedHashMap<String, Topic> readTopics(byte[] data) throws IOException {
        LinkedHashMap<String, Topic> subs = new LinkedHashMap<>();
        DataInputStream is = getDataInputStream(data);
        int size = is.readUnsignedShort();
        String topic;
        Topic rec;
        for (int i = 0; i < size; i++) {
            rec = new Topic();
            topic = readString(is);
            rec.type = is.read();
            if (PROTOCOL_MAJOR != 1 || clientProtocolMinor > 4) { // since 1.5: script
                rec.script = readString(is);
            }
            subs.put(topic, rec);
        }
        return subs;
    }

    public List<String> readStringList(byte[] data) throws IOException {
        ArrayList<String> subs = new ArrayList<>();
        DataInputStream is = getDataInputStream(data);
        int size = is.readUnsignedShort();
        for (int i = 0; i < size; i++) {
            subs.add(readString(is));
        }
        return subs;
    }

    public DataInputStream getDataInputStream(byte[] data) throws IOException {
        ByteArrayInputStream bi = new ByteArrayInputStream(data);
        return new DataInputStream(bi);
    }

    public RawCmd addTopicsRequest(int seqNo, LinkedHashMap<String, Topic> topics) throws IOException {
        return writeTopics(CMD_ADD_TOPICS, seqNo, topics);
    }

    public void getTopicsResponse(RawCmd request, Map<String, Topic> topics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (topics == null || topics.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(topics.size());
            for (Iterator<Entry<String, Topic>> it = topics.entrySet().iterator(); it.hasNext();) {
                Entry<String, Topic> e = it.next();
                writeString(e.getKey(), os);
                os.writeByte(e.getValue().type);
                if (Cmd.PROTOCOL_MAJOR != 1 || clientProtocolMinor > 4) { // since 1.5: script
                    writeString(e.getValue().script, os);
                }
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public RawCmd getCachedMessagesRequest(int cmd, int seqNo, long since, int seqCnt) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(since);
        os.writeInt(seqCnt);
        writeCommand(cmd, seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
        return readCommand();
    }

    public void getCachedMessagesResponse(RawCmd request, List<Object[]> messages) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (messages == null || messages.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(messages.size());
            for(Object[] msg : messages) {
                if (msg.length >= 4) {
                    os.writeLong((Long) msg[0]);
                    writeString((String) msg[1], os);
                    byte[] b = (byte[]) msg[2];
                    if (b == null || b.length == 0) {
                        os.writeShort(0);
                    } else {
                        os.writeShort(b.length);
                        os.write(b);
                    }
                    os.writeInt((Integer) msg[3]);
                }
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public void getCachedDashMessagesResponse(RawCmd request, long version, List<Object[]> messages, Map<String, Integer> dashboardTopics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(version);
        if (messages == null || messages.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(messages.size());
            String topic;
            if (dashboardTopics == null) {
                dashboardTopics = new HashMap<>();
            }
            for(Object[] msg : messages) {
                if (msg.length >= 4) {
                    os.writeLong((Long) msg[0]);
                    topic = (String) msg[1];
                    writeString(topic, os);
                    byte[] b = (byte[]) msg[2];
                    if (b == null || b.length == 0) {
                        os.writeShort(0);
                    } else {
                        os.writeShort(b.length);
                        os.write(b);
                    }
                    os.writeInt((Integer) msg[3]);
                    os.writeShort(dashboardTopics.containsKey(topic) ? dashboardTopics.get(topic) : 0);
                }
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public long readCachedMessageDashboard(byte[] data, List<Object[]> messages) throws IOException {
        DataInputStream is = getDataInputStream(data);
        long version = is.readLong();

        int len = is.readShort();
        if (len > 0) {
            for(int i = 0; i < len; i++) {
                Object[] o = new Object[6];
                o[0] = is.readLong();
                o[1] = readString(is);
                o[2] = readByteArray(is);
                o[3] = is.readInt();
                o[4] = is.readShort();
                messages.add(o);
            }
        }

        return version;
    }

    public List<Object[]> readCachedMessages(byte[] data)  throws IOException {
        List<Object[]> messages = new ArrayList<Object[]>();
        DataInputStream is = getDataInputStream(data);
        int len = is.readShort();
        if (len > 0) {
            for(int i = 0; i < len; i++) {
                Object[] o = new Object[4];
                o[0] = is.readLong();
                o[1] = readString(is);
                o[2] = readByteArray(is);
                o[3] = is.readInt();
                messages.add(o);
            }
        }
        return messages;
    }

    public RawCmd updateTopicsRequest(int seqNo, LinkedHashMap<String, Topic> topics) throws IOException {
        return writeTopics(CMD_UPD_TOPICS, seqNo, topics);
    }

    public RawCmd writeTopics(int cmd, int seqNo, LinkedHashMap<String, Topic> topics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (topics == null || topics.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(topics.size());
            Topic val;
            for (Iterator<Entry<String, Topic>> it = topics.entrySet().iterator(); it.hasNext();) {
                Entry<String, Topic> e = it.next();
                writeString(e.getKey(), os);
                val = e.getValue();
                os.writeByte(val.type);
                writeString(val.script, os);
            }
        }
        writeCommand(cmd, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd deleteTopicsRequest(int seqNo, List<String> topics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (topics == null || topics.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(topics.size());
            for (int i = 0; i < topics.size(); i++) {
                writeString(topics.get(i), os);
            }
        }
        writeCommand(CMD_DEL_TOPICS, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public void intArrayResponse(RawCmd request, int[] results) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (results == null || results.length == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(results.length);
            for (int i = 0; i < results.length; i++) {
                os.writeShort(results[i]);
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public int[] readIntArray(byte[] data) throws IOException {
        int rc[];
        DataInputStream is = getDataInputStream(data);
        rc = new int[is.readUnsignedShort()];
        for (int i = 0; i < rc.length; i++) {
            rc[i] = is.readShort();
        }
        return rc;
    }

    public RawCmd request(int cmd, int seq) throws IOException {
        writeCommand(cmd, seq, FLAG_REQUEST, 0, new byte[0]);
        return readCommand();
    }

    public void response(RawCmd request, int rc) throws IOException {
        response(request, new byte[0], rc);
    }

    public void response(RawCmd request, byte[] data, int rc) throws IOException {
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, rc, data);
    }

    protected void writeCommandStrPara(int cmd, int seqNo, String arg) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(arg, os);
        writeCommand(cmd, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
    }

    public String readStringPara(byte[] data) throws IOException {
        return readString(getDataInputStream(data));
    }

    /* basic read functions */

    public RawCmd readCommand() throws EOFException, IOException {
        RawCmd cmd = new RawCmd();
        byte[] magic = new byte[4];

        // read header
        byte[] h = new byte[HEADER_SIZE];
        dis.readFully(h);
        DataInputStream di = new DataInputStream(new ByteArrayInputStream(h));

        di.readFully(magic);
        String magicx = new String(magic, "US-ASCII");
        if (!magicx.toUpperCase().equals(MAGIC)) {
            throw new IOException("Invalid header");
        }

        cmd.command = di.readUnsignedShort();
        cmd.seqNo = di.readUnsignedShort();
        cmd.flags = di.readUnsignedShort();
        cmd.rc = di.readUnsignedShort();
        int len = di.readInt();

        boolean loadDataPart = true;

        /* if blobs are attached, do not load data here */
        if (cmd.command == CMD_SAVE_RESOURCE && (cmd.flags & FLAG_RESPONSE) == 0) {
            loadDataPart = false;
        } else if ( cmd.command == CMD_GET_RESOURCE && (cmd.flags & FLAG_RESPONSE) > 0) {
            loadDataPart = false;
        }

        if (!loadDataPart) {
            if (len > MAX_PAYLOAD_RESOURCE) {
                throw new IOException("Invalid content length");
            }
            len = 0;
        }

        if (len > MAX_PAYLOAD)
            throw new IOException("Invalid content length");
        cmd.data = new byte[len];
        dis.readFully(cmd.data);
        return cmd;
    }

    /* basic write functions */

    public void writeHeader(int cmd, int seqNo, int flags, int rc, int contentSize) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(out);
        os.write(MAGIC_BLOCK);
        os.writeShort(cmd);
        os.writeShort(seqNo); // seq no
        os.writeShort(flags);
        os.writeShort(rc); // return code
        os.writeInt(contentSize);
        bos.write(out.toByteArray());
    }

    public void writeContent(byte[] data) throws IOException {
        int offset = 0;
        int blockSize = 0;
        // write data in blocks of BUFFER_SIZE (15K) to prevent write operations > 16K (workaround for TLSv1.3 implementation errors)
        while(offset < data.length) {
            blockSize = Math.min(BUFFER_SIZE, data.length - offset);
            bos.write(data, offset, blockSize);
            offset += blockSize;
        }
        bos.flush();
    }

    public void writeCommand(int cmd, int seqNo, int flags, int rc, byte[] data) throws IOException {
        writeHeader(cmd, seqNo, flags, rc, data.length);
        writeContent(data);
    }

    public DataInputStream getInputStream() {
        return dis;
    }

    public DataOutputStream getOutputStream() {
        return dos;
    }

    public void close() {
        if (dis != null) {
            try {
                dis.close();
            } catch (IOException e) {
            }
        }
        if (dos != null) {
            try {
                dos.close();
            } catch (IOException e) {
            }
        }
        if (bos != null) {
            try {
                bos.close();
            } catch (IOException e) {
            }
        }
    }

    public Cmd(DataInputStream is, DataOutputStream os) {
        dis = is;
        dos = os;
        bos = new BufferedOutputStream(os, 8192);
    }

    public static void writeString(String s, DataOutputStream dos) throws IOException {
        writeString(s, dos, true);
    }

    public static void writeLongString(String s, DataOutputStream dos) throws IOException {
        writeString(s, dos, false);
    }

    protected static void writeString(String s, DataOutputStream dos, boolean lenShort) throws IOException {
        writeByteArray(s == null ? null : s.getBytes("UTF-8"), dos, lenShort);
    }

    public static String readString(DataInputStream dis) throws IOException {
        return readString(dis, true);
    }

    public static String readLongString(DataInputStream dis) throws IOException {
        return readString(dis, false);
    }

    protected static String readString(DataInputStream dis, boolean lenShort) throws IOException {
        return new String(readByteArray(dis, lenShort), "UTF-8");
    }

    public static byte[] readByteArray(DataInputStream dis)  throws IOException {
        return readByteArray(dis, true);
    }

    public static byte[] readByteArray(DataInputStream dis, boolean lenShort) throws IOException {
        byte[] b;
        int len;
        if (lenShort) {
            len = dis.readUnsignedShort();
        } else {
            len = dis.readInt();
        }
        if (len == 0 || len == -1) { // len == -1 was pre 1.2
            b = new byte[0];
        } else if (len > 0 && len <= MAX_PAYLOAD ) { // string len cannot be larger than max payload
            b = new byte[len];
            dis.readFully(b);
        } else {
            throw new IOException("Invalid string len");
        }
        return b;

    }

    public static void writeByteArray(byte[] b, DataOutputStream dos) throws IOException {
        writeByteArray(b, dos, true);
    }

    public static void writeByteArray(byte[] b, DataOutputStream dos, boolean lenShort) throws IOException {
        if (b == null || b.length == 0) {
            if (lenShort) {
                dos.writeShort(0);
            } else {
                dos.writeInt(0);
            }
        } else {
            if (lenShort) {
                dos.writeShort(b.length);
            } else {
                dos.writeInt(b.length);
            }
            dos.write(b);
        }
    }

    /** converts char array to UTF-8 byte array */
    public static byte[] toByteArray(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
        // Arrays.fill(charBuffer.array(), '\u0000'); // clear sensitive data
        Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
        return bytes;
    }

    /** converts UTF-8 byte array to char array */
    public static char[] toCharArray(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = Charset.forName("UTF-8").decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
        Arrays.fill(charBuffer.array(), '\u0000'); // clear sensitive data
        Arrays.fill(byteBuffer.array(), (byte) 0); // clear sensitive data
        return chars;
    }

    public BufferedOutputStream getBufferedOutputStream() {
        return bos;
    }

    protected DataInputStream dis;
    protected DataOutputStream dos;
    protected BufferedOutputStream bos;

    public static class RawCmd {
        public int command;
        public int seqNo;
        public int flags;
        public int rc;
        public byte[] data;
    }

    public static class Action {
        public String topic;
        public String content;
        public boolean retain;

        public final static int OK = 0;
        public final static int ERR_NOT_FOUND_EX = 1; // add: key already exists, update: entry not found
        public final static int ERR_INVALID_FORMAT = 2;
    }

    public static class Topic {
        public int type;
        public String script;
    }

    public static class FileInfo {
        public String name;
        public long mdate;
    }

    public static class Ref<T> {
        public T value;
    }

    /* return codes */
    public final static int RC_OK = 0;
    public final static int RC_INVALID_ARGS = 400;
    public final static int RC_NOT_AUTHORIZED = 401;
    public final static int RC_INVALID_PROTOCOL = 403;
    public final static int RC_SERVER_ERROR = 500;
    public final static int RC_MQTT_ERROR = 503;

    public final static int FLAG_REQUEST = 0;
    public final static int FLAG_RESPONSE = 1;
    public final static int FLAG_SSL = 2;
    public final static int FLAG_ADM = 4;

    /* protocol */
    public final static String MAGIC = "MQTP";
    public final static byte PROTOCOL_MAJOR = 1;
    public final static byte PROTOCOL_MINOR = 6;
    public final static int MAGIC_SIZE = 4;
    public final static byte[] MAGIC_BLOCK;

    /* file types */
    public final static String DASH512_PNG =  "dash512png";
    public final static String DASH_HTML =  "dashhtml";

    public byte clientProtocolMinor;

    public final static int MAX_TOPICS_SIZE = 65536;
    public final static int MAX_STRING_SIZE = MAX_TOPICS_SIZE;
    public final static int MAX_PAYLOAD = 1024 * 256;
    public final static long MAX_PAYLOAD_RESOURCE = MAX_PAYLOAD * 10L;
    public static int BUFFER_SIZE = 1024 * 15;
    static {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        try {
            bao.write(MAGIC.getBytes("US-ASCII"));
        } catch (IOException e) {
        }
        MAGIC_BLOCK = bao.toByteArray();
    }
    public final static int HEADER_SIZE = 16;

}
