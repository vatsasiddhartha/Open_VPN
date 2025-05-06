package com.dzboot.ovpn.fragments

import android.os.Bundle
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.dzboot.ovpn.R
import com.dzboot.ovpn.adapters.NotificationsAdapter
import com.dzboot.ovpn.base.BaseFragment
import com.dzboot.ovpn.data.db.AppDB
import com.dzboot.ovpn.data.models.Notification
import com.dzboot.ovpn.databinding.FragmentNotificationsBinding
import com.dzboot.ovpn.helpers.gone
import com.dzboot.ovpn.helpers.visible
import com.dzboot.ovpn.activities.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class NotificationsFragment : BaseFragment<MainActivity, FragmentNotificationsBinding>() {

    companion object {

        const val STATIC_TAG = "NotificationsFragment"
    }

    override val TAG = STATIC_TAG

    override fun initializeBinding() = FragmentNotificationsBinding.inflate(requireActivity().layoutInflater)

    override fun getPageTitle() = R.string.notifications

    private val notificationsDao by lazy { AppDB.getInstance(requireContext()).notificationsDao() }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateNotifications()
    }

    fun updateNotifications() = lifecycleScope.launch(Dispatchers.IO) {
        val notifications = notificationsDao.getAll() as ArrayList<Notification>
        withContext(Dispatchers.Main) {
            binding.loading.gone()
            if (notifications.isEmpty()) {
                binding.notifications.gone()
                binding.noNotifications.visible()
            } else {
                binding.notifications.adapter = NotificationsAdapter(notifications,
                    {
                        lifecycleScope.launch(Dispatchers.IO) {
                            notificationsDao.deleteNotification(it)
                            NotificationManagerCompat.from(requireContext()).cancel(it.toInt())
                        }
                    },
                    {
                        lifecycleScope.launch(Dispatchers.IO) {
                            notificationsDao.insertNotification(it)
                        }
                    }
                )
                binding.notifications.visible()
                binding.noNotifications.gone()
            }
        }
        notificationsDao.setAllRead()
        activity?.setNotificationsCount(0)
    }
}