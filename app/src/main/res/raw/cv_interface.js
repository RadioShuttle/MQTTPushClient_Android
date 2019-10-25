function buf2hex(buffer) {
  var byteArray = new Uint8Array(buffer);
  var hexStr = '';
  for(let i = 0; i < byteArray.length; i++) {
    var hex = byteArray[i].toString(16);
    var paddedHex = ('00' + hex).slice(-2);
    hexStr += paddedHex;
  }
  return hexStr;
}

MQTT.publish = function (topic, msg, retain) {
    if (typeof msg === 'string') {
        MQTT._publishStr(topic, msg, retain === true);
    } else if (msg instanceof ArrayBuffer) {
        MQTT._publishHex(topic, buf2hex(msg), retain === true);
    } else {
        throw "MQTT.publish(): arg msg must be of type String or ArrayBuffer";
    }
};

function _onMqttMessage(receivedDateMillis, topic, payloadStr, payloadBase64) {
    var msg = new Object();
    if (!payloadBase64) payloadBase64 = '';
    if (!payloadStr) payloadStr = '';
    msg.receivedDate = new Date(Number(receivedDateMillis));
    msg.topic = topic;
    msg.text = payloadStr;
    /* msg.raw = _base64DecToArr(payloadBase64); */ /* TODO consider using hex insread of base64 */
    onMqttMessage(msg);
}
