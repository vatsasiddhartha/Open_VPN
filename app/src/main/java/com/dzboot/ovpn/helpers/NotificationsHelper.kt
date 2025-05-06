package com.dzboot.ovpn.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dzboot.ovpn.R
import com.dzboot.ovpn.base.BaseActivity
import com.dzboot.ovpn.data.models.Server
import com.flags.CountryUtils
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnStatus


@SuppressLint("MissingPermission")
object NotificationsHelper {

	private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "persistent_notif"
	const val SHOW_NOTIFICATION_ACTION = "show_notification_action"
	const val STATUS_CHANNEL_ID = "status_notif"
	const val PUSH_NOTIFICATION_CHANNEL_ID = "push_notif"
	const val DELETE_NOTIFICATION_ACTION_CODE = 3
	const val DISMISS_NOTIFICATION_ACTION_CODE = 5
	const val DETAILS_NOTIFICATION_ACTION_CODE = 7
	const val STOP_VPN_ACTION_CODE = 7
	const val DEFAULT_NOTIFICATION_ID = 6
	const val PUSH_NOTIFICATION_ID_KEY = "notif_id"
	const val DELETE_NOTIFICATION_ACTION = "delete_notification_action"
	const val DISMISS_NOTIFICATION_ACTION = "dismiss_notification_action"

	private const val USER_REQ_CHANNEL_ID = "user_req_notif"

	val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
	else
		PendingIntent.FLAG_UPDATE_CURRENT


	fun cancelPersistentNotification(context: Context) {
		if (!VpnStatus.isVPNActive())
			NotificationManagerCompat.from(context).cancel(DEFAULT_NOTIFICATION_ID)
	}

	fun <T : BaseActivity<*>> showPersistentNotification(context: Context, clazz: Class<T>) {
		if (!PrefsHelper.shouldShowPersistentNotif()) {
			NotificationManagerCompat.from(context).cancel(DEFAULT_NOTIFICATION_ID)
			return
		}

		val pIntent = PendingIntent.getActivity(context, 0, Intent(context, clazz), flags)
		NotificationCompat.Builder(context, PERSISTENT_NOTIFICATION_CHANNEL_ID).apply {
			setContentTitle(context.getString(R.string.persistent_notif_title))
			setContentText(context.getString(R.string.persistent_notif_text))
			setSmallIcon(R.drawable.ic_baseline_notifications_24)
			setContentIntent(pIntent)
			setOngoing(true)
			NotificationManagerCompat.from(context).notify(DEFAULT_NOTIFICATION_ID, build())
		}
	}

