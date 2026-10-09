package com.mrdiy.careers.ui.home

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
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.repository.ProfileRepository
import com.mrdiy.careers.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var jobAdapter: JobAdapter
    private lateinit var authManager: AuthManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        authManager = AuthManager(requireContext())

        setupRecyclerView()
        setupUserGreeting()
        setupSearchBar()
        setupFilterChips()
        setupHeaderActions()
        observeViewModel()

        // Load jobs from API
        viewModel.fetchJobsFromApi()
    }

    override fun onResume() {
        super.onResume()
        setupUserGreeting()

        viewModel.loadSavedJobIds()
        viewModel.fetchJobsFromApi()
    }

    // ───────────────── Greeting ─────────────────

    private fun setupUserGreeting() {
        val uid = authManager.getCurrentUserId() ?: return
        val repo = ProfileRepository(requireContext())
        fun display(name: String) {
            if (_binding == null || authManager.getCurrentUserId() != uid) return
            binding.tvGreeting.text = com.mrdiy.careers.data.auth.AccountRules.greeting(name)
            binding.tvUserName.visibility = View.GONE
        }
        display(repo.loadFromPrefs(uid).fullName.ifBlank { authManager.getCurrentUserName() })
        repo.loadProfile(uid) { profile ->
            if (_binding != null && authManager.getCurrentUserId() == uid) {
                display(profile?.fullName?.ifBlank { authManager.getCurrentUserName() }.orEmpty())
                jobAdapter.updateProfile(profile)
            }
        }
    }

    // ───────────────── RecyclerView ─────────────────

    private fun setupRecyclerView() {

        jobAdapter = JobAdapter(

            jobs = emptyList(),

            onJobClick = { job ->

                val action =
                    HomeFragmentDirections
                        .actionHomeToJobDetail(
                            job.id
                        )

                findNavController()
                    .navigate(action)
            },

            onSaveClick = { job ->

                viewModel.toggleSaveJob(job)
            }
        )

        binding.jobsRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = jobAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(12)
        }
    }

    // ───────────────── Search ─────────────────

    private fun setupSearchBar() {

        binding.searchInput
            .addTextChangedListener(
                object : TextWatcher {

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) = Unit

                    override fun afterTextChanged(
                        s: Editable?
                    ) {
                        viewModel.onSearchQuery(
                            s?.toString()
                        )
                    }
                }
            )

        binding.btnFilter.setOnClickListener {

            binding.chipAll.isChecked = true
            viewModel.clearFilters()

            binding.searchInput
                .text
                ?.clear()
        }
    }

    // ───────────────── Filter Chips ─────────────────

    private fun setupFilterChips() {

        binding.chipAll
            .setOnCheckedChangeListener {
                    _, isChecked ->

                if (isChecked) {

                    viewModel.onJobTypeFilter(
                        null
                    )

                    viewModel.onLocationFilter(
                        null
                    )
                }
            }

        binding.chipFulltime
            .setOnCheckedChangeListener {
                    _, isChecked ->

                if (isChecked) {
                    viewModel.onJobTypeFilter(
                        "Full-time"
                    )
                }
            }

        binding.chipParttime
            .setOnCheckedChangeListener {
                    _, isChecked ->

                if (isChecked) {
                    viewModel.onJobTypeFilter(
                        "Part-time"
                    )
                }
            }

        binding.chipManila
            .setOnCheckedChangeListener {
                    _, isChecked ->

                if (isChecked) {
                    viewModel.onLocationFilter(
                        "Metro Manila"
                    )
                }
            }

    }

    // ───────────────── Header Actions ─────────────────

    private fun setupHeaderActions() {

        binding.btnNotifications
            .setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.alertsFragment
                    )
            }

        binding.btnMessages
            .setOnClickListener {

                findNavController()
                    .navigate(
                        R.id.messagesFragment
                    )
            }
    }

    // ───────────────── Observers ─────────────────

    private fun observeViewModel() {
        viewModel.appliedCount.observe(viewLifecycleOwner) { binding.tvStatApplied.text = it.toString() }
        binding.tvStatApplied.setOnClickListener { findNavController().navigate(R.id.applicationsFragment) }
        binding.tvStatSaved.setOnClickListener { findNavController().navigate(R.id.savedJobsFragment) }

        viewModel.filteredJobs.observe(
            viewLifecycleOwner
        ) { jobs ->
            android.util.Log.d("HomeFragment", "filteredJobs changed: ${jobs.size} jobs")
            jobAdapter.updateList(jobs)

            binding.tvEmptyState.visibility =
                if (jobs.isEmpty())
                    View.VISIBLE
                else
                    View.GONE
            android.util.Log.d("HomeFragment", "tvEmptyState visibility: ${binding.tvEmptyState.visibility}")
        }

        viewModel.savedJobIds.observe(
            viewLifecycleOwner
        ) { savedIds ->

            jobAdapter.updateSavedJobs(savedIds)
        }

        viewModel.savedCount.observe(
            viewLifecycleOwner
        ) { count ->

            binding.tvStatSaved.text =
                count.toString()
        }

        // ADD THIS

        viewModel.isLoading.observe(
            viewLifecycleOwner
        ) { loading ->

            binding.progressBar.visibility =
                if (loading)
                    View.VISIBLE
                else
                    View.GONE
        }

        viewModel.errorMessage.observe(
            viewLifecycleOwner
        ) { message ->

            if (!message.isNullOrBlank()) {

                Snackbar.make(
                    binding.root,
                    message,
                    Snackbar.LENGTH_LONG
                )
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
