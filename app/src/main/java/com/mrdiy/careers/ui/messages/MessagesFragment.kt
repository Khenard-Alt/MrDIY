package com.mrdiy.careers.ui.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mrdiy.careers.databinding.FragmentMessagesBinding
import com.mrdiy.careers.model.Message
import com.mrdiy.careers.data.repository.InboxRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.messagesRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.tvUnreadCount.text = "Loading messages..."
        viewLifecycleOwner.lifecycleScope.launch {
            InboxRepository(requireContext()).messages().onSuccess { messages ->
                if (_binding != null) renderMessages(messages)
            }.onFailure {
                if (_binding != null) binding.tvUnreadCount.text = "No messages yet"
            }
        }
    }

    private fun renderMessages(messages: List<Message>) {
        val adapter = MessageAdapter(messages) { message ->
            message.jobId?.let { jobId ->
                val action = MessagesFragmentDirections.actionMessagesToJobDetail(jobId)
                findNavController().navigate(action)
            }
        }

        binding.messagesRecycler.adapter = adapter

        val unreadCount = messages.count { !it.isRead }
        binding.tvUnreadCount.text = if (messages.isEmpty()) "No messages yet" else if (unreadCount > 0) "$unreadCount unread" else "All messages read"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}