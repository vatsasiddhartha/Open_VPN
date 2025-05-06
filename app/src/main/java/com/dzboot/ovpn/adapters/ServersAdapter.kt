package com.dzboot.ovpn.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dzboot.ovpn.R
import com.dzboot.ovpn.custom.ContextViewHolder
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.models.Server
import com.dzboot.ovpn.databinding.ItemServerBinding
import com.dzboot.ovpn.helpers.*
import com.flags.CountryUtils
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class ServersAdapter(private val context: Context, private val onServerSelect: (Server) -> Unit) :
	RecyclerView.Adapter<ServersAdapter.ViewHolder>() {

	private val servers = ArrayList<Server>()
	private val currentLanguageCode = PrefsHelper.getLanguage()
	private val pingIcon = IconicsDrawable(context, FontAwesome.Icon.faw_server)
		.apply {
			sizeDp = 14
			colorInt = ContextCompat.getColor(context, R.color.primary)
		}

	private var contextMenuPosition = 0


	fun getClickedServer() = servers[contextMenuPosition]

	@SuppressLint("NotifyDataSetChanged")
	fun setServers(context: Context, servers: List<Server>) {
		this.servers.clear()
		this.servers.add(0, Server.auto())
		this.servers.addAll(servers)
		notifyDataSetChanged()

		//ping servers only if not connected to VPN
		if (!VpnStatus.isVPNActive())
			CoroutineScope(Dispatchers.Default).launch {
				servers.forEachIndexed { index, server ->
					Timber.d("Pinging $index")
					server.ping = Utils.ping(server.ip)
					withContext(Dispatchers.Main) { notifyItemChanged(index) }
				}
				withContext(Dispatchers.IO) { AppDB.getInstance(context).serversDao().insert(servers) }
			}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		with(servers[position]) {
			holder.itemView.setOnLongClickListener {
				this@ServersAdapter.contextMenuPosition = holder.adapterPosition
				false
			}

			holder.binding.flag.setImageDrawable(
				holder.getDrawable(R.drawable.ic_auto)?.let { getFlagDrawable(context, it) }
			)

			if (isAuto()) {
				holder.binding.connectedDevices.gone()
				holder.binding.pinging.gone()
				holder.binding.ping.gone()
				holder.binding.freeConnectDuration.gone()
				holder.binding.freeOrPremium.gone()
				holder.binding.title.text = holder.getString(R.string.auto)
			} else {
				holder.binding.connectedDevices.visible()
				holder.binding.freeConnectDuration.visible()
				holder.binding.connectedDevices.text = connectedDevices.toString()
				holder.binding.title.text =
					CountryUtils.getLocalizedNameFromCountryCode(currentLanguageCode, countryCode)

				holder.binding.ping.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, pingIcon, null)

				val subscribed = SubscriptionManager.getInstance().isSubscribed
				if (subscribed || freeConnectDuration == 0) {
					holder.binding.freeConnectDuration.setTextColor(holder.getColor(R.color.ping_good))
					holder.binding.freeConnectDuration.text = holder.getString(R.string.unlimited)
				} else {
					holder.binding.freeConnectDuration.setTextColor(holder.getColor(android.R.color.tab_indicator_text))
					holder.binding.freeConnectDuration.text =
						holder.getString(R.string.connect_duration, freeConnectDuration)
				}

				if (subscribed) {
					holder.binding.freeOrPremium.gone()
				} else {
					holder.binding.freeOrPremium.visible()

					if (isFree) {
						holder.binding.freeOrPremium.background = holder.getDrawable(R.drawable.background_free_server)
						holder.binding.freeOrPremium.text = holder.getString(R.string.free)
					} else {
						holder.binding.freeOrPremium.background =
							holder.getDrawable(R.drawable.background_premium_server)
						holder.binding.freeOrPremium.text = holder.getString(R.string.premium)
					}
				}

				when {
					ping == 0L -> {
						holder.binding.pinging.visible()
						holder.binding.ping.gone()
					}

					ping == -1L -> {
						holder.binding.pinging.gone()
						holder.binding.ping.visible()
						holder.binding.ping.text = holder.getString(R.string.error)
						holder.binding.ping.setTextColor(holder.getColor(R.color.ping_bad))
					}

					ping > 500 -> {
						holder.binding.pinging.gone()
						holder.binding.ping.visible()
						holder.binding.ping.text = holder.getString(R.string.ping_text, ping)
						holder.binding.ping.setTextColor(holder.getColor(R.color.ping_bad))
					}

					ping > 200 -> {
						holder.binding.pinging.gone()
						holder.binding.ping.visible()
						holder.binding.ping.text = holder.getString(R.string.ping_text, ping)
						holder.binding.ping.setTextColor(holder.getColor(R.color.ping_fair))
					}

					else -> {
						holder.binding.pinging.gone()
						holder.binding.ping.visible()
						holder.binding.ping.text = holder.getString(R.string.ping_text, ping)
						holder.binding.ping.setTextColor(holder.getColor(R.color.ping_good))
					}
				}
			}

			holder.binding.subtitle.visibility = if (city.isBlank()) View.GONE else View.VISIBLE
			holder.binding.subtitle.text = city.trim()
		}
	}

	override fun getItemCount() = servers.size

	inner class ViewHolder(parent: ViewGroup) : ContextViewHolder<ItemServerBinding>(
		ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
	) {

		init {
			itemView.setOnClickListener {
				adapterPosition.let {
					if (it < 0)
						return@let
					onServerSelect(servers[it])
				}
			}

			binding.options.setOnClickListener {
				this@ServersAdapter.contextMenuPosition = adapterPosition
				itemView.showContextMenu()
			}
		}
	}

	override fun onViewRecycled(holder: ViewHolder) {
		holder.itemView.setOnLongClickListener(null)
		super.onViewRecycled(holder)
	}
}