package com.mrdiy.careers.ui.recommendations

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.FragmentRecommendedJobsBinding

class RecommendedJobsFragment : Fragment() {

    private var _binding: FragmentRecommendedJobsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecommendedJobsViewModel by viewModels()
    private lateinit var jobAdapter: RecommendedJobAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecommendedJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchBar()
        setupFilterChips()
        setupHeaderActions()
        observeViewModel()

        binding.btnUploadResume.setOnClickListener {
            findNavController().navigate(R.id.action_recommendations_to_resumeUpload)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSavedJobIds()
        viewModel.loadRecommendations()
    }

    private fun setupRecyclerView() {
        jobAdapter = RecommendedJobAdapter(
            results = emptyList(),
            onJobClick = { job ->
                val action = RecommendedJobsFragmentDirections.actionRecommendationsToJobDetail(job.id)
                findNavController().navigate(action)
            }
        )

        binding.recommendedJobsRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = jobAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(12)
        }
    }

    private fun setupSearchBar() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.onSearchQuery(s?.toString())
            }
        })

        binding.btnFilter.setOnClickListener {
            binding.chipAll.isChecked = true
            viewModel.clearFilters()
            binding.searchInput.text?.clear()
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.onJobTypeFilter(null)
                viewModel.onLocationFilter(null)
            }
        }

        binding.chipFulltime.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.onJobTypeFilter("Full-time")
        }

        binding.chipParttime.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.onJobTypeFilter("Part-time")
        }

        binding.chipManila.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.onLocationFilter("Metro Manila")
        }

    }

    private fun setupHeaderActions() {
        binding.btnNotifications.setOnClickListener {
            findNavController().navigate(R.id.alertsFragment)
        }

        binding.btnMessages.setOnClickListener {
            findNavController().navigate(R.id.messagesFragment)
        }
    }

    private fun observeViewModel() {
        viewModel.description.observe(viewLifecycleOwner) { binding.tvAiDescription.text = it }
        viewModel.matchResults.observe(viewLifecycleOwner) { jobs ->
            jobAdapter.updateResults(jobs)
            binding.emptyGroup.visibility = if (jobs.isEmpty() && viewModel.isLoading.value != true) View.VISIBLE else View.GONE
        }



        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.loadingGroup.visibility = if (loading) View.VISIBLE else View.GONE
            binding.emptyGroup.visibility = if (!loading && viewModel.matchResults.value.orEmpty().isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        viewModel.fetchJobsFromApi()
                    }
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
