/*
 * Copyright (c) 2019 HELIOS Software GmbH
 * 30827 Garbsen (Hannover) Germany
 * Licensed under the Apache License, Version 2.0
 */

if (typeof MQTT === 'undefined') {
  MQTT = new Object();
}

MQTT.Color = {
	rgba : function(r, g, b, a) {
		return ((a & 0x7F) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff)) + (a & 80 ? 0x80000000 : 0);
	},
	rgb : function(r, g, b) {
		return this.rgba(r, g, b, 0xFF);
	},
	red : function(color) {
		return (color >>> 16) & 0xFF;
	},
	green : function(color) {
		return (color >>> 8) & 0xFF;
	},
	blue : function(color) {
		return color & 0xFF;
	},
	alpha : function(color) {
		return (color >>> 24) & 0xFF;
	},

	WHITE : 0xffffffff,
	LT_GRAY : 0xffa0a0a0,
	DK_GRAY : 0xff575757,
	BLACK : 0xff000000,
	TAN : 0xffe9debb,
	YELLOW : 0xffffee33,
	ORANGE : 0xffff9233,
	RED : 0xffad2323,
	BROWN : 0xff814a19,
	LT_GREEN : 0xff81c57a,
	GREEN : 0xff1d6914,
	PINK : 0xffffcdf3,
	PURPLE : 0xff8126c0,
	CYAN : 0xff29d0d0,
	LT_BLUE : 0xff9dafff,
	BLUE : 0xff2a4bd7,
	TRANSPARENT : 0,
	OS_DEFAULT : 0x0100000000,
	CLEAR : 0x0200000000
};
