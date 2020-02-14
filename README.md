![Title image](/docs/title_image.png)

# RadioShuttle MQTT Push Client app for Android

This app allows you to receive push notifications from MQTT environments. The app communicates with the public MQTT push server (radioshuttle.de), which in turn connects to an MQTT server and sends messages via the push services of Apple or Google. The app allows the configuration of push notifications for defined MQTT topics, which are automatically monitored by the MQTT push server. In the case of activity, these are immediately forwarded to the app, which outputs the message as a push notification. Push notifications are delivered automatically even if the app is not running.

In addition to receiving push notifications for registered topics, MQTT actions can also be defined to send an MQTT message that can trigger an action on the recipients.

The app offers the following application sections:

* Message view of all messages (push messages or simple messages)
* Actions for sending defined messages (menu with MQTT actions)
* Dashboard view

The dashboard view has a graphical user interface in which display and control elements (“dashes”) can be created. These dashes can be used to monitor and control any actions, such as lighting, temperature display, weather, light color, heating control, home automation, to name just a few possibilities. Dash controls offer switches, sliders, text display, selection lists and web views, which are displayed as groupable interactive tiles.

## Prerequisites to test drive app
![App icon](/docs/app_icon.png)

The app can be downloaded from the app store (_Google Play Store_ or _Apple App Store_) which is the easiest way to test drive it. This app is designed as an addition to an existing MQTT environment and provides an easy-to-use interface with the ability to receive MQTT messages as push notifications.


Required MQTT server:

* Access to an MQTT server that manages the messages you want to receive or send using the app, as well as the corresponding user name and password
* Devices or sensors for MQTT message exchange
* Information about the MQTT topics used and their message content (message structure)

If no own MQTT server is available, a public MQTT server, for example from Arduino Hannover, can be used. At mqtt.arduino-hannover.de an account for the MQTT server can be created.

The MQTT push server “push.radioshuttle.de” is already preset when setting up a new server connection and can be used with any MQTT server.


## Supported Android devices
We started to support fairly old Android versions to allow almost every device to be used with this MQTT Push Client app, however latest Android versions are likely to work fine.
* Minimum version: Android 4.1
* Current target version: Android 10


## License and contributions
The software is provided under the [Apache 2.0 license](docs/LICENSE-apache-2.0.txt). Contributions to this project are accepted under the same license.

## Development prerequisites
* Android Studio 3.5.3 or later
* Java programming language skills
* Android development experience

### Getting Started
* Install Android Studio
* Check out (clone) this project
* Import the project in Android Studio: 
  * Start Android Studio and select “Import project”
  * Select folder containing the main build file (build.gradle)

For a first impression:
* Set up an emulator instance or connect a device via USB to run the application

## Publish an own app on the Google Store
* Set an application ID:
  * Open app/build.gradle (Module: app) and set your own unique application ID, the default   “de.radioshuttle.mqttpushclient” must not be used for own projects.
* Create a signing configuration

To upload the app to the Google Play Store, the app/bundle must be signed. See https://developer.android.com/studio/publish/app-signing

In app/build.gradle replace:
<pre>
if(project.hasProperty("MQTTPushClient.signing") {
...
}
</pre>
The custom signing configuration must be adjusted (the code to be replaced references an external 
file containing the radioshuttle.de signing configuration, which is not part of this 
distribution). 

Please delete the line …
<pre>
MQTTPushClient.signing=/helios/release/Android/MQTTPushClient/
</pre>
… in gradle.properties or replace the value with the location of your own
signing configuration file.


## RadioShuttle MQTT push server
This app requires communication with the RadioShuttle MQTT push server. For non-commercial users, the use of the public push server (push.radioshuttle.de) is currently free of charge up to 3 mobile devices and 3 MQTT accounts.
RadioShuttle licensees, i.e. RadioShuttle board customers can permanently benefit from this service free of charge.

Unlimited commercial use of the RadioShuttle MQTT push server software for operation on your own servers is available for an annual software rental with support included. A own deployment of the RadioShuttle MQTT push server (server written in Java) requires Apple or Google push certificates.

## MQTT push solution background
Apps that are permanently polling connections from mobile apps to MQTT servers do not work due to their high energy requirement and constant mobile network changes. Users wish to receive messages on their mobile devices, whether or not the app is running. Even when the mobile device is turned off, or there is no internet connection, messages should arrive automatically once the device is online again.

The RadioShuttle MQTT push solution implements this via the RadioShuttle MQTT push server and its corresponding MQTT Push Client apps for Android and iOS. The app communicates via the MQTT push server only. The MQTT push server monitors the MQTT messages for the specified accounts and sends push messages via Google (Android) and Apple (iOS) to corresponding mobile devices of this account. In addition, the server keeps the last 100 MQTT push messages for each account.

The mobile device receives and displays push messages even if the app is not running. Once the app is started it will display received messages in the app message view, at the same time it will update the latest messages from MQTT push server to ensure that it is up to date.

The dash view will not connect to the MQTT server for the dash gallery view, instead it will communicate with the MQTT push server only. The MQTT push server remembers the latest message for each registered topic, therefore the display should represent the latest data (e.g. lights on or off, temperature, etc.).

The entire solution is highly optimized for great performance and reliable push messages without any polling. As push messages are being processed by Google and Apple, the amount of push messages per day should be limited to a reasonable number per account (e.g. 50 messages per day). The MQTT push server will limit the push messages per account to not more than one message within 20 seconds. In case of too many messages, it will delay push messages to avoid spamming.

## Security
The communication between the app and the MQTT push server is SSL encrypted. The MQTT server credentials (username and password) are locally stored in the app data, which are secured by the mobile operating system. The MQTT server credentials are also transferred to the MQTT push server to be used for the communication with the MQTT server. The MQTT push server saves the credentials and its configuration encrypted to ensure that noone (even not admins) can access these.

After an account is removed within the app on all authorized devices, the account on the MQTT push server will be removed as well.

Received MQTT messages are stored encrypted on the MQTT push server.

The MQTT push server can be licensed (subscription based) to be deployed on own servers and used with a own version of the MQTT push client app. This would allow deploying it completely independent of RadioShuttle.de, however developer certificates from Google and Android are required for sending push messages.

The server operator of the MQTT push server can send direct messages to the app, to inform users about errors or other information.

## Credits
This app has initially been written by the RadioShuttle engineers (www.radioshuttle.de). Many thanks to everyone who helped to bring this project forward.

## Acknowledgements
We used external resources, libraries and code, many thanks to:
* Google Material Icons: https://material.io/resources/icons/
* Duktape JavaScript Engine: https://duktape.org/
* Canvas clock HTML exmaple: https://developer.mozilla.org/
* Google Charts: https://developers.google.com/chart/interactive/docs/gallery/gauge
