/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TimeZone;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.utils.Utils;

public class Connection {
    public Connection(String pushServer, Context context) {
        mPushServer = pushServer;
        mContext = context.getApplicationContext();
    }

    public void connect() throws IOException, InterruptedException, InsecureConnection {
        if (mPushServer == null || Utils.isEmpty(mPushServer)) {
            throw new UnknownHostException("No push notification server specified.");
        }
        String[] pushserverArray = mPushServer.split(":");
        int port = DEFAULT_PORT;
        if (pushserverArray.length > 1) {
            try {
                port = Integer.parseInt(pushserverArray[1]);
            } catch(NumberFormatException e) { }
        }

        InetAddress lastValidIP = mLastValidIPMap.get(mPushServer);
        InetAddress[] iaddr = InetAddress.getAllByName(pushserverArray[0]);
        if (lastValidIP != null) {
            boolean found = false;
            for(int i = 0; i < iaddr.length; i++) {
                if (lastValidIP.equals(iaddr[i])) {
                    if (i > 0) {
                        InetAddress tmp = iaddr[0];
                        iaddr[0] = lastValidIP;
                        iaddr[i] = tmp;
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                mLastValidIPMap.remove(mPushServer);
            }
        }

        Socket socket = null;
        for(int i = 0; i < iaddr.length; i++) {
            try {
                // Log.d(TAG, iaddr[i].toString());
                socket = new Socket();
                socket.connect(new InetSocketAddress(iaddr[i], port), CONNECT_TIMEOUT);
                mLastValidIPMap.put(mPushServer, iaddr[i]);
                break;
            } catch(IOException | IllegalArgumentException | SecurityException e) {
                try {
                    if (socket != null)
                        socket.close();
                } catch(IOException e2) {}

                if (i == iaddr.length - 1) {
                    mLastValidIPMap.remove(mPushServer);
                    throw e;
                }
            }
        }

        mClientSocket = socket;
        mClientSocket.setSoTimeout(READ_TIMEOUT);

        mCmd = new Cmd(
                new DataInputStream(mClientSocket.getInputStream()),
                new DataOutputStream(mClientSocket.getOutputStream()));
        mCmd.clientProtocolMinor = Cmd.PROTOCOL_MINOR;


        boolean requestSSL = !debugMode;
        Cmd.RawCmd reponse = mCmd.helloRequest(++mSeqNo, requestSSL);

        if (reponse.rc == Cmd.RC_INVALID_PROTOCOL) {
            throw new IncompatibleProtocolException(Cmd.PROTOCOL_MAJOR, Cmd.PROTOCOL_MINOR,
                    reponse.data[0], reponse.data[1], mContext);
        }

        if (reponse.rc == Cmd.RC_OK) {
            if ((reponse.flags & Cmd.FLAG_SSL) > 0) {
                /* upgrade to ssl */
                SSLSocketFactory sslSocketFactory = null;
                try {
                    sslSocketFactory = (SSLSocketFactory) SSLUtils.getPushServerSSLSocketFactory();
                } catch (Exception e) {
                    Log.e(TAG, "error creating socket factory: ", e);
                    throw new IOException("error creating socket factory", e);
                }

                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(mClientSocket,
                        mClientSocket.getInetAddress().getHostAddress(),
                        mClientSocket.getPort(),
                        true);

                sslSocket.setUseClientMode(true);
                sslSocket.startHandshake();

                Certificate[] certs = sslSocket.getSession().getPeerCertificates();
                if (!AppTrustManager.isValidException((X509Certificate) certs[0])) {
                    HostnameVerifier hv = SSLUtils.getPushServeHostVerifier();
                    if (!hv.verify(mPushServer, sslSocket.getSession())) {
                        throw new HostVerificationError("Invalid host", (X509Certificate[]) certs);
                    }
                }

                mClientSocket = sslSocket;
                mCmd = new Cmd(
                        new DataInputStream(mClientSocket.getInputStream()),
                        new DataOutputStream(mClientSocket.getOutputStream()));
                mCmd.clientProtocolMinor = Cmd.PROTOCOL_MINOR;
            } else if ((reponse.flags & Cmd.FLAG_SSL) == 0) {
                Boolean allow = mInsecureConnection.get(mPushServer);
                if (allow == null || !allow) {
                    throw new InsecureConnection();
                }
            }
        }
    }

    public Cmd.RawCmd login(PushAccount b, String uuid) throws IOException, ServerError, InterruptedException {
        Cmd.RawCmd response = mCmd.loginRequest(++mSeqNo, b.uri, b.user, b.password, uuid);

        handleLoginError(response);
        return response;
    }

    public Map<String, String> getFCMData() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.request(Cmd.CMD_GET_FCM_DATA, ++mSeqNo);
        handleError(response);
        return mCmd.readFCMData(response.data);
    }

    public Map<String, String> getFCMDataIOS() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.request(Cmd.CMD_GET_FCM_DATA_IOS, ++mSeqNo);
        handleError(response);
        return mCmd.readFCMData(response.data);
    }

