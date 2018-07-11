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

    public PushAccount(PushAccount b) {
        uri = b.uri;
        user = b.user;
        if (b.password != null) {
            Arrays.copyOf(b.password, b.password.length);
        } else {
            b.password = new char[0];
        }
        clientID = b.clientID;
        pushserver = b.pushserver;
        topics = new ArrayList<>();
        if (b.topics != null && b.topics.size() > 0) {
            topics.addAll(b.topics);
        }

        // status = b.status;
        // requestStatus = b.requestStatus;
        // requestErrorCode = b.requestErrorCode;
        // requestErrorTxt = b.requestErrorTxt;
    }

    public String uri;
    public String user;
    public char[] password;
    public String clientID;
    public String pushserver;

    // transient
    public int status;
    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;

    public ArrayList<String> topics;

    public JSONObject getJSONObject() throws JSONException {
        JSONObject broker = new JSONObject();
        broker.put("uri", uri);
        broker.put("user", user);
        if (password == null)
            password = new char[0];
        broker.put("password", new String(password));
        broker.put("clientID", clientID);
        broker.put("pushserver", pushserver);

        JSONArray t = new JSONArray();
        for(String s : topics) {
            t.put(s);
        }
        broker.put("topics", t);
        return broker;
    };

    public JSONObject getJSONState() throws JSONException {
        JSONObject bs = new JSONObject();
        bs.put("key", getKey());
        bs.put("status", status);
        bs.put("requestStatus", requestStatus);
        bs.put("requestErrorCode", requestErrorCode);
        bs.put("requestErrorTxt", requestErrorTxt);
        return bs;
    }

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
                sb.append(':');
                sb.append(u.getPort());
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

    public static PushAccount createBrokerFormJSON(JSONObject o) throws JSONException {
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
        JSONArray t = o.getJSONArray("topics");
        for(int i = 0; i < t.length(); i++) {
            pushAccount.topics.add(t.getString(i));
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

}
