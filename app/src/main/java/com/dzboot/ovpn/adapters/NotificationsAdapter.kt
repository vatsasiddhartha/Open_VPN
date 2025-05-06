package com.dzboot.ovpn.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dzboot.ovpn.R
import com.dzboot.ovpn.custom.ContextViewHolder
import com.dzboot.ovpn.data.models.Notification
import com.dzboot.ovpn.databinding.ItemNotificationBinding
import com.google.android.material.snackbar.Snackbar
import org.ocpsoft.prettytime.PrettyTime
import java.util.*


class NotificationsAdapter(
    private val notifications: ArrayList<Notification>,
    private val deleteNotification: (Long) -> Unit,
    private val addNotification: (Notification) -> Unit,
) :
    RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(notifications[position]) {
            holder.binding.title.text = if (read) title else holder.getString(R.string.new_notification, title)
            holder.binding.body.text = body
            holder.binding.received.text = PrettyTime(Locale.getDefault()).format(Date(receiveTime))
        }
    }

    override fun getItemCount() = notifications.size

    inner class ViewHolder(parent: ViewGroup) : ContextViewHolder<ItemNotificationBinding>(
        ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    ) {

        init {
            binding.delete.setOnClickListener {
                adapterPosition.let { position ->
                    val notification = notifications[position]
                    deleteNotification(notification.id)
                    notifications.removeAt(position)
                    notifyItemRemoved(position)

                    Snackbar.make(itemView, R.string.undo_delete_notification, Snackbar.LENGTH_SHORT)
                        .setAction(R.string.undo) {
                            addNotification(notification)
                            notifications.add(position, notification)
                            notifyItemInserted(position)
                        }.show()
                }
            }
        }
    }
}