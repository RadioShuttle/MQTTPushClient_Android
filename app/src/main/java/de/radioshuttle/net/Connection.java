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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.Utils;

public class Connection {
    public Connection(String pushServer, Context context) {
        mPushServer = pushServer;
        mContext = context.getApplicationContext();
    }

    public void connect() throws IOException, InterruptedException {
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

        mClientSocket = new Socket();
        mClientSocket.connect(new InetSocketAddress(pushserverArray[0], port), CONNECT_TIMEOUT);
        mClientSocket.setSoTimeout(READ_TIMEOUT);

        mCmd = new Cmd(
                new DataInputStream(mClientSocket.getInputStream()),
                new DataOutputStream(mClientSocket.getOutputStream()));

        Cmd.RawCmd reponse = mCmd.helloRequest(++mSeqNo);

        if (reponse.rc == Cmd.RC_INVALID_PROTOCOL) {
            throw new IncompatibleProtocolException(Cmd.PROTOCOL_MAJOR, Cmd.PROTOCOL_MINOR,
                    reponse.data[0], reponse.data[1], mContext);
        }
    }

    public void login(PushAccount b) throws IOException, ServerError, InterruptedException {
        Cmd.RawCmd response = mCmd.loginRequest(++mSeqNo, b.uri, b.user, b.password);

        handleError(response);
    }

    public Map<String, String> getFCMData() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.fcmDataRequest(++mSeqNo);
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

        Cmd.RawCmd response = mCmd.setDeviceInfo(++mSeqNo,
                "Android", String.valueOf(Build.VERSION.SDK_INT), deviceInfo, fcmToken, null );
        handleError(response);
    }

    public void removeFCMToken(String token) throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.removeFCMTokenRequest(++mSeqNo, token);
        handleError(response);
    }

    public LinkedHashMap<String, Integer> getTopics() throws IOException, ServerError {
        Cmd.RawCmd response = mCmd.request(Cmd.CMD_GET_SUBSCR, ++mSeqNo);
        handleError(response);
        LinkedHashMap<String, Integer> topics;
        if (lastReturnCode == Cmd.RC_OK) {
            topics = mCmd.readTopics(response.data);
        } else {
            topics = null;
        }

        return topics;
    }

    public int[] addTopics(Map<String, Integer> topics) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.subscribeRequest(++mSeqNo, topics);
        handleError(response);
        return mCmd.readSubscriptionUpdateResult(response.data);
    }

    public int[] deleteTopics(List<String> topics) throws IOException, ServerError  {
        Cmd.RawCmd response = mCmd.unsubscribeRequest(++mSeqNo, topics);
        handleError(response);
        return mCmd.readSubscriptionUpdateResult(response.data);
    }

    public void bye() throws IOException {
        mCmd.writeCommand(Cmd.CMD_BYE, ++mSeqNo, Cmd.FLAG_REQUEST, 0, new byte[0]);
    }

    /*
    public void getSubstricptions() throws IOException {
        mCmd.request(Cmd.CMD_SUBSCRPTIONS);
    }
    */

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

            if (response.rc == Cmd.RC_MQTT_ERROR) {
                throw new MQTTException(errorCode, errorTxt);
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

}
