/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
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
        short n = is.readShort();
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
            n = is.readShort();
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
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeShort(errorCode);
        writeString(errorMsg, os);
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, rc, ba.toByteArray());
    }

    public Map<String, Object> readErrorData(byte[] data) throws IOException {
        HashMap<String, Object> m = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        m.put("err_code", is.readShort());
        m.put("err_msg", readString(is));
        return m;
    }

    public RawCmd fcmDataRequest(int seqNo) throws IOException {
        return request(CMD_GET_FCM_DATA, seqNo);
    }

    public void fcmDataResponse(RawCmd request, String app_id, String senderID, String pushServerID) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(app_id, os);
        writeString(senderID, os);
        writeString(pushServerID, os);
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public Map<String, String> readFCMData(byte[] data) throws IOException {
        HashMap<String, String> m = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        m.put("app_id", readString(is));
        m.put("sender_id", readString(is));
        m.put("pushserverid", readString(is));
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

    public Map<String, String> readActionData(int cmd, byte[] data) throws IOException {
        HashMap<String, String> map = new HashMap<>();
        DataInputStream is = getDataInputStream(data);
        if (cmd == CMD_UPD_ACTION) {
            map.put("prev_actionname", readString(is));
        }
        if (cmd != CMD_MQTT_PUBLISH) {
            map.put("actionname", readString(is));
        }
        map.put("topic", readString(is));
        map.put("content", readString(is));
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

    public RawCmd setDashboardRequest(int seqNo, long version, String dashboardJson) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(version);
        writeString(dashboardJson, os);
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
        writeString(dashboard, os);
        writeCommand(cmd.command, cmd.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public Object[] readDashboardData(byte[] data) throws IOException {
        Object[] result  = new Object[2];
        DataInputStream is = getDataInputStream(data);
        result[0] = is.readLong();
        result[1] = readString(is);
        return result;
    }

    public RawCmd mqttPublishRequest(int seqNo, String topic, String content, boolean retain) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(topic, os);
        writeString(content, os);
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

    public RawCmd getCachedMessagesRequest(int seqNo, long since, int seqCnt) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(since);
        os.writeInt(seqCnt);
        writeCommand(CMD_GET_MESSAGES, seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
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

    public void getCachedDashMessagesResponse(RawCmd request, long version, List<Object[]> messages) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        os.writeLong(version);
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

    public long readCachedMessageDashboard(byte[] data, List<Object[]> messages) throws IOException {
        DataInputStream is = getDataInputStream(data);
        long version = is.readLong();
        messages.addAll(readCachedMessages(Arrays.copyOfRange(data, 8, data.length)));
        return version;
    }

    public List<Object[]> readCachedMessages(byte[] data)  throws IOException {
        List<Object[]> messages = new ArrayList<Object[]>();
        DataInputStream is = getDataInputStream(data);
        int len = is.readShort();
        int b = 0;
        if (len > 0) {
            for(int i = 0; i < len; i++) {
                Object[] o = new Object[4];
                o[0] = is.readLong();
                o[1] = readString(is);
                b = is.readUnsignedShort();
                if (b > 0) {
                    byte[] buf = new byte[b];
                    is.readFully(buf);
                    o[2] = buf;
                } else {
                    o[2] = new byte[0];
                }
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
        bos.write(data);
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
        bos = new BufferedOutputStream(os);
    }

    public static void writeString(String s, DataOutputStream dos) throws IOException {
        if (s == null || s.length() == 0) {
            dos.writeShort(0);
        } else {
            byte[] b = s.getBytes("UTF-8");
            dos.writeShort(b.length);
            dos.write(b);
        }
    }

    public static String readString(DataInputStream dis) throws IOException {
        String s;
        short len = dis.readShort();
        if (len == 0 || len == -1) { // len == -1 was pre 1.2
            s = "";
        } else if (len > 0) {
            byte[] b = new byte[len];
            dis.readFully(b);
            s = new String(b, "UTF-8");
        } else {
            throw new IOException("Invalid string len");
        }
        return s;
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
    public final static byte PROTOCOL_MINOR = 5;
    public final static int MAGIC_SIZE = 4;
    public final static byte[] MAGIC_BLOCK;

    public byte clientProtocolMinor;

    public final static int MAX_PAYLOAD = 1024 * 256;
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