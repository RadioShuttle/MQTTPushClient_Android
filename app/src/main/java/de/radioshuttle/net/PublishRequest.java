/*
 *	$Id$
 *	This is an unpublished work copyright (c) 2018 HELIOS Software GmbH
 *	30827 Garbsen, Germany.
 */

package de.radioshuttle.net;

import android.content.Context;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicLong;

import de.radioshuttle.mqttpushclient.PushAccount;
import de.radioshuttle.mqttpushclient.dash.Item;
import de.radioshuttle.mqttpushclient.dash.Message;

public class PublishRequest extends Request {

    public PublishRequest(Context context, PushAccount pushAccount, MutableLiveData<Request> accountLiveData) {
        super(context, pushAccount, accountLiveData);
        mGetTopicFilterScripts = false;
        mPublishID = PUBLISH_ID.incrementAndGet();
    }

    public void setMessage(String topic, byte[] payload, boolean retain, int itemID) {
        mTopic = topic;
        mPayload = payload;
        mRetain = retain;
        mItemID = itemID;
        mWhen = System.currentTimeMillis();
    }

    @Override
    public boolean perform() throws Exception {
        try {
            int[] rc = mConnection.publish(mTopic, mPayload, mRetain);
        } catch(MQTTException e) {
            requestErrorCode = e.errorCode;
            requestErrorTxt = e.getMessage();
        } catch(ServerError e) {
            requestErrorCode = e.errorCode;
            requestErrorTxt = e.getMessage();
        }
        requestStatus = mConnection.lastReturnCode;

        return true;
    }

    public void setResultDelivered(boolean delivered) {
        mDelivered = delivered;
    }

    public boolean isDelivered() {
        return mDelivered;
    }

    public int getItemID() {
        return mItemID;
    }

    public long getmPublishID() {
        return mPublishID;
    }

    public Message getMessage() {
        Message m = new Message();
        m.setTopic(mTopic);
        m.setPayload(mPayload);
        m.setWhen(mWhen);
        return m;
    }

    public int requestStatus;
    public int requestErrorCode;
    public String requestErrorTxt;
    public String outputScriptError;

    protected long mPublishID; // a unique internal ID used in UI
    protected static AtomicLong PUBLISH_ID = new AtomicLong();


    String mTopic;
    byte[] mPayload;
    boolean mRetain;
    int mItemID;
    boolean mDelivered;
    long mWhen;

}
