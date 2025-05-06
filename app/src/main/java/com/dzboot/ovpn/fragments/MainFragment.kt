package com.dzboot.ovpn.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.*
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.animation.Animation
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.dzboot.ovpn.R
import com.dzboot.ovpn.activities.MainActivity
import com.dzboot.ovpn.base.BaseApplication.Companion.runningOnTV
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.custom.ReverseInterpolator
import com.dzboot.ovpn.custom.Timer
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.databinding.FragmentMainBinding
import com.dzboot.ovpn.helpers.*
import com.dzboot.ovpn.helpers.AdsManager.Companion.showInterstitialAd
import com.dzboot.ovpn.helpers.AdsManager.Companion.showMoreTimeAd
import com.dzboot.ovpn.helpers.VPNHelper.requestVPNPermission
import com.dzboot.ovpn.services.VPNService
import com.google.android.ads.nativetemplates.NativeTemplateStyle
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.play.core.review.ReviewManagerFactory
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.VpnProfile
import de.blinkt.openvpn.core.VpnStatus

import org.lsposed.lsparanoid.Obfuscate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


@Obfuscate
class MainFragment : BaseFragment<MainActivity, FragmentMainBinding>(), VpnStatus.StateListener,
    VpnStatus.ByteCountListener, VPNService.UpdateTimerCallback, OpenVPNService.ReconnectCallback,
    OpenVPNService.AskPasswordCallback {

    companion object {

        const val STATIC_TAG = "MainFragment"
        const val PROMPT_DISCONNECT = "prompt_disconnect"
        private const val SELECTED_SERVER_KEY = "selected_server"
    }

    override val TAG = STATIC_TAG
    override fun getPageTitle() = R.string.app_name
    override fun initializeBinding(): FragmentMainBinding =
        FragmentMainBinding.inflate(requireActivity().layoutInflater)

    //selectedLocation and connectLocation are different in Auto mode
    private var selectedServer: Server = Server.auto()
    private var service: VPNService? = null

    private var isServiceBound = false

    private val reviewManager by lazy { ReviewManagerFactory.create(requireContext()) }
    private val animator = ValueAnimator.ofFloat(0f, 1f)
    private val serversDao by lazy { AppDB.getInstance(requireContext()).serversDao() }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            service = (binder as VPNService.LocalBinder).service
            if (!SubscriptionManager.getInstance().isSubscribed)
                service!!.updateTimerCallback = this@MainFragment
            service!!.reconnectCallback = this@MainFragment
            service!!.askPassword = this@MainFragment

            VpnStatus.addStateListener(this@MainFragment)
            VpnStatus.addByteCountListener(this@MainFragment)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            service!!.updateTimerCallback = null
            service!!.reconnectCallback = null
            service!!.askPassword = null
            service = null
        }
    }

    private val startVPNForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onRequestVPNPermissionResult(result.resultCode)
        }

    private fun onRequestVPNPermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK)
            VPNHelper.startVPNIntent<VPNService>(requireContext(), selectedServer)
        else if (resultCode == Activity.RESULT_CANCELED) {
            // User does not want us to start, so we just vanish
            VpnStatus.updateStateString(
                "USER_VPN_PERMISSION_CANCELLED",
                "",
                R.string.state_user_vpn_permission_cancelled,
                ConnectionStatus.LEVEL_DISCONNECTED
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                DialogHelper.otherVPNAppAlwaysAllowed(requireContext())
        }
    }

    private fun connectBtnClicked() {
        Timber.d("connectBtnClicked ${VpnStatus.mLastLevel} ${service?.isRetrying}")
        if (VpnStatus.isVPNActiveOrConnecting() || service?.isRetrying == true) {
            Timber.d("Already connected or connecting. Stopping...")
            stopVPN()

            //show rating dialog
            reviewManager.requestReviewFlow().addOnCompleteListener { request ->
                if (isAdded && request.isSuccessful)
                    reviewManager.launchReviewFlow(requireActivity(), request.result)
            }
            return
        }

        //Click app if Ads are not initialized
        if (!SubscriptionManager.getInstance().isSubscribed && !PrefsHelper.getAdsInitialization()) {
            throw RuntimeException()
        }

        if(!SubscriptionManager.getInstance().isSubscribed && !selectedServer.isFree) {
            Toast.makeText(requireContext(), R.string.premium_server, Toast.LENGTH_SHORT).show()
            return
        }

        if (!Utils.isConnected(requireContext())) {
            Timber.d("Not connected to internet")
            Toast.makeText(requireContext(), R.string.log_no_internet, Toast.LENGTH_SHORT).show()
            return
        }

        //don't show ad first time to connect
        if (PrefsHelper.isFirstConnect())
            PrefsHelper.disableFirstConnect()
        else
            activity?.showInterstitialAd()

        startAnim()

        if (requireContext().requestVPNPermission(startVPNForResult)) {
            VpnStatus.updateStateString(
                "USER_VPN_PERMISSION",
                "",
                R.string.state_user_vpn_permission,
                ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT
            )
        } else
            onRequestVPNPermissionResult(Activity.RESULT_OK)
    }

    //region Fragment lifecycle
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.connectLayout.setOnClickListener { connectBtnClicked() }
        binding.connect?.setOnClickListener { connectBtnClicked() }
        binding.currentLocationLayout?.setOnClickListener {
            activity?.showInterstitialAd()
            val serversFragment = ServersFragment()
            val args = Bundle()
            args.putSerializable(ServersFragment.CONNECTED_SERVER, service?.connectServer)
            serversFragment.arguments = args
            activity?.changeScreen(serversFragment, false)
        }

        binding.info?.setOnClickListener {
            if (binding.infoLayout.container.isVisible)
                hideInfoLayoutOnNonTV()
            else {
                showInfoLayoutOnNonTV()
            }
        }

        binding.moreTime.setOnClickListener { activity?.showMoreTimeAd { service?.addMoreTime() } }

        //TODO called always when returning to fragment
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            selectedServer = if (savedInstanceState == null) {
                serversDao.getServer(PrefsHelper.getSavedServerId()) ?: Server.auto()
            } else {
                savedInstanceState.getSerializable(SELECTED_SERVER_KEY) as Server
            }
            withContext(Dispatchers.Main) { onServerSelected() }
        }
    }

    override fun onResume() {
        super.onResume()

        val intent = Intent(requireContext(), VPNService::class.java)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        isServiceBound = true

        if (!VpnStatus.isVPNActiveOrConnecting()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) { VPNHelper.saveOriginalIP(requireContext()) }
        }
    }

    override fun onPause() {
        super.onPause()

        if (isServiceBound) {
            requireContext().unbindService(connection)
            isServiceBound = false
        }

        VpnStatus.removeStateListener(this)
        VpnStatus.removeByteCountListener(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(SELECTED_SERVER_KEY, selectedServer)
    }
    //endregion

    //region Functions Related to OpenVPN
    private fun stopVPN() {
        service?.isRetrying = false
        setUIToDisconnected()
        try {
            service?.stopVPN()
        } catch (e: RemoteException) {
            Timber.e(e)
        }
    }

    override fun updateState(
        state: String?, logMessage: String?, localizedResId: Int, level: ConnectionStatus, Intent: Intent?
    ) {
        if (isAdded)
            activity?.runOnUiThread {
                if (state == "NOPROCESS")
                    setUIToDisconnected()
                else when (VpnStatus.mLastLevel) {
                    ConnectionStatus.LEVEL_CONNECTED -> setUIToConnected()
                    ConnectionStatus.LEVEL_DISCONNECTED -> setUIToDisconnected()
                    ConnectionStatus.USER_VPN_PASSWORD_CANCELLED -> setUIToDisconnected()
                    ConnectionStatus.LEVEL_AUTH_FAILED -> {
                        Toast.makeText(requireContext(), R.string.wrong_credentials, Toast.LENGTH_SHORT).show()
                        setUIToDisconnected()
                    }
                    ConnectionStatus.LEVEL_INVALID_CERTIFICATE -> {
                        setUIToDisconnected()
                        DialogHelper.invalidCertificate(requireContext())
                    }
                    else -> {
                        //connecting
                    }
                }
            }
    }

    override fun updateByteCount(inTraffic: Long, outTraffic: Long, p2: Long, p3: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) { updateTrafficCount(inTraffic, outTraffic) }
    }
    //endregion

    //region Update UI
    fun onServerSelected() {
        val newServer = arguments?.get(ServersFragment.NEW_SERVER) as Server?
        if (newServer != null) {
            activity?.showInterstitialAd()
            selectedServer = newServer
            setSelectedServer()
        }

        if (VpnStatus.isVPNActive() && arguments?.getBoolean(PROMPT_DISCONNECT, false) == true) {
            arguments?.remove(PROMPT_DISCONNECT)
            DialogHelper.disconnect(
                requireContext(),
                R.string.apps_using_change_disconnect_alert_message
            ) { stopVPN() }
        }
    }

    private fun setSelectedServer() {
        binding.locationFlag?.setImageDrawable(getDrawableCompat(R.drawable.ic_auto)?.let {
            selectedServer.getFlagDrawable(requireContext(), it)
        })
        binding.locationTitle?.text = selectedServer.getLocationName(requireContext())

        with(selectedServer.city) {
            if (isBlank()) {
                binding.locationSubtitle?.visibility = View.GONE
            } else {
                binding.locationSubtitle?.visibility = View.VISIBLE
                binding.locationSubtitle?.text = this
            }
        }
    }

    private fun updateTrafficCount(inTraffic: Long, outTraffic: Long) = with(binding.infoLayout) {
        dataDown.text = getString(R.string.data_down, Utils.humanReadableByteCount(inTraffic, false, resources))
        dataUp.text = getString(R.string.data_up, Utils.humanReadableByteCount(outTraffic, false, resources))
    }

    fun showNativeAd(nativeAd: NativeAd) {
        binding.nativeAdView?.visible()
        binding.nativeAdView?.setStyles(
            NativeTemplateStyle.Builder().build()
        )
        binding.nativeAdView?.setNativeAd(nativeAd)
    }

    fun hideNativeAd() {
        binding.nativeAdView?.gone()
        try {
            binding.nativeAdView?.destroyNativeAd()
        } catch (ignore: Exception) {
        }
    }

    private fun showInfoLayoutOnNonTV() {
        binding.infoLayout.container.visible()
        binding.info?.setImageResource(R.drawable.ic_baseline_close_24)
        binding.info?.contentDescription = getString(R.string.open_info_description)
    }

    private fun hideInfoLayoutOnNonTV() {
        binding.infoLayout.container.gone()
        binding.info?.setImageResource(R.drawable.ic_baseline_error_24)
        binding.info?.contentDescription = getString(R.string.close_info_description)
    }

    private fun setUIToConnected() {
        if (!isAdded)
            return

        animator.end()
        with(binding.tvLog) {
            setTextColor(getColorCompat(R.color.primary))
            text = getString(R.string.connected_to, service?.connectServer?.getLocationName(requireContext()))
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                null,
                null,
                getDrawableCompat(R.drawable.ic_auto)?.let { autoDrawable ->
                    service?.connectServer?.getFlagDrawable(requireContext(), autoDrawable)?.let {
                        Utils.resizeDrawable(requireContext(), it, 24, 20)
                    }
                },
                null
            )
        }

        binding.info?.visible()
        if (runningOnTV)
            binding.infoLayout.container.visible()

        PrefsHelper.getOriginalIP()?.let { ip ->
            binding.infoLayout.originalIp.text = getString(R.string.original_ip, ip)
        }
        binding.infoLayout.newIp.text = getString(R.string.new_ip, service?.connectServer?.ip)
        service?.connectServer?.let { server ->
            if (server.connectedDevices != 0)
                binding.infoLayout.connectedDevices.text =
                    getString(R.string.connected_devices, server.connectedDevices)
        }

        if (SubscriptionManager.getInstance().isSubscribed || service?.connectServer?.freeConnectDuration == 0) {
            binding.timeLeftProgress.gone()
            binding.timeLeft.gone()
            binding.moreTime.gone()
        } else {
            binding.timeLeftProgress.visible()
            binding.timeLeft.visible()
            binding.moreTime.visible()
        }

        binding.connect?.setText(R.string.disconnect)
        binding.ivStatusBackground.background = getDrawableCompat(R.drawable.ic_shield_on)
        val connectString = getString(R.string.connected)
        binding.ivStatusBackground.contentDescription = connectString
        binding.ivStatusForeground.contentDescription = connectString
        DrawableCompat.setTint(
            DrawableCompat.wrap(binding.ivStatusForeground.background),
            getColorCompat(R.color.background)
        )
    }

    private fun setUIToDisconnected() {
        if (service?.isRetrying == true)
            return

        animator.end()
        with(binding.tvLog) {
            setTextColor(getColorCompat(android.R.color.tab_indicator_text))
            setText(R.string.not_connected)
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        }

        binding.info?.gone()
        if (runningOnTV)
            binding.infoLayout.container.gone()
        else
            hideInfoLayoutOnNonTV()

        binding.timeLeftProgress.gone()
        binding.moreTime.gone()
        binding.timeLeft.gone()
        binding.connect?.setText(R.string.connect)
        binding.ivStatusBackground.background = getDrawableCompat(R.drawable.ic_shield_off)
        val notConnectedString = getString(R.string.not_connected)
        binding.ivStatusBackground.contentDescription = notConnectedString
        binding.ivStatusForeground.contentDescription = notConnectedString
        DrawableCompat.setTint(
            DrawableCompat.wrap(binding.ivStatusForeground.background),
            getColorCompat(R.color.background)
        )
    }

    private fun startAnim(retries: Int = 0) {
        animator.duration = 1000
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = Animation.INFINITE

        val hsv = FloatArray(3)
        val from = FloatArray(3)
        val to = FloatArray(3)
        Color.colorToHSV(ContextCompat.getColor(requireContext(), R.color.background), from)
        Color.colorToHSV(ContextCompat.getColor(requireContext(), R.color.primary), to)

        val text = if (retries == 0)
            getString(R.string.connecting)
        else
            getString(R.string.retrying_count, retries, PrefsHelper.getReconnectRetries())

        binding.tvLog.text = text
        binding.ivStatusForeground.contentDescription = text

        binding.connect?.setText(R.string.connecting)

        val drawable = DrawableCompat.wrap(binding.ivStatusForeground.background)
        animator.addUpdateListener { animation ->
            hsv[0] = (from[0] + to[0] * animation.animatedFraction)
            hsv[1] = (from[1] + to[1] * animation.animatedFraction)
            hsv[2] = (from[2] + to[2] * animation.animatedFraction)
            DrawableCompat.setTint(drawable, Color.HSVToColor(hsv))
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animation.removeListener(this)
                animation.duration = 0
                animation.interpolator = ReverseInterpolator()
                animation.start()
            }
        })

        animator.start()
    }
    //endregion

    override fun updateTimeLeft(timer: Timer) {
        if (isAdded) {
            binding.timeLeftProgress.progress = timer.getProgress()
            binding.timeLeft.text = getString(R.string.time_left, timer.getTimeLeftString())
        }
    }

    override fun onReconnect(retries: Int) {
        activity?.runOnUiThread {
            startAnim(retries + 1)
        }
    }

    override fun onDropConnect() {
        activity?.runOnUiThread {
            Toast.makeText(
                requireContext(),
                getString(R.string.dropped_connection, PrefsHelper.getReconnectRetries()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun showPasswordDialog(
        profile: VpnProfile,
        submitCallback: OpenVPNService.SubmitAuthCredentialsCallback
    ) {
        if (!isAdded)
            return

        val entry = EditText(requireContext())

        entry.setSingleLine()
        entry.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        entry.transformationMethod = PasswordTransformationMethod()

        val dialog = AlertDialog.Builder(requireContext())
        dialog.setTitle(getString(R.string.pw_request_dialog_title, getString(R.string.password)))
        dialog.setMessage(getString(R.string.pw_request_dialog_prompt, profile.mName))


        val userPasswordLayout: View = layoutInflater.inflate(R.layout.userpass_layout, null, false)

        val usernameET = userPasswordLayout.findViewById<EditText>(R.id.username)
        val passwordET = userPasswordLayout.findViewById<EditText>(R.id.password)
        val saveCredentialsET = userPasswordLayout.findViewById<CheckBox>(R.id.save_credentials)
        val showPasswordET = userPasswordLayout.findViewById<CheckBox>(R.id.show_password)

        profile.mUsername = PrefsHelper.getSavedUsername()
        profile.mPassword = PrefsHelper.getSavedPassword()
        usernameET.setText(profile.mUsername)
        passwordET.setText(profile.mPassword)
        saveCredentialsET.isChecked = profile.mUsername != ""

        showPasswordET.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked)
                passwordET.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                passwordET.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        dialog.setView(userPasswordLayout)

        dialog.setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
            val username = usernameET.text.toString()
            val password = passwordET.text.toString()
            if (saveCredentialsET.isChecked)
                PrefsHelper.saveUserCredentials(username, password)
            else
                PrefsHelper.saveUserCredentials("", "")

            submitCallback.onSubmit(username, password)
        }

        dialog.setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int ->
            VpnStatus.updateStateString(
                "USER_VPN_PASSWORD_CANCELLED",
                "",
                R.string.state_user_vpn_password_cancelled,
                ConnectionStatus.LEVEL_DISCONNECTED
            )
        }

        activity?.runOnUiThread { dialog.show() }
    }
}