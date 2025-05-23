# FCMfix (Android 10-15)

[![Android CI](https://github.com/kooritea/fcmfix/workflows/Android%20CI/badge.svg)](https://github.com/kooritea/fcmfix/actions)

Let FCM/GCM wake up the unstarted application to send notifications

### Additional features

- Prevent Android system from automatically removing notifications from the notification bar when the app is stopped
- Dynamically remove the auto-start restriction from fcm on MIUI/HyperOS(?)/OxygenOS15(?)/ColorOS15(?)
- Remove notification restrictions on background apps from MIUI/HyperOS
- Send a reminder notification when the target application is not woken up as expected

### lsposed scope
- If there is no problem with push on MIUI/HyperOS, you don't need to check power and performance

### About FCM

FCM is a long link between Google server and GMS application for push notifications maintained by Google in Android.
The general workflow is that the application server sends the message to Google server, Google server pushes the message to GMS application, GMS application passes it to the application through broadcast, and the application decides whether to send notification and notification content based on the received FCM message.
When GMS notifies the application through FCM broadcast, if the application is not running, `Failed to broadcast to stopped app` will appear. FCMfix is mainly to solve this problem.

### Known Issues

- Non-MIUI/HyperOS/OxygenOS15/ColorOS15 systems may need to grant the target application permissions similar to allowing automatic startup and set the battery option to not optimized.
