function publish(topic, msg, retain) {
    var buf = null;

    if (typeof msg === 'string') {
        PushApp.publish(topic, msg, retain);
    } else if (msg instanceof ArrayBuffer) {
        /* TODO */
        /* _publishBase64(); */
        throw "publish(): arg msg of type ArrayBuffer not supported yet";
    } else {
        throw "publish(): arg msg must be of type String or ArrayBuffer";
    }
}

function _onUpdate(receivedDateMillis, topic, payloadStr, payloadBase64) {
    var msg = new Object();
    if (!payloadBase64) payloadBase64 = '';
    if (!payloadStr) payloadStr = '';
    msg.receivedDate = new Date(Number(receivedDateMillis));
    msg.topic = topic;
    msg.text = payloadStr;
    /* msg.raw = _base64DecToArr(payloadBase64); */ /* TODO */
    onUpdate(msg);
}

/* TODO: https://developer.mozilla.org/en-US/docs/Web/API/WindowBase64/Base64_encoding_and_decoding */
