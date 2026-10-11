package com.mrdiy.careers.ui.recommendations

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mrdiy.careers.data.repository.PublishedJobsRepository
import com.mrdiy.careers.data.repository.SavedJobsRepository
import com.mrdiy.careers.data.repository.ProfileRepository
import com.mrdiy.careers.data.ml.HybridMatchingService
import com.mrdiy.careers.data.search.MetroManilaScope
import com.mrdiy.careers.model.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as KJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecommendedJobsViewModel(application: Application) : AndroidViewModel(application) {

    private val savedJobsRepository = SavedJobsRepository(application)
    private val publishedJobsRepository = PublishedJobsRepository()
    private val profileRepository = ProfileRepository(application)

    val description = MutableLiveData<String>()
    val hasMatchingProfile = MutableLiveData(false)
    val matchResults = MutableLiveData<List<com.mrdiy.careers.model.JobMatchResult>>()
    private var rankedResults = emptyList<com.mrdiy.careers.model.JobMatchResult>()

    private var allJobs: List<Job> = emptyList()

    private val _filteredJobs = MutableLiveData<List<Job>>()
    val filteredJobs: LiveData<List<Job>> get() = _filteredJobs

    private val _savedJobIds = MutableLiveData<Set<String>>()
    val savedJobIds: LiveData<Set<String>> get() = _savedJobIds

    private val _savedCount = MutableLiveData<Int>()
    val savedCount: LiveData<Int> get() = _savedCount

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    private var currentSearchQuery: String? = null
    private var currentJobTypeFilter: String? = null
    private var currentLocationFilter: String? = null

    private var autoRefreshJob: KJob? = null
    private var searchJob: KJob? = null
    private var fetchJob: KJob? = null

    init {
        _filteredJobs.value = emptyList()
        loadSavedJobIds()
        fetchJobsFromApi()
        startAutoRefresh()
    }

    fun loadRecommendations() {
        fetchJobsFromApi()
    }

    fun fetchJobsFromApi() {
        if (fetchJob?.isActive == true) return
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            publishedJobsRepository.fetchOpenJobs()
                .onSuccess { apiJobs ->
                    Log.d("RecommendedJobsVM", "API returned ${apiJobs.size} jobs")
                    allJobs = rankJobs(apiJobs.filter(MetroManilaScope::contains))
                    _isLoading.value = false
                    applyFilters()
                }
                .onFailure { e ->
                    com.mrdiy.careers.data.SafeDiagnostics.record("backend_request", e)
                    _isLoading.value = false
                    allJobs = emptyList()
                    _errorMessage.value = "Unable to load recommendations. Please retry."
                    applyFilters()
                }
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(60_000)
                fetchJobsFromApi()
            }
        }
    }

    fun loadSavedJobIds() {
        viewModelScope.launch {
            val ids = savedJobsRepository.getSavedJobIds()
            _savedJobIds.value = ids
            _savedCount.value = ids.size
        }
    }

    fun onSearchQuery(query: String?) {
        currentSearchQuery = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            applyFilters()
        }
    }

    fun onJobTypeFilter(jobType: String?) {
        currentJobTypeFilter = jobType
        applyFilters()
    }

    fun onLocationFilter(location: String?) {
        currentLocationFilter = location
        applyFilters()
    }

    fun clearFilters() {
        currentSearchQuery = null
        currentJobTypeFilter = null
        currentLocationFilter = null
        applyFilters()
    }

    fun saveJob(job: Job) {
        viewModelScope.launch {
            savedJobsRepository.saveJob(job)
            loadSavedJobIds()
        }
    }

    fun unsaveJob(job: Job) {
        viewModelScope.launch {
            savedJobsRepository.unsaveJob(job.id)
            loadSavedJobIds()
        }
    }

    fun toggleSaveJob(job: Job) {
        viewModelScope.launch {
            val isSaved = savedJobsRepository.isJobSaved(job.id)
            if (isSaved) unsaveJob(job) else saveJob(job)
        }
    }

    fun getSavedCount(): Int = _savedCount.value ?: 0

    private suspend fun rankJobs(jobs: List<Job>): List<Job> {
        val profile = profileRepository.loadFromPrefs()
        hasMatchingProfile.value = profile.skills.isNotEmpty() || profile.desiredPosition.isNotBlank() ||
            profile.about.isNotBlank() || profile.headline.isNotBlank() || profile.workExperiences.isNotEmpty() || profile.resumeText.isNotBlank()
        description.value = if (hasMatchingProfile.value == true)
            "Published jobs ranked by similarity to your profile. Scores are text similarity, not hiring probabilities."
        else "Add your skills or desired position, or upload a resume to personalize these jobs."

        // Matching does an expensive text-similarity pass over every job — keep it off the main thread.
        rankedResults = withContext(Dispatchers.Default) {
            HybridMatchingService.matchJobsToProfile(profile, jobs)
        }
        return rankedResults.map { it.job }
    }

    private fun applyFilters() {
        var filtered = allJobs

        if (!currentSearchQuery.isNullOrEmpty()) {
            filtered = filtered.filter { job ->
                job.title.contains(currentSearchQuery!!, ignoreCase = true) ||
                job.company.contains(currentSearchQuery!!, ignoreCase = true) ||
                job.branch.contains(currentSearchQuery!!, ignoreCase = true)
            }
        }

        if (!currentJobTypeFilter.isNullOrEmpty()) {
            filtered = filtered.filter { job ->
                job.jobType.equals(currentJobTypeFilter, ignoreCase = true)
            }
        }

        if (!currentLocationFilter.isNullOrEmpty()) {
            filtered = filtered.filter { job ->
                if (currentLocationFilter == "Metro Manila") MetroManilaScope.isMetro(job.location)
                else job.location.contains(currentLocationFilter!!, ignoreCase = true)
            }
        }

        _filteredJobs.value = filtered
        val ids = filtered.map { it.id }.toSet()
        matchResults.value = rankedResults.filter { it.job.id in ids }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        searchJob?.cancel()
    }
}
