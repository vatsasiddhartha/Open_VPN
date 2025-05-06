package com.dzboot.ovpn.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.core.content.ContentProviderCompat.*
import androidx.core.content.ContextCompat
import com.dzboot.ovpn.R
import timber.log.Timber
import java.net.InetAddress
import java.util.*
import kotlin.math.ln
import kotlin.math.pow


object Utils {

	fun resizeDrawable(
		context: Context, drawable: Drawable, widthDp: Int, heightDp: Int
	): Drawable {
		val b = (drawable as BitmapDrawable).bitmap
		val bitmapResized =
			Bitmap.createScaledBitmap(b, dpToPx(context, widthDp.toFloat()), dpToPx(context, heightDp.toFloat()), false)
		return BitmapDrawable(context.resources, bitmapResized)
	}

	fun dpToPx(context: Context, dp: Float) =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()

	fun Context?.isOrientationLandscape() =
		if (this == null) false
		else resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE


	fun humanReadableByteCount(bytes: Long, isSpeed: Boolean, res: Resources): String {
		val b = if (isSpeed) 8 * bytes else bytes
		val unit = if (isSpeed) 1000 else 1024
		val exp = 0.coerceAtLeast((ln(b.toDouble()) / ln(unit.toDouble())).toInt().coerceAtMost(3))
		val bytesUnit = (b / unit.toDouble().pow(exp.toDouble())).toFloat()
		return if (isSpeed) when (exp) {
			0 -> res.getString(R.string.bits_per_second, bytesUnit)
			1 -> res.getString(R.string.kbits_per_second, bytesUnit)
			2 -> res.getString(R.string.mbits_per_second, bytesUnit)
			else -> res.getString(R.string.gbits_per_second, bytesUnit)
		} else when (exp) {
			0 -> res.getString(R.string.volume_byte, bytesUnit)
			1 -> res.getString(R.string.volume_kbyte, bytesUnit)
			2 -> res.getString(R.string.volume_mbyte, bytesUnit)
			else -> res.getString(R.string.volume_gbyte, bytesUnit)
		}
	}

	fun Context.goToAppInfoPage() = try {
		val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
		intent.data = Uri.parse("package:$packageName")
		startActivity(intent)
	} catch (e: ActivityNotFoundException) {
		startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
	}

	fun Context.getToSubscriptionsPage() = startActivity(
		Intent(
			Intent.ACTION_VIEW,
			Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
		)
	)

	fun shareApp(context: Context) = with(context) {
		val sendIntent = Intent()
		sendIntent.action = Intent.ACTION_SEND
		sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message, packageName))
		sendIntent.type = "text/plain"
		try {
			startActivity(sendIntent)
		} catch (anfe: ActivityNotFoundException) {
			Toast.makeText(this, R.string.no_app_event, Toast.LENGTH_SHORT).show()
		}
	}

	fun openPlayStoreAppPage(context: Context) = with(context) {
		try {
			startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
		} catch (anfe: ActivityNotFoundException) {
			try {
				startActivity(
					Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
				)
			} catch (anfe: ActivityNotFoundException) {
				Toast.makeText(this, R.string.no_app_event, Toast.LENGTH_SHORT).show()
			}
		}
	}

	fun isConnected(context: Context): Boolean {
		//no easy fast way to check for internet than this right now
		return try {
			val cm = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
			val nInfo = cm?.activeNetworkInfo
			nInfo != null && nInfo.isAvailable && nInfo.isConnected
		} catch (ignore: Exception) {
			false
		}
	}

	fun ping(ip: String): Long = try {
		var time = System.currentTimeMillis()
		//		Timber.d("Start pinging $ip")
		val address = InetAddress.getByName(ip)
		val reachable = address.isReachable(10000)
		time = System.currentTimeMillis() - time
		//		Timber.d("Ping $ip result=$reachable, time=$time")
		time
	} catch (e: java.lang.Exception) {
		Timber.e(e)
		-1
	}

	fun Context.canDrawOverlays() =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

	@RequiresApi(Build.VERSION_CODES.M)
	fun Context.launchDrawOverlaysPermission(request: ActivityResultLauncher<Intent>) = try {
		request.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}")))
	} catch (exception: ActivityNotFoundException) {
		Toast.makeText(this, R.string.no_app_event, Toast.LENGTH_SHORT).show()
	}

	fun millisToTime(timeSeconds: Long): String {
		val minutes = timeSeconds / 60
		val hours = minutes / 60
		return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes % 60, timeSeconds % 60)
	}
}