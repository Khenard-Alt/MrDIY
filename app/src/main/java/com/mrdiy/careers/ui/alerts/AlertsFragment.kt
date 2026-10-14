package com.mrdiy.careers.ui.alerts

import android.os.Bundle
import android.view.*
import androidx.recyclerview.widget.LinearLayoutManager
import com.mrdiy.careers.MainActivity
import com.mrdiy.careers.R
import com.mrdiy.careers.model.Alert
import com.mrdiy.careers.model.AlertType
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
                binding.tvUnreadCount.text = if (alerts.isEmpty()) "" else "${alerts.count { !it.isRead }} unread"
                binding.tvEmptyAlerts.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmptyAlerts.text = "No notifications yet."
                binding.alertsRecycler.adapter = AlertAdapter { alert -> openAlert(alert) }
                (binding.alertsRecycler.adapter as AlertAdapter).submitList(alerts)
                // The list above still shows which ones were new; the bell clears for next time.
                if (alerts.any { !it.isRead }) InboxRepository(requireContext()).markAllAlertsRead()
            }.onFailure { binding.alertsRecycler.visibility = View.GONE
                binding.tvEmptyAlerts.visibility = View.VISIBLE
                binding.tvEmptyAlerts.text = "Could not load notifications. Tap to retry."
                binding.tvEmptyAlerts.setOnClickListener { androidx.navigation.fragment.NavHostFragment.findNavController(this@AlertsFragment).navigate(com.mrdiy.careers.R.id.alertsFragment) } }
        }
    }

    /**
     * Opens what the notification is about: the specific application (if this alert has one —
     * older alerts, from before application_id was added to public.alerts, won't), the job, or
     * the messages. Previously APPLICATION_UPDATE/INTERVIEW_INVITE always opened the generic
     * Applications list, leaving the applicant to hunt for the right one themselves — e.g. tapping
     * "Clearance complete" or "Resignation recorded" never landed on that specific application.
     */
    private fun openAlert(alert: Alert) {
        val nav = androidx.navigation.fragment.NavHostFragment.findNavController(this)
        when (alert.type) {
            AlertType.MESSAGE -> nav.navigate(R.id.messagesFragment)
            AlertType.APPLICATION_UPDATE, AlertType.INTERVIEW_INVITE -> {
                val applicationId = alert.applicationId
                if (applicationId != null) {
                    nav.navigate(
                        R.id.applicationDetailFragment,
                        android.os.Bundle().apply {
                            putString(
                                com.mrdiy.careers.ui.applications.ApplicationDetailFragment.ARG_APPLICATION_ID,
                                applicationId
                            )
                        }
                    )
                } else {
                    nav.navigate(R.id.applicationsFragment)
                }
            }
            else -> alert.jobId?.let { id ->
                nav.navigate(R.id.jobDetailFragment, android.os.Bundle().apply { putString("jobId", id) })
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}