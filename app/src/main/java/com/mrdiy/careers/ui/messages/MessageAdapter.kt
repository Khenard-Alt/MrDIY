package com.mrdiy.careers.ui.messages

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.ItemMessageBinding
import com.mrdiy.careers.model.Message

class MessageAdapter(
    private val messages: List<Message>,
    private val onMessageClick: (Message) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            binding.tvSenderInitial.text = message.senderAvatar
            binding.tvSenderName.text = message.senderName
            binding.tvLastMessage.text = message.lastMessage
            binding.tvTimeAgo.text = message.timeAgo

            if (message.isRead) {
                binding.tvSenderName.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_primary))
                binding.tvLastMessage.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_secondary))
            } else {
                binding.tvSenderName.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_primary))
                binding.tvLastMessage.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_primary))
                binding.tvSenderName.textSize = 16f
                binding.tvLastMessage.textSize = 14f
            }

            if (message.unreadCount > 0) {
                binding.tvUnreadCount.visibility = android.view.View.VISIBLE
                binding.tvUnreadCount.text = message.unreadCount.toString()
            } else {
                binding.tvUnreadCount.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onMessageClick(message) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size
}