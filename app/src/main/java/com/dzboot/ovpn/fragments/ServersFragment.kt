package com.dzboot.ovpn.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.dzboot.ovpn.R
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.data.remote.NetworkCaller
import com.dzboot.ovpn.databinding.FragmentServersBinding
import com.dzboot.ovpn.helpers.*
import com.dzboot.ovpn.helpers.AdsManager.Companion.loadBannerAd
import com.dzboot.ovpn.helpers.VPNHelper.requestVPNPermission
import com.dzboot.ovpn.activities.MainActivity
import com.dzboot.ovpn.adapters.ServersAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class ServersFragment : BaseFragment<MainActivity, FragmentServersBinding>() {

	companion object {

		const val STATIC_TAG = "ServersFragment"
		const val CONNECTED_SERVER = "connected_server"
		const val NEW_SERVER = "connected_server"
	}

	override val TAG = STATIC_TAG

	override fun initializeBinding() = FragmentServersBinding.inflate(requireActivity().layoutInflater)
	override fun getPageTitle() = R.string.servers_list

	private var serverToAddToShortcuts: Server? = null
	private val serversDao by lazy { AppDB.getInstance(requireContext()).serversDao() }

	private val startVPNForResult =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			onRequestVPNPermissionResult(result.resultCode)
		}

	private fun onRequestVPNPermissionResult(resultCode: Int) {
		if (resultCode == Activity.RESULT_OK)
			serverToAddToShortcuts?.let { ShortcutsHelper.add(requireContext(), it) }
		else if (resultCode == Activity.RESULT_CANCELED) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
				DialogHelper.otherVPNAppAlwaysAllowed(requireContext())
		}
	}

	private val serversAdapter by lazy {
		ServersAdapter(requireContext()) { server ->
			if (!server.isFree && !SubscriptionManager.getInstance().isSubscribed) {
				Toast.makeText(requireContext(), R.string.premium_server, Toast.LENGTH_SHORT).show()
				activity?.changeScreen(SubscriptionFragment(), false)
				return@ServersAdapter
			}

			PrefsHelper.saveServer(server.id)
			setSelectedServer(server)
			val connectedServer = arguments?.get(CONNECTED_SERVER) as Server?
			val arguments = Bundle()
			arguments.putSerializable(NEW_SERVER, server)
			activity?.switchToMainScreen(arguments, connectedServer?.id != server.id)
		}
	}

	//used on landscape only
	private fun setSelectedServer(selectedServer: Server) {
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

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		Timber.d("ServersFragment onViewCreated")
		registerForContextMenu(binding.serversList)
		activity?.loadBannerAd(binding.bannerAdLayout)
		binding.serversList.hasFixedSize()
		binding.serversList.adapter = serversAdapter

		binding.close?.setOnClickListener { activity?.switchToMainScreen() }
		binding.refresh.setOnClickListener {
			Timber.d("Update servers")
			binding.loadingServers.visible()
			binding.serversList.gone()
			binding.refresh.invisible()

			viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
				Timber.d("Call from refresh.onClick->viewLifecycleOwner")
				try {
					val serversResponse = NetworkCaller.getApi(requireContext()).getServers()
					val servers = serversResponse.body()

					if (serversResponse.isSuccessful && servers != null) {
						Timber.d("Updating servers in database. Size = ${servers.size}")
						serversDao.deleteAll()
						serversDao.insert(servers)
						setServersList(servers)
						ShortcutsHelper.removeNonExistent(requireContext(), servers)
					} else throw Exception("Error while loading servers list")
				} catch (e: Exception) {
					Timber.e(e, "updateServers error")
				}
			}
		}

		viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
			Timber.d("Called from viewLifecycleOwner")
			setServersList(serversDao.getAll())
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	fun updateServersList() = serversAdapter.notifyDataSetChanged()

	private suspend fun setServersList(servers: List<Server>) = withContext(Dispatchers.Main) {
		Timber.d("Updating servers")
		serversAdapter.setServers(requireContext(), servers)
		Timber.d("set servers done")
		binding.refresh.visible()
		binding.serversList.visible()
		binding.loadingServers.gone()
	}

	override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
		super.onCreateContextMenu(menu, v, menuInfo)
		val inflater = requireActivity().menuInflater
		inflater.inflate(R.menu.server_menu, menu)
		val server = serversAdapter.getClickedServer()

		if (ShortcutsHelper.isAdded(requireContext(), server.id))
			menu.findItem(R.id.menu_add_shortcut).isVisible = false
		else
			menu.findItem(R.id.menu_remove_shortcut).isVisible = false
	}

	override fun onContextItemSelected(item: MenuItem): Boolean {
		val server = serversAdapter.getClickedServer()
		when (item.itemId) {
			R.id.menu_remove_shortcut -> ShortcutsHelper.remove(requireContext(), "server_${server.id}")

			R.id.menu_add_shortcut -> {
				serverToAddToShortcuts = server
				checkVPNPermission()
			}
		}
		return true
	}

	private fun checkVPNPermission() {
		if (!SubscriptionManager.getInstance().isSubscribed) {
			Toast.makeText(requireContext(), R.string.subscription_required, Toast.LENGTH_SHORT).show()
			return
		}

		if (!requireContext().requestVPNPermission(startVPNForResult))
			onRequestVPNPermissionResult(Activity.RESULT_OK)
	}

	fun hideBanner() {
		binding.bannerAdLayout.removeAllViews()
		binding.bannerAdLayout.gone()
	}
}