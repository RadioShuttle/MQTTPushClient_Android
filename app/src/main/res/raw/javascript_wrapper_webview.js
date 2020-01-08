MQTT.view = MQTT.getView(),
MQTT.view.setUserData = function(data) {
  var jsonStr = JSON.stringify(data);
  if (jsonStr.length > 1048576) {
    throw "User data is limited to 1 MB.";
  }
  this._setUserData(JSON.stringify(data));
};
MQTT.view.getUserData = function() {
  var data = this._getUserData();
  return data ? JSON.parse(data) : null;
};
MQTT.view.getHistoricalData = function() {
  var result = null;
  var data = this._getHistoricalData();
  if (data) {
    result = JSON.parse(data);
    for(var i = 0; i < result.length; i++) {
      result[i].receivedDate = new Date(result[i]._received);
      result[i].raw = MQTT.hex2buf(result[i]._raw);
    }
  }
  return result;
};

MQTT.buf2hex = function (buffer) {
  var byteArray = new Uint8Array(buffer);
  var hexStr = '';
  for(var i = 0; i < byteArray.length; i++) {
    var hex = byteArray[i].toString(16);
    var paddedHex = ('00' + hex).slice(-2);
    hexStr += paddedHex;
  }
  return hexStr;
};

MQTT.hex2buf = function (hex) {
  if (!hex) {
    hex = '';
  }
  if (hex.length % 2 == 1) {
    hex = '0' + hex;
  }
  var buf = new ArrayBuffer(hex.length / 2);
  var dv = new DataView(buf);
  var j = 0;
  for(var i = 0; i < hex.length; i += 2) {
    dv.setUint8(j, parseInt(hex.substring(i, i + 2), 16));
    j++;
  }
  return buf;
};

MQTT.publish = function (topic, msg, retain) {
    var requestStarted = false;
    if (typeof msg === 'string') {
        requestStarted = MQTT._publishStr(topic, msg, retain === true);
    } else if (msg instanceof ArrayBuffer) {
        requestStarted = MQTT._publishHex(topic, MQTT.buf2hex(msg), retain === true);
    } else {
        throw "MQTT.publish(): arg msg must be of type String or ArrayBuffer";
    }
    return requestStarted;
};

function _onMqttMessage(receivedDateMillis, topic, payloadStr, payloadHEX) {
    var msg = new Object();
    if (!payloadHEX) payloadHEX = '';
    if (!payloadStr) payloadStr = '';
    msg.receivedDate = new Date(Number(receivedDateMillis));
    msg.topic = topic;
    msg.text = payloadStr;
    msg.raw = MQTT.hex2buf(payloadHEX);
    onMqttMessage(msg);
}
