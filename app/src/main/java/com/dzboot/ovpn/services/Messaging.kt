package com.dzboot.ovpn.services

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.models.Notification
import com.dzboot.ovpn.helpers.NotificationsHelper
import com.dzboot.ovpn.activities.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class Messaging : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.data["title"] ?: return
        val body = remoteMessage.data["body"] ?: return

        CoroutineScope(Dispatchers.Default).launch {
            val id = AppDB.getInstance(this@Messaging)
                .notificationsDao()
                .insertNotification(Notification().also {
                    it.title = title
                    it.body = body
                    it.receiveTime = System.currentTimeMillis()
                })

            NotificationsHelper.showPushNotification<MainActivity, NotificationActionBroadcast>(
                this@Messaging, title, body, id
            )
            Timber.d("Notification received. id=$id")
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(MainActivity.MESSAGING_NOTIFICATION_ACTION))
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("Token changed")
    }
}