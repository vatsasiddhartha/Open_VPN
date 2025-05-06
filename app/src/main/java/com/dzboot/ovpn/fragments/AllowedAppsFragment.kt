package com.dzboot.ovpn.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.DividerItemDecoration
import com.dzboot.ovpn.BuildConfig
import com.dzboot.ovpn.R
import com.dzboot.ovpn.adapters.AppsUsingAdapter
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.data.models.Application
import com.dzboot.ovpn.databinding.FragmentAllowedAppsBinding
import com.dzboot.ovpn.helpers.PrefsHelper
import com.dzboot.ovpn.helpers.gone
import com.dzboot.ovpn.helpers.visible
import com.dzboot.ovpn.activities.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class AllowedAppsFragment : BaseFragment<MainActivity, FragmentAllowedAppsBinding>(), TextWatcher,
    AppsUsingAdapter.CountChangeListener {

    companion object {
        const val STATIC_TAG = "AllowedAppsFragment"
    }

    override val TAG = STATIC_TAG

    private val adapter: AppsUsingAdapter = AppsUsingAdapter(this)


    override fun getPageTitle() = R.string.allowed_apps

    override fun initializeBinding(): FragmentAllowedAppsBinding =
        FragmentAllowedAppsBinding.inflate(requireActivity().layoutInflater)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvAppsList.setHasFixedSize(true)
        binding.rvAppsList.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        binding.enableAll.setOnClickListener { adapter.enableAll() }
        binding.disableAll.setOnClickListener { adapter.disableAll() }
        binding.searchBar.addTextChangeListener(this)

        CoroutineScope(Dispatchers.Default).launch {
            onAppsLoaded(getApps())
        }
    }

    private suspend fun onAppsLoaded(apps: ArrayList<Application>) {
        if (activity == null || !isAdded)
            return

        withContext(Dispatchers.Main) {
            adapter.setApps(apps)
            binding.rvAppsList.adapter = adapter
            binding.pbLoading.gone()
            binding.tvLoading.gone()
            binding.container.visible()
            binding.sActiveApps.text = getString(R.string.active_apps_count, adapter.appsUsingCount, apps.size)
        }
    }

    override fun onCountChange(newCount: Int) {
        binding.sActiveApps.text = getString(R.string.active_apps_count, newCount, adapter.allItemsCount)
    }

    fun hasConfigChanged() = adapter.hasConfigChanged()

    @SuppressLint("QueryPermissionsNeeded")
    private fun getApps(): ArrayList<Application> {
        val pm = activity?.packageManager
        val apps: ArrayList<Application> = ArrayList()

        if (pm == null)
            return apps

        for (packageInfo in pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            if (packageInfo.packageName == BuildConfig.APPLICATION_ID || packageInfo.requestedPermissions == null) continue
            for (permission in packageInfo.requestedPermissions) {
                if (TextUtils.equals(permission, Manifest.permission.INTERNET)) {
                    val name = pm.getApplicationLabel(packageInfo.applicationInfo).toString()
                    val icon = pm.getApplicationIcon(packageInfo.applicationInfo)
                    apps.add(Application(name, packageInfo.packageName, icon))
                    break
                }
            }
        }

        apps.sortWith { o1, o2 -> o1.name.compareTo(o2.name) }
        val inactiveApps: Set<String> = PrefsHelper.getAppsNotUsing()
        if (inactiveApps.isEmpty())
            return apps

        for (app in apps)
            if (inactiveApps.contains(app.packageName))
                app.isActive = false

        return apps
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = adapter.filter.filter(s)

    override fun afterTextChanged(s: Editable?) {
    }
}