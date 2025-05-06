package com.dzboot.ovpn.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.helpers.NotificationsHelper
import com.dzboot.ovpn.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber


class NotificationActionBroadcast : BroadcastReceiver() {

    companion object {
        const val NOTIFICATION_ID = "notif_id"
        const val DELETE_NOTIFICATION_ACTION = "delete_notification_action"
        const val DISMISS_NOTIFICATION_ACTION = "dismiss_notification_action"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Timber.d("NotificationAction received")

        when (intent?.action) {
            DELETE_NOTIFICATION_ACTION -> {
                val id = intent.getLongExtra(NOTIFICATION_ID, -1)
                CoroutineScope(Dispatchers.IO).launch {
                    AppDB.getInstance(context).notificationsDao().deleteNotification(id)
                }
                LocalBroadcastManager.getInstance(context)
                    .sendBroadcast(Intent(MainActivity.MESSAGING_NOTIFICATION_ACTION))
                NotificationManagerCompat.from(context).cancel(id.toInt())
            }
            DISMISS_NOTIFICATION_ACTION -> {
                NotificationsHelper.showPersistentNotification(context, MainActivity::class.java)
            }
        }
    }
}