    final static String deviceInfo;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append(Build.MANUFACTURER);
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        sb.append(' ');
        sb.append(Build.MODEL);
        sb.append(" (Android ");
        sb.append(Build.VERSION.RELEASE);
        sb.append(")");
        deviceInfo = sb.toString();
    }

    public void setDeviceInfo(String fcmToken) throws IOException, ServerError {

        Locale l = Locale.getDefault();
        Cmd.RawCmd response = mCmd.setDeviceInfo(++mSeqNo,
                "Android", String.valueOf(Build.VERSION.SDK_INT), deviceInfo, fcmToken, null,
                l.getCountry(), l.getLanguage(), TimeZone.getDefault().getRawOffset());
        handleError(response);
    }

    public void removeDevice() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.request(Cmd.CMD_REMOVE_DEVICE, ++mSeqNo);
        handleError(response);
    }

    public LinkedHashMap<String, Cmd.Topic> getTopics() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.request(Cmd.CMD_GET_TOPICS, ++mSeqNo);
        handleError(response);
        LinkedHashMap<String, Cmd.Topic> topics;
        if (lastReturnCode == Cmd.RC_OK) {
            topics = mCmd.readTopics(response.data);
        } else {
            topics = null;
        }

        return topics;
    }

    public int[] addTopics(LinkedHashMap<String, Cmd.Topic> topics) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.addTopicsRequest(++mSeqNo, topics);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public int[] updateTopics(LinkedHashMap<String, Cmd.Topic> topics) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.updateTopicsRequest(++mSeqNo, topics);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public int[] deleteTopics(List<String> topics) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.deleteTopicsRequest(++mSeqNo, topics);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public int[] addAction(String actionName, Cmd.Action a) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.addActionRequest(++mSeqNo, actionName, a);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public int[] updateAction(String prevName, String actionName, Cmd.Action a) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.updateActionRequest(++mSeqNo, prevName, actionName, a);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public int[] deleteActions(List<String> actionNames) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.deleteActionsRequest(++mSeqNo, actionNames);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public int[] publish(String topic, byte[] content, boolean retain) throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.mqttPublishRequest(++mSeqNo, topic, content, retain);
        handleError(response);
        return mCmd.readIntArray(response.data);
    }

    public LinkedHashMap<String, Cmd.Action> getActions() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.request(Cmd.CMD_GET_ACTIONS, ++mSeqNo);
        handleError(response);
        LinkedHashMap<String, Cmd.Action> actions;
        if (lastReturnCode == Cmd.RC_OK) {
            actions = mCmd.readActions(response.data);
        } else {
            actions = null;
        }
        return actions;
    }

    public List<Object[]> getCachedMessages(long since, int seqNo)  throws IOException, ServerError {
        List<Object[]> messages = new ArrayList<>();
        Cmd.RawCmd response = mCmd.getCachedMessagesRequest(Cmd.CMD_GET_MESSAGES, ++mSeqNo, since, seqNo);
        handleError(response);
        if (lastReturnCode == Cmd.RC_OK) {
            messages = mCmd.readCachedMessages(response.data);
        }
        return messages;
    }

    public long getCachedMessagesDash(long since, int seqNo, List<Object[]> result) throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.getCachedMessagesRequest(Cmd.CMD_GET_MESSAGES_DASH, ++mSeqNo, since, seqNo);
        handleError(response);
        long version = -1;
        if (lastReturnCode == Cmd.RC_OK) {
            version = mCmd.readCachedMessageDashboard(response.data, result);
        }
        return version;
    }

    public Object[] getDashboard() throws IOException, ServerError {
        Object[] result = null;
        Cmd.RawCmd repoonse = mCmd.request(Cmd.CMD_GET_DASHBOARD, ++mSeqNo);
        handleError(repoonse);
        if (lastReturnCode == Cmd.RC_OK) {
            result = mCmd.readDashboardData(repoonse.data);
        }
        return result;
    }

    public long setDashboardRequest(long version, int itemID, String dashboard) throws IOException, ServerError {
        long result = 0L;
        Cmd.RawCmd repoonse = mCmd.setDashboardRequest(++mSeqNo, version, itemID, dashboard);
        handleError(repoonse);
        if (lastReturnCode == Cmd.RC_OK) {
            DataInputStream is = mCmd.getDataInputStream(repoonse.data);
            result = is.readLong();
        }
        return result;
    }


    public void bye() throws IOException {
        mCmd.writeCommand(Cmd.CMD_DISCONNECT, ++mSeqNo, Cmd.FLAG_REQUEST, 0, new byte[0]);
    }

    public void disconnect() {
        if (mCmd != null) {
            mCmd.close();
        }
        if (mClientSocket != null) {
            try { mClientSocket.close(); } catch (IOException e) { }
        }
    }

    protected void handleError(Cmd.RawCmd response) throws IOException, ServerError {
        lastReturnCode = response.rc;
        int errorCode = 0;
        String errorTxt = "";

        if (response.rc == Cmd.RC_MQTT_ERROR || response.rc == Cmd.RC_SERVER_ERROR) {
            Map<String, Object> m = mCmd.readErrorData(response.data);

            errorCode = (m.containsKey("err_code") ? (short) m.get("err_code") : 0);
            errorTxt = (m.containsKey("err_msg") ? (String) m.get("err_msg") : "");
            //TODO
            /*
            if (errorCode != 0 && Utils.isEmpty(errorTxt)) {
                errorTxt = "error code " + errorCode;
            }
            */

            if (response.rc == Cmd.RC_MQTT_ERROR) {
                throw new MQTTException(errorCode, errorTxt);
            }
            if (response.rc == Cmd.RC_SERVER_ERROR) {
                throw new ServerError(errorCode, errorTxt);
            }
        }
    }

    protected void handleLoginError(Cmd.RawCmd response) throws IOException, ServerError {
        lastReturnCode = response.rc;
        int errorCode = 0;
        String errorTxt = "";

        if (response.rc == Cmd.RC_MQTT_ERROR || response.rc == Cmd.RC_SERVER_ERROR) {

            HashMap<String, Object> m = new HashMap<>();
            DataInputStream is = mCmd.getDataInputStream(response.data);
            m.put("err_code", is.readShort());
            m.put("err_msg", Cmd.readString(is));

            errorCode = (m.containsKey("err_code") ? (short) m.get("err_code") : 0);
            errorTxt = (m.containsKey("err_msg") ? (String) m.get("err_msg") : "");

            if (response.rc == Cmd.RC_MQTT_ERROR) {
                int accountInfo = 0;
                if (is.available() > 0) {
                    accountInfo = is.read();
                }
                throw new MQTTException(errorCode, errorTxt, accountInfo);
            }
            if (response.rc == Cmd.RC_SERVER_ERROR) {
                throw new ServerError(errorCode, errorTxt);
            }
        }
    }

    /* client staus codes (see also CMD_RC_ codes ) */
    public final static int STATUS_UNEXPECTED_ERROR = 1; // unexpected error
    public final static int STATUS_CANCELED = 2;
    public final static int STATUS_CLIENT_ERROR = 3;
    public final static int STATUS_IO_ERROR = 10;
    public final static int STATUS_TIMEOUT = 11;
    public final static int STATUS_CONNECTION_FAILED = 12;
    public final static int STATUS_UNKNOWN_HOST = 13;

    public final static int CONNECT_TIMEOUT = 3000;
    public final static int READ_TIMEOUT = 30000;

    public final static int DEFAULT_PORT = 2033;

    protected Context mContext;
    protected int mSeqNo;
    protected Cmd mCmd;
    protected Socket mClientSocket;
    protected String mPushServer;

    public int lastReturnCode;

    private static ConcurrentHashMap<String, InetAddress> mLastValidIPMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Boolean> mInsecureConnection = new ConcurrentHashMap<>();

    public static volatile boolean debugMode = false;

    private final static String TAG = Connection.class.getSimpleName();
}
