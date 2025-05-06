package com.dzboot.ovpn.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.dzboot.ovpn.custom.ContextViewHolder
import com.dzboot.ovpn.data.models.Application
import com.dzboot.ovpn.databinding.ItemAppsUsingBinding
import com.dzboot.ovpn.helpers.PrefsHelper
import java.util.*


@SuppressLint("NotifyDataSetChanged")
class AppsUsingAdapter(private val countChangeListener: CountChangeListener) :
	RecyclerView.Adapter<AppsUsingAdapter.ViewHolder>(), Filterable {

	private var apps: ArrayList<Application> = arrayListOf()
	private var allApps: ArrayList<Application> = arrayListOf()
	private var configChanged = false
	var appsUsingCount = 0
		private set

	private val filter: Filter = object : Filter() {

		override fun performFiltering(constraint: CharSequence): FilterResults {
			val query = constraint.toString().lowercase(Locale.ROOT).trim()
			var temp: MutableList<Application> = ArrayList()
			if (query.isEmpty()) {
				temp = allApps
			} else
				for (app in allApps) if (app.name.lowercase(Locale.ROOT).contains(query)) temp.add(app)

			val results = FilterResults()
			results.values = temp
			return results
		}

		override fun publishResults(constraint: CharSequence, results: FilterResults) {
			apps.clear()
			apps.addAll((results.values as List<Application>))
			notifyDataSetChanged()
		}
	}

	override fun getFilter() = filter

	fun setApps(apps: ArrayList<Application>) {
		this.apps = apps
		allApps = ArrayList(apps)
		for (app in apps) if (app.isActive) appsUsingCount++
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(parent)

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val app = apps[position]
		holder.binding.icon.setImageDrawable(app.icon)
		holder.binding.activate.text = app.name
		holder.binding.activate.isChecked = app.isActive
	}

	override fun getItemCount() = apps.size

	val allItemsCount: Int
		get() = allApps.size

	fun hasConfigChanged() = configChanged

	fun enableAll() {
		for (app in allApps) app.isActive = true
		notifyDataSetChanged()
		PrefsHelper.setAllAppsUsing()
		appsUsingCount = apps.size
		countChangeListener.onCountChange(appsUsingCount)
	}

	fun disableAll() {
		val inactiveApps = HashSet<String>()
		for (app in allApps) {
			app.isActive = false
			inactiveApps.add(app.packageName)
		}
		notifyDataSetChanged()
		PrefsHelper.saveAppsNotUsing(inactiveApps)
		appsUsingCount = 0
		countChangeListener.onCountChange(appsUsingCount)
	}

	inner class ViewHolder(parent: ViewGroup) : ContextViewHolder<ItemAppsUsingBinding>(
		ItemAppsUsingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
	), View.OnClickListener {

		override fun onClick(v: View) {
			val inactiveApps: HashSet<String> = PrefsHelper.getAppsNotUsing()
			val pos = adapterPosition
			val app = apps[pos]
			if ((v as SwitchCompat).isChecked) {
				app.isActive = true
				inactiveApps.remove(app.packageName)
				countChangeListener.onCountChange(++appsUsingCount)
			} else {
				app.isActive = false
				inactiveApps.add(app.packageName)
				countChangeListener.onCountChange(--appsUsingCount)
			}
			configChanged = true
			PrefsHelper.saveAppsNotUsing(inactiveApps)
			notifyItemChanged(pos)
		}

		init {
			binding.activate.setOnClickListener(this)
		}
	}

	interface CountChangeListener {

		fun onCountChange(newCount: Int)
	}
}