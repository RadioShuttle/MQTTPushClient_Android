view.setUserData = function(data) {
  var jsonStr = JSON.stringify(data);
  if (jsonStr.length > 1048576) {
    throw "User data is limited to 1 MB.";
  }
  this._setUserData(JSON.stringify(data));
};

view.getUserData = function() {
  var data = this._getUserData();
  return data ? JSON.parse(data) : null;
};