	inline fun <reified T : BaseActivity<*>, reified D : BroadcastReceiver> showPushNotification(
		context: Context,
		title: String,
		body: String,
		id: Long
	): Notification = with(NotificationCompat.Builder(context, PUSH_NOTIFICATION_CHANNEL_ID)) {
		val notifManager = NotificationManagerCompat.from(context)
		//need to remove persistent notification to show actions
		notifManager.cancel(DEFAULT_NOTIFICATION_ID)

		val deleteAction = NotificationCompat.Action.Builder(
			0, context.getString(R.string.delete), PendingIntent.getBroadcast(
				context,
				DELETE_NOTIFICATION_ACTION_CODE,
				Intent(context, D::class.java).apply {
					putExtra(PUSH_NOTIFICATION_ID_KEY, id)
					action = DELETE_NOTIFICATION_ACTION
				},
				flags
			)
		).build()

		val dismissIntent = PendingIntent.getBroadcast(
			context,
			DISMISS_NOTIFICATION_ACTION_CODE,
			Intent(context, D::class.java).apply {
				action = DISMISS_NOTIFICATION_ACTION
			},
			flags
		)

		val detailsAction = NotificationCompat.Action.Builder(
			0, context.getString(R.string.details), PendingIntent.getActivity(
				context,
				DETAILS_NOTIFICATION_ACTION_CODE,
				Intent(context, T::class.java).apply {
					action = SHOW_NOTIFICATION_ACTION
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
				},
				flags
			)
		).build()

		priority = NotificationCompat.PRIORITY_MAX
		setWhen(0)
		setContentTitle(title)
		setContentText(body)
		setSmallIcon(R.drawable.ic_baseline_notifications_24)
		setOnlyAlertOnce(true)
		setOngoing(false)
		setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_drawer))
		addAction(deleteAction)
		addAction(detailsAction)
		setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, T::class.java), flags))
		setDeleteIntent(dismissIntent)

		return build().also { notifManager.notify(id.toInt(), it) }
	}

	inline fun <reified T : BaseActivity<*>, reified D : OpenVPNService> showStatusNotification(
		context: Context,
		status: ConnectionStatus,
		server: Server?,
		timeLeft: Long = 0L
	): Notification = with(NotificationCompat.Builder(context, STATUS_CHANNEL_ID)) {
		setContentTitle(getNotificationTitle(context, status, server))
		setContentText(getNotificationText(context, status, server, timeLeft))
		setSmallIcon(
			if (status == ConnectionStatus.LEVEL_CONNECTED) R.drawable.ic_baseline_vpn_key_24
			else R.drawable.ic_baseline_notifications_24
		)
		setOnlyAlertOnce(true)
		setOngoing(true)
		setLargeIcon(BitmapFactory.decodeResource(context.resources, getLargeNotificationIcon(context, server)))
		setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, T::class.java), flags))

		val isPaused = status == ConnectionStatus.LEVEL_PAUSED
		if (status == ConnectionStatus.LEVEL_CONNECTED || isPaused) {
			val stopAction = NotificationCompat.Action.Builder(
				R.drawable.ic_baseline_stop_24, context.getString(R.string.disconnect), PendingIntent.getService(
					context,
					STOP_VPN_ACTION_CODE,
					Intent(context, D::class.java).apply { action = OpenVPNService.STOP_VPN },
					flags
				)
			).build()

			addAction(stopAction)
		}

		build().also { NotificationManagerCompat.from(context).notify(DEFAULT_NOTIFICATION_ID, it) }
	}

	fun getNotificationTitle(context: Context, status: ConnectionStatus, server: Server?): String =
		if (server == null) context.getString(R.string.state_connecting)
		else when (status) {
			ConnectionStatus.LEVEL_CONNECTED -> context.getString(
				R.string.connected_to,
				CountryUtils.getLocalizedNameFromCountryCode(PrefsHelper.getLanguage(), server.countryCode)
			)

			ConnectionStatus.LEVEL_DISCONNECTED -> context.getString(R.string.state_disconnected)
			else -> context.getString(R.string.state_connecting)
		}

	fun getNotificationText(context: Context, status: ConnectionStatus, server: Server?, timeLeft: Long): String =
		if (server == null) context.getString(R.string.preparing_to_connect)
		else when (status) {
			ConnectionStatus.LEVEL_PREPARING -> context.getString(R.string.preparing_to_connect)
			ConnectionStatus.LEVEL_CONNECTED ->
				if (SubscriptionManager.getInstance().isSubscribed || timeLeft == 0L)
					context.getString(R.string.you_safe)
				else
					context.getString(R.string.disconnect_in, Utils.millisToTime(timeLeft / 1000))

			ConnectionStatus.LEVEL_DISCONNECTED -> context.getString(R.string.persistent_notif_text)
			else -> context.getString(
				R.string.connecting_to,
				CountryUtils.getLocalizedNameFromCountryCode(PrefsHelper.getLanguage(), server.countryCode)
			)
		}

	fun getLargeNotificationIcon(context: Context, server: Server?) =
		if (server == null) 0
		else CountryUtils.getFlagResIDFromCountryCode(context, server.countryCode).let {
			if (it == 0)
				R.drawable.ic_drawer
			else
				it
		}

	@RequiresApi(api = Build.VERSION_CODES.O)
	fun createNotificationChannels(context: Context) {
		val channels: MutableList<NotificationChannel> = ArrayList()
		NotificationChannel(
			PERSISTENT_NOTIFICATION_CHANNEL_ID,
			context.getString(R.string.persistent_notification),
			NotificationManager.IMPORTANCE_LOW
		).apply {
			description = context.getString(R.string.persistent_notification_summary)
			enableLights(false)
			setSound(null, null)
			enableVibration(false)
			setShowBadge(false)
			channels.add(this)
		}

		// Connection status change messages
		NotificationChannel(
			STATUS_CHANNEL_ID,
			context.getString(R.string.channel_name_status),
			NotificationManager.IMPORTANCE_LOW
		).apply {
			description = context.getString(R.string.channel_description_status)
			enableLights(false)
			enableVibration(false)
			setShowBadge(false)
			channels.add(this)
		}

		// Urgent requests, e.g. two factor auth
		NotificationChannel(
			USER_REQ_CHANNEL_ID,
			context.getString(R.string.channel_name_user_req),
			NotificationManager.IMPORTANCE_HIGH
		).apply {
			description = context.getString(R.string.channel_description_user_req)
			enableLights(false)
			enableVibration(false)
			setShowBadge(false)
			channels.add(this)
		}

		// Push notifications
		NotificationChannel(
			PUSH_NOTIFICATION_CHANNEL_ID,
			context.getString(R.string.channel_name_push),
			NotificationManager.IMPORTANCE_HIGH
		).apply {
			description = context.getString(R.string.channel_description_push)
			enableLights(false)
			enableVibration(true)
			setShowBadge(true)
			channels.add(this)
		}

		NotificationManagerCompat.from(context).createNotificationChannels(channels)
	}
}