package com.mrdiy.careers.ui.applications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mrdiy.careers.R
import com.mrdiy.careers.data.repository.ApplicationsRepository
import com.mrdiy.careers.databinding.FragmentApplicationsBinding
import kotlinx.coroutines.launch

class ApplicationsFragment : Fragment() {
    private var _binding: FragmentApplicationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: ApplicationsRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentApplicationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = ApplicationsRepository(requireContext())
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
        binding.applicationsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            setItemViewCacheSize(10)
        }
        loadApplications()
    }

    private fun loadApplications() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            repository.observeApplicationsRealtime().collect { result ->
                if (!isAdded || _binding == null) return@collect
                binding.progressBar.visibility = View.GONE
                result.onSuccess { applications ->
                    binding.tvEmpty.visibility = if (applications.isEmpty()) View.VISIBLE else View.GONE
                    binding.applicationsList.adapter = ApplicationsAdapter(applications)
                }.onFailure { error ->
                    Toast.makeText(requireContext(), "Could not load applications. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private class ApplicationsAdapter(
    private val applications: List<ApplicationsRepository.ApplicationRow>
) : androidx.recyclerview.widget.RecyclerView.Adapter<ApplicationsAdapter.Holder>() {
    class Holder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val title = view.findViewById<android.widget.TextView>(R.id.tv_title)
        val status = view.findViewById<android.widget.TextView>(R.id.tv_status)
        val date = view.findViewById<android.widget.TextView>(R.id.tv_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_application, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = applications[position]
        holder.title.text = item.job_title
        holder.status.text = "Status: ${item.status}"
        holder.date.text = if (item.applied_at.isBlank()) "" else "Applied ${item.applied_at}"
    }

    override fun getItemCount() = applications.size
}
