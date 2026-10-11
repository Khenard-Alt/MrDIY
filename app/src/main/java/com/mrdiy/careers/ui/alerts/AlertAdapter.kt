package com.mrdiy.careers.ui.alerts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.ItemAlertBinding
import com.mrdiy.careers.model.Alert
import com.mrdiy.careers.model.AlertType

class AlertAdapter(
    private val onAlertClick: (Alert) -> Unit
) : ListAdapter<Alert, AlertAdapter.AlertViewHolder>(AlertDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(private val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: Alert) {
            binding.tvAlertTitle.text = alert.title
            binding.tvAlertMessage.text = alert.message
            binding.tvAlertTime.text = alert.timeAgo
            binding.viewUnreadDot.visibility = if (alert.isRead) View.GONE else View.VISIBLE

            val (icon, bgColor) = when (alert.type) {
                AlertType.INTERVIEW_INVITE -> "📅" to R.color.orange_light
                AlertType.APPLICATION_UPDATE -> "📄" to R.color.blue_light
                AlertType.JOB_MATCH -> "🎯" to R.color.green_success_light
                AlertType.NEW_JOB -> "🆕" to R.color.orange_light
                AlertType.NEARBY_JOB -> "📍" to R.color.green_success_light
                AlertType.MESSAGE -> "💬" to R.color.blue_light
                AlertType.SYSTEM -> "🔔" to R.color.orange_light
            }
            binding.tvAlertIcon.text = icon
            binding.tvAlertIcon.setBackgroundResource(bgColor)

            binding.root.setOnClickListener { onAlertClick(alert) }
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Alert, newItem: Alert) = oldItem == newItem
    }
}
