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

function _onMqttMessage(receivedDateMillis, topic, payloadStr, payloadBase64) {
    var msg = new Object();
    if (!payloadBase64) payloadBase64 = '';
    if (!payloadStr) payloadStr = '';
    msg.receivedDate = new Date(Number(receivedDateMillis));
    msg.topic = topic;
    msg.text = payloadStr;
    /* msg.raw = _base64DecToArr(payloadBase64); */ /* TODO */
    onMqttMessage(msg);
}

function _onDashboardScriptError(e) {
  var string = e.message.toLowerCase();
  var substring = "script error";
  if (string.indexOf(substring) > -1){
    /* for security reasons there are no detail infos available for external scripts */
    PushApp.log('Script Error (extenal script). No details available.'); /* TODO: call view.setError() if implemented */
  } else {
    var message = [
      'Message: ' + e.message,
      /* 'URL: ' + url, */
      'Line: ' + e.lineno,
      'Column: ' + e.colno,
      'Error object: ' + JSON.stringify(e.error)
    ].join(' - ');
    PushApp.log("Script Error: " + message); /* TODO: call view.setError() if implemented */
  }
  return false;
};
window.addEventListener('error', _onDashboardScriptError);

/* TODO: https://developer.mozilla.org/en-US/docs/Web/API/WindowBase64/Base64_encoding_and_decoding */
