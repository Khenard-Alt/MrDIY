package com.mrdiy.careers.ui.savedjobs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.mrdiy.careers.R
import com.mrdiy.careers.databinding.FragmentSavedJobsBinding
import com.mrdiy.careers.model.Job
import com.mrdiy.careers.ui.home.HomeFragmentDirections

class SavedJobsFragment : Fragment() {

    private var _binding: FragmentSavedJobsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavedJobsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedJobsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()

        viewModel.loadSavedJobs()
    }

    private fun setupRecyclerView() {
        binding.savedJobsRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
            setItemViewCacheSize(12)
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnRetry.setOnClickListener {
            viewModel.loadSavedJobs()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.loadingGroup.visibility = View.GONE
            binding.emptyGroup.visibility = View.GONE
            binding.errorGroup.visibility = View.GONE
            binding.contentGroup.visibility = View.GONE

            when (state) {
                is SavedJobsUiState.Loading -> {
                    binding.loadingGroup.visibility = View.VISIBLE
                }
                is SavedJobsUiState.Success -> {
                    binding.contentGroup.visibility = View.VISIBLE
                    val adapter = SavedJobAdapter(
                        jobs = state.jobs,
                        onJobClick = { job ->
                            val action = SavedJobsFragmentDirections.actionSavedJobsToJobDetail(job.id)
                            findNavController().navigate(action)
                        },
                        onRemoveClick = { job ->
                            viewModel.unsaveJob(job.id)
                        }
                    )
                    binding.savedJobsRecycler.adapter = adapter
                }
                is SavedJobsUiState.Empty -> {
                    binding.emptyGroup.visibility = View.VISIBLE
                }
                is SavedJobsUiState.Error -> {
                    binding.errorGroup.visibility = View.VISIBLE
                    binding.tvErrorMessage.text = state.message
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadSavedJobs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}