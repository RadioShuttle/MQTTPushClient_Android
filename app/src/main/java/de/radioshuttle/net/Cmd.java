/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Cmd {

    public final static int CMD_HELLO = 1;
    public final static int CMD_LOGIN = 2;
    public final static int CMD_GET_FCM_DATA = 3;
    public final static int CMD_GET_SUBSCR = 4;
    public final static int CMD_SUBSCRIBE = 5;
    public final static int CMD_UNSUBSCRIBE = 6;
    public final static int CMD_SET_TOKEN = 7;
    public final static int CMD_REMOVE_TOKEN = 8;
    public final static int CMD_LOGOUT = 9;
    public final static int CMD_BYE = 10;

    public RawCmd helloRequest(int seqNo) throws IOException {
        writeCommand(CMD_HELLO, seqNo, FLAG_REQUEST, 0, new byte[] {PROTOCOL_MAJOR, PROTOCOL_MINOR});
        return readCommand();
    }

    public RawCmd loginRequest(int seqNo, String uri, String user, char[] password) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(uri, os);
        writeString(user, os);
        if (password == null)
            password = new char[0];
        os.writeShort(password.length);
        for(int i = 0; i < password.length; i++)
            os.writeChar(password[i]);
        writeCommand(CMD_LOGIN, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public Map<String, Object> readLoginData(byte[] data) throws IOException {
        HashMap<String, Object> m = new HashMap<>();
        ByteArrayInputStream ba = new ByteArrayInputStream(data);
        DataInputStream is = new DataInputStream(ba);
        m.put("uri", readString(is));
        m.put("user", readString(is));
        short n = is.readShort();
        char[] pwd = new char[n];
        for(int i = 0; i < n; i++) {
            pwd[i] = is.readChar();
        }
        m.put("password", pwd);
        return m;
    }

    public void helloResponse(RawCmd requestHeader, int rc) throws IOException {
        writeCommand(CMD_HELLO, requestHeader.seqNo, FLAG_RESPONSE, rc, new byte[] {PROTOCOL_MAJOR, PROTOCOL_MINOR});
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
        ByteArrayInputStream ba = new ByteArrayInputStream(data);
        DataInputStream is = new DataInputStream(ba);
        m.put("err_code", is.readShort());
        m.put("err_msg", readString(is));
        return m;
    }

    public RawCmd fcmDataRequest(int seqNo) throws IOException {
        return request(CMD_GET_FCM_DATA, seqNo);
    }

    public void fcmDataResponse(RawCmd request, String app_id , String api_key) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(app_id, os);
        writeString(api_key, os);
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public Map<String, String> readFCMData(byte[] data) throws IOException {
        HashMap<String, String> m = new HashMap<>();
        ByteArrayInputStream ba = new ByteArrayInputStream(data);
        DataInputStream is = new DataInputStream(ba);
        m.put("app_id", readString(is));
        m.put("api_key", readString(is));
        return m;
    }

    public RawCmd setFcmTokenRequest(int seqNo, String token) throws IOException {
        writeCommandStrPara(CMD_SET_TOKEN, seqNo, token);
        return readCommand();
    }

    public void getSubscriptionsResponse(RawCmd request, List<String> topics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (topics == null || topics.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(topics.size());
            for(int i = 0; i < topics.size(); i++) {
                writeString(topics.get(i), os);
            }
        }
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, 0, ba.toByteArray());
    }

    public ArrayList<String> readTopics(byte[] data)  throws IOException {
        ArrayList<String> subs = new ArrayList<>();
        ByteArrayInputStream ba = new ByteArrayInputStream(data);
        DataInputStream is = new DataInputStream(ba);
        int size = is.readUnsignedShort();
        for(int i = 0; i < size; i++) {
            subs.add(readString(is));
        }
        return subs;
    }

    public RawCmd subscribeRequest(int seqNo, List<String> topics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (topics == null || topics.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(topics.size());
            for(int i = 0; i < topics.size(); i++) {
                writeString(topics.get(i), os);
            }
        }
        writeCommand(CMD_SUBSCRIBE, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd unsubscribeRequest(int seqNo, List<String> topics) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        if (topics == null || topics.size() == 0) {
            os.writeShort(0);
        } else {
            os.writeShort(topics.size());
            for(int i = 0; i < topics.size(); i++) {
                writeString(topics.get(i), os);
            }
        }
        writeCommand(CMD_UNSUBSCRIBE, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
        return readCommand();
    }

    public RawCmd removeFCMTokenRequest(int seqNo, String token) throws IOException {
        writeCommandStrPara(CMD_REMOVE_TOKEN, seqNo, token);
        return readCommand();
    }

    public RawCmd request(int cmd, int seq) throws IOException {
        writeCommand(cmd, seq, FLAG_REQUEST, 0, new byte[0]);
        return readCommand();
    }

    public void response(RawCmd request, int rc) throws IOException {
        writeCommand(request.command, request.seqNo, FLAG_RESPONSE, rc, new byte[0]);
    }

    protected void writeCommandStrPara(int cmd, int seqNo, String arg) throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream();
        DataOutputStream os = new DataOutputStream(ba);
        writeString(arg, os);
        writeCommand(cmd, seqNo, FLAG_REQUEST, 0, ba.toByteArray());
    }

    public String readStringPara(byte[] data) throws IOException {
        ByteArrayInputStream ba = new ByteArrayInputStream(data);
        DataInputStream is = new DataInputStream(ba);
        return readString(is);
    }

    /* basic read functions */

    public RawCmd readCommand() throws EOFException, IOException {
        RawCmd cmd = new RawCmd();
        byte[] magic = new byte[4];
        dis.readFully(magic);
        String magicx = new String(magic, "US-ASCII");
        if (!magicx.toUpperCase().equals(MAGIC)) {
            throw new IOException("Invalid header");
        }

        // read header
        byte[] h = new byte[HEADER_SIZE];
        dis.readFully(h);
        DataInputStream di = new DataInputStream(new ByteArrayInputStream(h));

        cmd.command = di.readUnsignedShort();
        cmd.seqNo = di.readUnsignedShort();
        cmd.flags = di.readUnsignedShort();
        cmd.rc = di.readUnsignedShort();
        int len = di.readInt();
        if (len > 1024)
            throw new IOException("Invalid content length");
        cmd.data = new byte[len];
        dis.readFully(cmd.data);
        return cmd;
    }

    /* basic write functions */

    public void writeHeader(int cmd, int seqNo, int flags, int rc, int contentSize) throws IOException {
        dos.write(MAGIC_BLOCK);
        dos.writeShort(cmd);
        dos.writeShort(seqNo); // seq no
        dos.writeShort(flags);
        dos.writeShort(rc); // return code
        dos.writeInt(contentSize);
    }

    public void writeContent(byte[] data) throws IOException {
        dos.write(data);
        dos.flush();
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
            try { dis.close();} catch (IOException e) {}
        }
        if (dos != null) {
            try { dos.close();} catch (IOException e) {}
        }
    }

    public Cmd(DataInputStream is, DataOutputStream os) {
        dis = is;
        dos = os;
    }


    public static void writeString(String s, DataOutputStream dos) throws IOException {
        if (s == null) {
            dos.writeShort(-1);
        } else if (s.length() == 0) {
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
        if (len == -1) {
            s = null;
        } else if (len == 0) {
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

    protected DataInputStream dis;
    protected DataOutputStream dos;

    public static class RawCmd {
        public int command;
        public int seqNo;
        public int flags;
        public int rc;
        public byte[] data;
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

    /* protocol */
    public final static String MAGIC = "MQTP";
    public final static byte PROTOCOL_MAJOR = 1;
    public final static byte PROTOCOL_MINOR = 0;
    public final static int MAGIC_SIZE = 4;
    public final static byte[] MAGIC_BLOCK;
    static {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        try {
            bao.write(MAGIC.getBytes("US-ASCII"));
        } catch (IOException e) {
        }
        MAGIC_BLOCK = bao.toByteArray();
    }
    public final static int HEADER_SIZE = 12;

}
