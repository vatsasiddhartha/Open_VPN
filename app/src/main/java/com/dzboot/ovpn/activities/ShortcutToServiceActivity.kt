package com.dzboot.ovpn.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import com.dzboot.ovpn.R
import com.dzboot.ovpn.services.VPNService
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnStatus


class ShortcutToServiceActivity : Activity() {

    private var service: VPNService? = null


    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            service = (binder as VPNService.LocalBinder).service

            val serverId = intent.getIntExtra(OpenVPNService.SELECTED_SERVER_ID_EXTRA, -1)

            if (service != null && VpnStatus.isVPNActive()) {
                if (serverId == service?.connectServer?.id) {
                    Toast.makeText(
                        this@ShortcutToServiceActivity,
                        R.string.already_connected,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return
                }
                service?.stopVPN()
            }

            Toast.makeText(this@ShortcutToServiceActivity, R.string.connecting, Toast.LENGTH_SHORT).show()

            val serviceIntent = Intent(this@ShortcutToServiceActivity, VPNService::class.java).apply {
                action = OpenVPNService.START_VPN
                putExtra(OpenVPNService.SELECTED_SERVER_ID_EXTRA, serverId)
            }

            startService(serviceIntent)
            finish()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            service = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, VPNService::class.java)
        intent.action = OpenVPNService.START_SERVICE
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }


    override fun onStop() {
        super.onStop()
        unbindService(connection)
    }
}