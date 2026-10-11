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
import com.mrdiy.careers.data.search.LocationDistance
import com.mrdiy.careers.data.search.LocationFilter
import com.mrdiy.careers.databinding.FragmentRecommendedJobsBinding
import com.mrdiy.careers.ui.alerts.refreshNotificationBadge
import com.mrdiy.careers.ui.base.setAdapterWithHeader

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

        binding.btnClearFilters.setOnClickListener {
            binding.chipAll.isChecked = true
            viewModel.clearFilters()
            binding.searchInput.text?.clear()
        }
    }

    private fun updateEmptyState() {
        val empty = viewModel.matchResults.value.orEmpty().isEmpty() && viewModel.isLoading.value != true
        if (empty) showEmptyState()
        binding.emptyGroup.visibility = if (empty) View.VISIBLE else View.GONE
    }

    /** Picks the empty-state wording for why the list is empty instead of always suggesting a resume. */
    private fun showEmptyState() {
        val noJobsAtAll = viewModel.hasAnyJobs.value == false
        val nearMeWithoutLocation =
            viewModel.currentLocationFilter() == LocationFilter.NEAR_ME && !viewModel.hasApplicantLocation()
        when {
            noJobsAtAll -> {
                binding.tvEmptyTitle.text = "No jobs are open right now"
                binding.tvEmptyBody.text = "New branch openings will show up here. Please check back soon."
            }
            nearMeWithoutLocation -> {
                binding.tvEmptyTitle.text = "Add your location"
                binding.tvEmptyBody.text = "Set your Location in Edit Profile so we can find branches near you."
            }
            else -> {
                binding.tvEmptyTitle.text = "No jobs match your filters"
                binding.tvEmptyBody.text = "Try another location or clear the filters to see all jobs."
            }
        }
        binding.btnClearFilters.visibility = if (noJobsAtAll) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        refreshNotificationBadge(binding.notificationBadge)
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

        binding.recommendedJobsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recommendedJobsRecycler.setAdapterWithHeader(binding.listHeader, jobAdapter)
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

        mapOf(
            binding.chipNearMe to LocationFilter.NEAR_ME,
            binding.chipManila to LocationDistance.METRO_MANILA,
            binding.chipRizal to LocationDistance.RIZAL,
            binding.chipCavite to LocationDistance.CAVITE,
            binding.chipLaguna to LocationDistance.LAGUNA,
            binding.chipBulacan to LocationDistance.BULACAN
        ).forEach { (chip, area) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) viewModel.onLocationFilter(area)
            }
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
        viewModel.selectedLocation.observe(viewLifecycleOwner) { filter ->
            val chip = when (filter) {
                LocationFilter.NEAR_ME -> binding.chipNearMe
                LocationDistance.METRO_MANILA -> binding.chipManila
                LocationDistance.RIZAL -> binding.chipRizal
                LocationDistance.CAVITE -> binding.chipCavite
                LocationDistance.LAGUNA -> binding.chipLaguna
                LocationDistance.BULACAN -> binding.chipBulacan
                else -> binding.chipAll
            }
            if (!chip.isChecked) chip.isChecked = true
        }
        viewModel.matchResults.observe(viewLifecycleOwner) { jobs ->
            jobAdapter.updateResults(jobs)
            updateEmptyState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            // Background refreshes keep the current results on screen instead of the spinner.
            val hasResults = viewModel.matchResults.value.orEmpty().isNotEmpty()
            binding.loadingGroup.visibility = if (loading && !hasResults) View.VISIBLE else View.GONE
            updateEmptyState()
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
