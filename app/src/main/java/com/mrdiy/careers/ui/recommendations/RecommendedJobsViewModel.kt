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
import com.mrdiy.careers.data.search.LocationDistance
import com.mrdiy.careers.data.search.LocationFilter
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
    private var hasApplicantLocation = false
    private var defaultFilterDone = false

    private val _selectedLocation = MutableLiveData<String?>()
    /** The active location filter (null = All Jobs), so a recreated screen can restore the right chip. */
    val selectedLocation: LiveData<String?> get() = _selectedLocation

    /** False when no published jobs exist at all (as opposed to none matching the current filters). */
    val hasAnyJobs = MutableLiveData(true)

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
        _selectedLocation.value = location
        applyFilters()
    }

    fun clearFilters() {
        currentSearchQuery = null
        currentJobTypeFilter = null
        currentLocationFilter = null
        _selectedLocation.value = null
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
        hasMatchingProfile.value = profile.canShowMatch
        val applicantPoint = LocationDistance.locate(profile.location)
        hasApplicantLocation = applicantPoint != null
        val matchText = if (hasMatchingProfile.value == true)
            "Scores are text similarity to your profile, not hiring probabilities."
        else "Upload your resume to see how well you match each job."
        description.value = if (applicantPoint != null)
            "Nearest branches to ${profile.location.trim()} first. $matchText"
        else "Add your location in your profile to see the nearest branches first. $matchText"

        // Matching does an expensive text-similarity pass over every job — keep it off the main thread.
        rankedResults = withContext(Dispatchers.Default) {
            val matched = HybridMatchingService.matchJobsToProfile(profile, jobs)
            if (applicantPoint == null) {
                matched
            } else {
                // Nearest first; same distance keeps the higher match score first. Unknown locations go last.
                matched.map { result ->
                    val jobPoint = LocationDistance.locate(result.job.location)
                    result.copy(distanceKm = jobPoint?.let { LocationDistance.distanceKm(applicantPoint, it) })
                }.sortedWith(
                    compareBy<com.mrdiy.careers.model.JobMatchResult> { it.distanceKm ?: Double.MAX_VALUE }
                        .thenByDescending { it.matchScore }
                )
            }
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

        val distances = rankedResults.associate { it.job.id to it.distanceKm }

        // First time jobs arrive: if the applicant has a profile location and branches are nearby,
        // start on "Near Me" so the list is filtered to their area. They can switch to All Jobs.
        if (!defaultFilterDone && allJobs.isNotEmpty()) {
            defaultFilterDone = true
            if (currentLocationFilter.isNullOrEmpty() &&
                distances.values.any { it != null && it <= LocationFilter.NEAR_ME_RADIUS_KM }
            ) {
                currentLocationFilter = LocationFilter.NEAR_ME
                _selectedLocation.value = LocationFilter.NEAR_ME
            }
        }

        if (!currentLocationFilter.isNullOrEmpty()) {
            filtered = filtered.filter { job ->
                LocationFilter.matches(currentLocationFilter, job.location, distances[job.id])
            }
        }

        hasAnyJobs.value = allJobs.isNotEmpty()
        _filteredJobs.value = filtered
        val ids = filtered.map { it.id }.toSet()
        matchResults.value = rankedResults.filter { it.job.id in ids }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        searchJob?.cancel()
    }

    fun currentLocationFilter(): String? = currentLocationFilter

    fun hasApplicantLocation(): Boolean = hasApplicantLocation
}
