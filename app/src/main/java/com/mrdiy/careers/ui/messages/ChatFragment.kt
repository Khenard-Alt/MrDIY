package com.mrdiy.careers.ui.messages

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.R
import com.mrdiy.careers.data.repository.InboxRepository
import com.mrdiy.careers.databinding.FragmentChatBinding
import com.mrdiy.careers.databinding.ItemChatMessageBinding
import kotlinx.coroutines.launch

/** A conversation with MR.D.I.Y. HR: read their messages and reply. */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val otherId: String by lazy { requireArguments().getString(ARG_OTHER_ID).orEmpty() }
    private val adapter = ChatAdapter()
    private var jobId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.chatRecycler.adapter = adapter
        binding.btnSend.setOnClickListener { send() }

        // The app draws edge to edge, so the system no longer resizes the screen for the keyboard.
        // Lift the whole chat (including the reply box) by the keyboard's height instead.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            root.updatePadding(bottom = keyboard)
            if (keyboard > 0 && adapter.itemCount > 0) {
                binding.chatRecycler.post { binding.chatRecycler.scrollToPosition(adapter.itemCount - 1) }
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = InboxRepository(requireContext())
            repository.thread(otherId).onSuccess { messages ->
                if (_binding == null) return@onSuccess
                adapter.submit(messages)
                binding.tvChatStatus.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                binding.tvChatStatus.text = "No messages yet."
                if (messages.isNotEmpty()) binding.chatRecycler.scrollToPosition(messages.size - 1)
            }.onFailure {
                if (_binding != null) {
                    binding.tvChatStatus.visibility = View.VISIBLE
                    binding.tvChatStatus.text = "Could not load messages. Please try again."
                }
            }
            jobId = repository.lastJobId(otherId)
            repository.markThreadRead(otherId)
        }
    }

    private fun send() {
        val text = binding.etMessage.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        binding.btnSend.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val sent = InboxRepository(requireContext()).sendMessage(otherId, jobId, text)
            if (_binding == null) return@launch
            binding.btnSend.isEnabled = true
            if (sent) {
                binding.etMessage.text?.clear()
                load()
            } else {
                android.widget.Toast.makeText(requireContext(), "Could not send. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_OTHER_ID = "otherId"
    }
}

private class ChatAdapter : RecyclerView.Adapter<ChatAdapter.Holder>() {
    private var items: List<InboxRepository.ChatMessage> = emptyList()

    class Holder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(messages: List<InboxRepository.ChatMessage>) {
        items = messages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val message = items[position]
        val context = holder.binding.root.context
        val gravity = if (message.mine) Gravity.END else Gravity.START

        holder.binding.tvBubble.text = message.body
        holder.binding.tvBubble.setBackgroundResource(if (message.mine) R.drawable.bg_chat_mine else R.drawable.bg_chat_theirs)
        holder.binding.tvBubble.setTextColor(
            ContextCompat.getColor(context, if (message.mine) R.color.white else R.color.text_primary)
        )
        holder.binding.tvTime.text = message.time

        listOf(holder.binding.tvBubble, holder.binding.tvTime).forEach { view ->
            (view.layoutParams as LinearLayout.LayoutParams).gravity = gravity
            view.requestLayout()
        }
    }

    override fun getItemCount() = items.size
}
