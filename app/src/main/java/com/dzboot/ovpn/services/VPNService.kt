package com.dzboot.ovpn.services

import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.dzboot.ovpn.R
import com.dzboot.ovpn.activities.MainActivity
import com.dzboot.ovpn.custom.Timer
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.data.remote.NetworkCaller
import com.dzboot.ovpn.helpers.*
import com.google.firebase.installations.FirebaseInstallations
import de.blinkt.openvpn.core.*
import kotlinx.coroutines.*
import org.lsposed.lsparanoid.Obfuscate
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber


@Obfuscate
class VPNService : OpenVPNService() {

	interface UpdateTimerCallback {

		fun updateTimeLeft(timer: Timer)
	}

	private val serversDao by lazy { AppDB.getInstance(this).serversDao() }

	private val mBinder = LocalBinder()
	var updateTimerCallback: UpdateTimerCallback? = null
	var reconnectCallback: ReconnectCallback? = null
	var askPassword: AskPasswordCallback? = null

	private lateinit var selectedServer: Server

	private var connectJob: Job? = null
	private var serverId: Int = 0
	private var reconnectCount = 0
	var isRetrying = false
	var connectServer: Server? = null

	private val timer = object : Timer() {
		override fun onTick(millisLeft: Long) {
			updateNotification(VpnStatus.mLastLevel, millisLeft)
			updateTimerCallback?.updateTimeLeft(this)
		}

		override fun onFinish() {
			stopVPN()
		}
	}

	private val reconnectTimer by lazy {
		object : Timer() {
			override fun onTick(millisLeft: Long) {
			}

			override fun onFinish() {
				isRetrying = true
				connectJob?.cancel()
				stopVPN()
			}
		}
	}

	override fun onVPNConnected() {
		isRetrying = false
		reconnectTimer.cancel()
		reconnectCount = 0
		logConnect()

		val initialConnectDuration = connectServer?.freeConnectDuration ?: 0
		if (!SubscriptionManager.getInstance().isSubscribed && initialConnectDuration != 0) {
			timer.start(initialConnectDuration)
		}
	}

	override fun onVPNDisconnected() {
		if (isRetrying) {
			if (++reconnectCount < PrefsHelper.getReconnectRetries()) {
				reconnectCallback?.onReconnect(reconnectCount)
				startConnectProcess()
			} else {
				reconnectCallback?.onDropConnect()
				NotificationsHelper.showPersistentNotification(this, MainActivity::class.java)
				isRetrying = false
				reconnectTimer.cancel()
				reconnectCount = 0
			}
		} else {
			Timber.d("DebugReconnect won't retry")
			reconnectCount = 0
			timer.cancel()
			connectJob?.cancel()
			reconnectTimer.cancel()
			NotificationsHelper.showPersistentNotification(this, MainActivity::class.java)
			logDisconnect()
		}
	}

	override fun askForPW(profile: VpnProfile) {
		val profileUsername = connectServer?.username ?: ""
		val profilePassword = connectServer?.password ?: ""

		if (profileUsername.isNotEmpty() && profilePassword.isNotEmpty()) {
			startVPN(profileUsername, profilePassword)
		} else {
			reconnectTimer.pause()
			askPassword?.showPasswordDialog(profile) { username, password ->
				reconnectTimer.resume()
				startVPN(username, password)
			}
		}
	}

	override fun onBind(intent: Intent?): IBinder = mBinder

	override fun stopVPN(): Boolean {
		VpnStatus.mLastLevel = ConnectionStatus.LEVEL_DISCONNECTED
		connectJob?.cancel()
		return super.stopVPN()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		if (intent == null)
			return START_NOT_STICKY

		serverId = intent.getIntExtra(SELECTED_SERVER_ID_EXTRA, Server.AUTO_ID)

		when (intent.action) {
			START_SERVICE -> return START_STICKY
			START_SERVICE_STICKY -> return START_REDELIVER_INTENT

			STOP_VPN -> {
				stopVPN()
				return START_NOT_STICKY
			}
		}

		startForeground(
			NotificationsHelper.DEFAULT_NOTIFICATION_ID,
			NotificationsHelper.showStatusNotification<MainActivity, VPNService>(
				this,
				ConnectionStatus.LEVEL_PREPARING,
				null
			)
		)

		VpnStatus.mLastLevel = ConnectionStatus.LEVEL_PREPARING
		startConnectProcess()

		return START_STICKY
	}

	private fun startConnectProcess() {
		reconnectTimer.start(PrefsHelper.getReconnectTimeout() * 1000)

		connectJob = CoroutineScope(Dispatchers.Default).launch {

			VPNHelper.saveOriginalIP(this@VPNService)

			selectedServer =
				if (serverId == Server.AUTO_ID) Server.auto() else serversDao.getServer(serverId) ?: Server.auto()

			connectServer = if (selectedServer.isAuto()) {
				when (PrefsHelper.getAutoMode()) {
					Server.DISTANCE -> serversDao.getNearestServer()
					Server.RANDOM -> serversDao.getRandomServer()

					else -> try {
						val bestServersResponse = NetworkCaller.getApi(this@VPNService).getBestServerId()
						val serverId = bestServersResponse.body()

						if (bestServersResponse.isSuccessful && serverId != null) {
							serversDao.getServer(serverId)
						} else
							throw Exception("Error while loading best server")
					} catch (e: Exception) {
						Timber.w(e)
						Timber.d("Can not fetch best server. Falling back to local database")
						serversDao.getLeastLoadedServer()
					}
				}
			} else {
				selectedServer
			}

			if (connectServer == null) {
				withContext(Dispatchers.Main) {
					Toast.makeText(this@VPNService, R.string.no_servers, Toast.LENGTH_SHORT).show()
				}
			} else connectServer?.let {
				ProfileFetcher.getProfile(this@VPNService, it, object : ProfileFetcher.ConnectCallback {
					override fun connect() {
						if (connectJob?.isCancelled == true)
							return

						checkAuthAndStartVPN(it.getProfileName())
					}

					override fun error(message: String?) {
						if (connectJob?.isCancelled == true)
							return

						Timber.e(message)
						isRetrying = true
						stopVPN()
					}
				})
			}
		}
	}

