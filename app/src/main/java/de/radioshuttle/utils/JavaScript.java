/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

package de.radioshuttle.utils;

import android.app.Application;
import android.util.Base64;
import android.util.Log;

import com.squareup.duktape.Duktape;

import java.io.IOException;
import java.util.HashMap;

import de.radioshuttle.db.MqttMessage;
import de.radioshuttle.mqttpushclient.dash.DColor;
import de.radioshuttle.mqttpushclient.dash.DashBoardJavaScript;

public class JavaScript {

    public static synchronized JavaScript getInstance(Application app) {
        if (js == null) {
            js = new JavaScript(app);
        }
        return js;
    }

    protected JavaScript(Application app) {
        this.app = app;
        try {
            color_js = Utils.getRawStringResource(app, "javascript_color", true);
        } catch (IOException e) {
            Log.d(TAG, "Error loading raw resource: javascript_color.js", e);
        }

    }

    /** formatter - interpreter resources are release after call */
    public String formatMsg(String jsBody, MqttMessage m, int notificationType, String accUser, String accMqttServer, String accPushServer) throws Exception {
        String res = null;
        Context context = initFormatter(jsBody, accUser, accPushServer, accMqttServer);
        initMessageViewProperties(context, null);
        try {
            res = formatMsg(context, m, notificationType);
        } finally {
            context.close();
        }
        return res;
    }

    public String formatMsg(Context context, MqttMessage message, int notificationType) {
        String result = null;
        if (context instanceof DuktapeContext) {
            String payloadBase64 = Base64.encodeToString(message.getPayload(), Base64.DEFAULT);
            String payloadStr = new String(message.getPayload(), Utils.UTF_8);

            result = ((DuktapeContext) context).formatter.formatMsg(
                    String.valueOf(message.getWhen()),
                    message.getTopic(),
                    payloadBase64,
                    payloadStr,
                    notificationType
            );
        }
        return result;
    }

    /** for use in message view to set textColor and backgroundColor */
    public void initMessageViewProperties(Context jsContext, HashMap<String, Object> viewProperties) {
        MessageViewPropertiesImpl viewPropsImpl = new MessageViewPropertiesImpl(viewProperties);
        ((Duktape) jsContext.getInterpreter()).set("view", MessageViewProperties.class, viewPropsImpl);
        if (!Utils.isEmpty(color_js)) {
            ((Duktape) jsContext.getInterpreter()).evaluate(color_js);
        }
    }

    protected interface MessageViewProperties {
        void setBackgroundColor(double color);
        double getBackgroundColor();
        void setTextColor(double color);
        double getTextColor();
    }

    public static class MessageViewPropertiesImpl implements MessageViewProperties {

        public MessageViewPropertiesImpl() {
            this(null);
        }

        public MessageViewPropertiesImpl(HashMap<String, Object> viewProps) {
            p = viewProps;
            if (p == null) {
                p = new HashMap<>();
                setTextColor(DColor.OS_DEFAULT);
                setBackgroundColor(DColor.OS_DEFAULT);
            }
        }

        @Override
        public void setBackgroundColor(double color) {
            // Log.d(TAG, "c: " + color);
            p.put("background", doubleToLong(color));
        }

        @Override
        public double getBackgroundColor() {
            return longToDouble((long) (p.get("background") == null ? 0L : p.get("background")));
        }

        @Override
        public void setTextColor(double color) {
            p.put("textcolor", doubleToLong(color));
        }

        @Override
        public double getTextColor() {
            return longToDouble((long) (p.get("textcolor") == null ? 0L : p.get("textcolor")));
        }

        protected double longToDouble(long i) {
            double v;
            if (i == DColor.CLEAR) {
                v = DColor.CLEAR;
            } else if (i == DColor.OS_DEFAULT) {
                v = DColor.OS_DEFAULT;
            } else {
                v = i & 0xFFFFFFFFL;
            }
            return v;
        }

        protected long doubleToLong(double d) {
            long v;
            if (d == (double) DColor.CLEAR) {
                v = DColor.CLEAR;
            } else if (d == (double) DColor.OS_DEFAULT) {
                v = DColor.OS_DEFAULT;
            } else {
                v = (long) d & 0xFFFFFFFFL;
            }
            return v;
        }

        public HashMap<String, Object> p;
    }

    public Context initFormatter(String jsBody, String accUser, String accMqttServer, String accPushServer) throws Exception {
        DuktapeContext dc = new DuktapeContext();
        try {
            Duktape duktape = dc.getInterpreter();

            StringBuilder sb = new StringBuilder();
            sb.append("var acc = new Object();\n");
            sb.append("acc.user = ");
            sb.append("'");
            if (accUser != null)
                sb.append(accUser);
            sb.append("';\n");

            sb.append("acc.mqttServer = ");
            sb.append("'");
            if (accMqttServer != null)
                sb.append(accMqttServer);
            sb.append("';\n");

            sb.append("acc.pushServer = ");
            sb.append("'");
            if (accPushServer != null)
                sb.append(accPushServer);
            sb.append("';\n");

            //gloabls
            duktape.evaluate(sb.toString());

            duktape.evaluate(""
                    + "var DuktapeMsgFormatter = { "
                    + "  formatMsg: function(receivedDateMillis, topic, payloadBase64, payloadStr, notificationType) { "
                    + "  var msg = new Object();"
                    + "  if (!payloadBase64) payloadBase64 = '';"
                    + "  if (!payloadStr) payloadStr = '';"
                    + "  msg.receivedDate = new Date(Number(receivedDateMillis)); "
                    + "  msg.topic = topic; "
                    + "  msg.text = payloadStr; "
                    + "  msg.content = payloadStr;" // deprecated, for backward comp
                    + "  msg.raw = Duktape.dec('base64', payloadBase64).buffer; "
                    + "  msg.notificationType = notificationType; "
                    + "  var content = payloadStr;"
                    + jsBody + "\n"
                    + "  return content;}"
                    + "};");

            dc.formatter = duktape.get("DuktapeMsgFormatter", DuktapeMsgFormatter.class);


        } catch(Exception e) {
            dc.close();
            throw e;
        }
        return dc;
    }


    public void closeFormatter(Context context) {
        if (context != null) {
            context.close();
        }
    }

    public  interface Context<T> {
        T getInterpreter();
        void close();
    }


    /* implementation specific */

    protected static class DuktapeContext implements Context<Duktape> {

        public DuktapeContext() {
            duktape  = Duktape.create();
        }

        @Override
        public Duktape getInterpreter() {
            return duktape;
        }

        @Override
        public void close() {
            duktape.close();
        }

        public Duktape duktape;
        public DuktapeMsgFormatter formatter;
    }


    protected interface DuktapeMsgFormatter {
        String formatMsg(String receivedDateMillis, String topic, String payloadBase64, String payloadStr, int notificationType);
    }

    private static JavaScript js;
    protected volatile String color_js;
    protected Application app;

    private final static String TAG = JavaScript.class.getSimpleName();

    public static long TIMEOUT_MS = 2000;
}
