package com.mrdiy.careers.ui.alerts

import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.mrdiy.careers.MainActivity
import com.mrdiy.careers.databinding.FragmentAlertsBinding
import com.mrdiy.careers.ui.base.BaseFragment
import com.mrdiy.careers.data.repository.InboxRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class AlertsFragment : BaseFragment() {

    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setName("AlertsFragment")

        (requireActivity() as MainActivity).markAlertsRead()

        binding.tvUnreadCount.text = ""
        binding.tvEmptyAlerts.text = "Loading notifications..."
        viewLifecycleOwner.lifecycleScope.launch {
            InboxRepository(requireContext()).alerts().onSuccess { alerts ->
                binding.alertsRecycler.visibility = if (alerts.isEmpty()) View.GONE else View.VISIBLE
                binding.alertsRecycler.layoutManager = LinearLayoutManager(requireContext())
                binding.alertsRecycler.setHasFixedSize(true)
                binding.alertsRecycler.setItemViewCacheSize(10)
                binding.tvUnreadCount.text = if (alerts.isEmpty()) "" else "${alerts.count { !it.isRead }} unread"
                binding.tvEmptyAlerts.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmptyAlerts.text = "No notifications yet."
                binding.alertsRecycler.adapter = AlertAdapter { alert ->
                    alert.jobId?.let { id -> androidx.navigation.fragment.NavHostFragment.findNavController(this@AlertsFragment).navigate(com.mrdiy.careers.R.id.jobDetailFragment, android.os.Bundle().apply { putString("jobId", id) }) }
                }
                (binding.alertsRecycler.adapter as AlertAdapter).submitList(alerts)
            }.onFailure { binding.alertsRecycler.visibility = View.GONE
                binding.tvEmptyAlerts.visibility = View.VISIBLE
                binding.tvEmptyAlerts.text = "Could not load notifications. Tap to retry."
                binding.tvEmptyAlerts.setOnClickListener { androidx.navigation.fragment.NavHostFragment.findNavController(this@AlertsFragment).navigate(com.mrdiy.careers.R.id.alertsFragment) } }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}