	override fun checkAuthAndStartVPN(profileName: String) {
		VpnStatus.addStateListener(this)
		Timber.i("Building configuration…", *arrayOfNulls<Any>(0))
		VpnStatus.updateStateString(
			"VPN_GENERATE_CONFIG",
			"",
			de.blinkt.openvpn.R.string.building_configration,
			ConnectionStatus.LEVEL_START
		)

		profile = ProfileManager.getInstance(this).loadProfile(profileName)
		when {
			profile.isUserPWAuth && !profile.mUsername.isNullOrBlank() && !profile.mPassword.isNullOrBlank() ->
				startVPN(profile.mUsername, profile.mPassword)

			profile.needUserPWInput() == 0 -> startVPN(null, null)

			else -> {
				VpnStatus.updateStateString(
					"USER_VPN_PASSWORD",
					"",
					de.blinkt.openvpn.R.string.state_user_vpn_password,
					ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT
				)
				askForPW(profile)
			}
		}
	}

	override fun updateNotification(status: ConnectionStatus, timeLeft: Long) {
		NotificationsHelper.showStatusNotification<MainActivity, VPNService>(
			this, status, connectServer ?: selectedServer, timeLeft
		)
	}

	fun addMoreTime() = timer.prolong()

	inner class LocalBinder : Binder() {

		val service: VPNService get() = this@VPNService
	}

	private fun logConnect() {
		val profileId = profile?.id ?: return

		Handler(Looper.getMainLooper()).postDelayed({
			                                            if (NetworkCaller.isUsingLocalServer())
				                                            NetworkCaller.getApi(this).logConnect(
					                                            profileId,
					                                            "local",
					                                            "local",
					                                            selectedServer.isAuto()
				                                            )
					                                            .enqueue(object : Callback<Void> {
						                                            override fun onResponse(
							                                            call: Call<Void>,
							                                            response: Response<Void>
						                                            ) {
							                                            if (response.isSuccessful)
								                                            Timber.d("logConnect success")
							                                            else {
								                                            Timber.d("logConnect error ${response.code()}")
								                                            logConnect()
							                                            }
						                                            }

						                                            override fun onFailure(call: Call<Void>, t: Throwable) {
							                                            Timber.d("logConnect fail: ${t.message}")

							                                            //retry until success
							                                            if (VpnStatus.isVPNActive())
								                                            logConnect()
						                                            }
					                                            })
			                                            else
				                                            PrefsHelper.getOriginalIP()?.let { ip ->
					                                            FirebaseInstallations.getInstance().id.addOnSuccessListener { firebaseId ->
						                                            NetworkCaller.getApi(this).logConnect(
							                                            profileId,
							                                            firebaseId,
							                                            ip,
							                                            selectedServer.isAuto()
						                                            )
							                                            .enqueue(object : Callback<Void> {
								                                            override fun onResponse(
									                                            call: Call<Void>,
									                                            response: Response<Void>
								                                            ) {
									                                            if (response.isSuccessful)
										                                            Timber.d("logConnect success")
									                                            else {
										                                            Timber.d("logConnect error ${response.code()}")
										                                            logConnect()
									                                            }
								                                            }

								                                            override fun onFailure(
									                                            call: Call<Void>,
									                                            t: Throwable
								                                            ) {
									                                            Timber.d("logConnect fail: ${t.message}")

									                                            //retry until success
									                                            if (VpnStatus.isVPNActive())
										                                            logConnect()
								                                            }
							                                            })
					                                            }
				                                            }
		                                            }, 1000L) // wait a second for the connection to stabilize
	}

	private fun logDisconnect() {
		val profileId = profile?.id ?: return

		Handler(Looper.getMainLooper()).postDelayed({
			                                            if (NetworkCaller.isUsingLocalServer())
				                                            NetworkCaller.getApi(this).logDisconnect(profileId, "local")
					                                            .enqueue(object : Callback<Void> {
						                                            override fun onResponse(
							                                            call: Call<Void>,
							                                            response: Response<Void>
						                                            ) {
							                                            Timber.d("logDisconnect success")
						                                            }

						                                            override fun onFailure(call: Call<Void>, t: Throwable) {
							                                            Timber.d("logDisconnect fail: ${t.message}")

							                                            //retry until success
							                                            if (!VpnStatus.isVPNActive())
								                                            logDisconnect()
						                                            }
					                                            })
			                                            else
				                                            FirebaseInstallations.getInstance().id.addOnSuccessListener {
					                                            NetworkCaller.getApi(this).logDisconnect(profileId, it)
						                                            .enqueue(object : Callback<Void> {
							                                            override fun onResponse(
								                                            call: Call<Void>,
								                                            response: Response<Void>
							                                            ) {
								                                            Timber.d("logDisconnect success")
							                                            }

							                                            override fun onFailure(
								                                            call: Call<Void>,
								                                            t: Throwable
							                                            ) {
								                                            Timber.d("logDisconnect fail: ${t.message}")

								                                            //retry until success
								                                            if (!VpnStatus.isVPNActive())
									                                            logDisconnect()
							                                            }
						                                            })
				                                            }
		                                            }, 1000L) // wait a second for the connection to stabilize
	}
}