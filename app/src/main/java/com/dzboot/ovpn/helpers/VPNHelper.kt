package com.dzboot.ovpn.helpers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import com.dzboot.ovpn.R
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.data.remote.NetworkCaller
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnStatus
import timber.log.Timber


object VPNHelper {

    suspend fun saveOriginalIP(context: Context) {
        //save IP if not connected
        try {
            val ipResponse = NetworkCaller.getApi(context).getIp()
            val ip = ipResponse.body()
            if (ipResponse.isSuccessful && ip != null) {
                PrefsHelper.saveOriginalIP(ip.value)
            }
        } catch (ignore: Exception) {
            PrefsHelper.saveOriginalIP(null)
        }
    }

    inline fun <reified T : OpenVPNService> startVPNIntent(context: Context, selectedServer: Server) {
        if (VpnStatus.isVPNActive())
            return

        Timber.d("startVPNIntent")
        with(Intent(context, T::class.java)) {
            action = OpenVPNService.START_VPN
            putExtra(OpenVPNService.SELECTED_SERVER_ID_EXTRA, selectedServer.id)

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(this)
                else
                    context.startService(this)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun Context.openVPNSettings() = try {
        startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_app_event, Toast.LENGTH_SHORT).show()
    }

    //return false if user already accepted vpn permission
    fun Context?.requestVPNPermission(request: ActivityResultLauncher<Intent>): Boolean {
        val vpn = VpnService.prepare(this) ?: return false
        request.launch(vpn)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun Context.isVpnAlwaysOn(): Boolean {
        val alwaysOn = Settings.Secure.getString(contentResolver, "always_on_vpn_app")
        return alwaysOn == packageName
    }
}