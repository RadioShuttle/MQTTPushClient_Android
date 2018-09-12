/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.mqttpushclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;

import de.radioshuttle.net.Connection;

public class PushAccount {
    public PushAccount() {
        topics = new ArrayList<>();
    }

    public String uri;
    public String user;
    public char[] password;
    public String clientID;
    public String pushserver;
    public String pushserverID;

    // transient
    public int status;
    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;
    public int newMessages;

    public volatile ArrayList<Topic> topics;

    public JSONObject getJSONObject() throws JSONException {
        JSONObject account = new JSONObject();
        account.put("uri", uri);
        account.put("user", user);
        if (password == null)
            password = new char[0];
        account.put("password", new String(password));
        account.put("clientID", clientID);
        account.put("pushserver", pushserver);
        account.put("pushserverID", pushserverID == null ? "" : pushserverID);

        return account;
    };

    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        if (user != null && user.trim().length() > 0) {
            sb.append(user);
            sb.append('@');
        }
        if (uri != null && uri.trim().length() > 0) {
            URI u = null;
            try {
                u = new URI(uri);
                sb.append(u.getAuthority());
                /*
                sb.append(':');
                sb.append(u.getPort());
                */
            } catch(Exception e) {
                return "";
            }
        }
        return sb.toString();
    }

    public String getKey() {
        StringBuilder sb = new StringBuilder();
        if (pushserver != null && pushserver.trim().length() > 0) {
            sb.append(pushserver.trim());
            if (pushserver.indexOf(':') == -1) {
                sb.append(':');
                sb.append(Connection.DEFAULT_PORT);
            }
        }
        sb.append('@');
        if (uri != null && uri.trim().length() > 0) {
            URI u = null;
            try {
                u = new URI(uri);
                sb.append(u.getAuthority());
                /*
                sb.append(':');
                sb.append(u.getPort());
                */
            } catch(Exception e) {
                return "";
            }
        }
        sb.append('@');
        if (user != null && user.trim().length() > 0) {
            sb.append(user);
        }
        return sb.toString();
    }

    public String getMqttAccountName() {
        StringBuilder sb = new StringBuilder();
        if (user != null && user.trim().length() > 0) {
            sb.append(user);
        }
        sb.append('@');
        if (uri != null && uri.trim().length() > 0) {
            URI u = null;
            try {
                u = new URI(uri);
                sb.append(u.getAuthority());
            } catch(Exception e) {
                return "";
            }
        }
        return sb.toString();
    }

    public static PushAccount createAccountFormJSON(JSONObject o) throws JSONException {
        PushAccount pushAccount = new PushAccount();
        if (o.has("pushserver")) {
            pushAccount.pushserver = o.getString("pushserver");
        }
        pushAccount.uri = o.getString("uri");
        pushAccount.user = o.getString("user");
        String p = o.getString("password");
        if (p != null) {
            pushAccount.password = p.toCharArray();
        }
        if (o.has("clientID"))
            pushAccount.clientID = o.getString("clientID");
        if (o.has("pushserverID")) {
            pushAccount.pushserverID = o.getString("pushserverID");
        }

        return pushAccount;
    }

    // order by PushServer, MQTT Server, user name
    public static class Comparator implements java.util.Comparator<PushAccount> {

        @Override
        public int compare(PushAccount o1, PushAccount o2) {
            int cmp = 0;
            if (o1 == null) {
                if (o2 == null)
                    cmp = 0;
                else
                    cmp = -1;
            } else if (o2 == null) {
                cmp = 1;
            } else {
                String[] s1 = o1.getKey().split("@");
                String[] s2 = o1.getKey().split("@");
                if (s1.length == 3 && s2.length == 3) {
                    cmp = s1[0].compareTo(s2[0]);
                    if (cmp == 0) {
                        cmp = s1[1].compareTo(s2[1]);
                        if (cmp == 0) {
                            cmp = s1[2].compareTo(s2[2]);
                        }
                    }
                }
            }

            return cmp;
        }
    }

    public final static class Topic {
        public String name;
        public int prio;

        public final static int NOTIFICATION_HIGH = 3;
        public final static int NOTIFICATION_MEDIUM = 2;
        public final static int NOTIFICATION_LOW = 1;
        public final static int NOTIFICATION_DISABLED = 0;
    }

}
