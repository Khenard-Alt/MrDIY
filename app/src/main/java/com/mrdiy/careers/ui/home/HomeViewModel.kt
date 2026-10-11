package com.mrdiy.careers.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mrdiy.careers.data.repository.PublishedJobsRepository
import com.mrdiy.careers.data.repository.SavedJobsRepository
import com.mrdiy.careers.model.Job
import com.mrdiy.careers.data.search.UniversalJobSearchEngine
import com.mrdiy.careers.data.search.LocationFilter
import com.mrdiy.careers.data.search.MetroManilaScope
import kotlinx.coroutines.Job as KJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val appliedCount = MutableLiveData<Int>()
    private val applicationsRepository = com.mrdiy.careers.data.repository.ApplicationsRepository(application)
    private val savedJobsRepository = SavedJobsRepository(application)
    private val publishedJobsRepository = PublishedJobsRepository()

    private val profileRepository = com.mrdiy.careers.data.repository.ProfileRepository(application)

    private var allJobs: List<Job> = emptyList()

    private var defaultFilterDone = false
    private val _selectedLocation = MutableLiveData<String?>()
    /** The active location filter (null = All Jobs), so a recreated screen can restore the right chip. */
    val selectedLocation: LiveData<String?> get() = _selectedLocation

    /** Distance in km from the applicant to each currently shown job, keyed by job id. */
    var distances: Map<String, Double> = emptyMap()
        private set

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
        _filteredJobs.value = allJobs
        loadSavedJobIds()
        fetchJobsFromApi()
        startAutoRefresh()
    }

    fun fetchJobsFromApi() {
        if (fetchJob?.isActive == true) return
        fetchJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            publishedJobsRepository.fetchOpenJobs()
                .onSuccess { apiJobs ->
                    Log.d("HomeViewModel", "API returned ${apiJobs.size} jobs")
                    allJobs = apiJobs.filter(MetroManilaScope::contains)
                    _isLoading.value = false
                    applyFilters()
                }
                .onFailure { e ->
                    com.mrdiy.careers.data.SafeDiagnostics.record("backend_request", e)
                    allJobs = emptyList()
                    _isLoading.value = false
                    _errorMessage.value = "Unable to load published jobs. Please retry."
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
            applicationsRepository.getApplications().onSuccess { appliedCount.value = it.size }
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
            if (!savedJobsRepository.saveJob(job)) _errorMessage.value = "Could not save this job. Please sign in and retry."
            loadSavedJobIds()
        }
    }

    fun unsaveJob(job: Job) {
        viewModelScope.launch {
            if (!savedJobsRepository.unsaveJob(job.id)) _errorMessage.value = "Could not remove saved job. Please retry."
            loadSavedJobIds()
        }
    }

    fun toggleSaveJob(job: Job) {
        viewModelScope.launch {
            val isSaved = savedJobsRepository.isJobSaved(job.id)
            if (isSaved) unsaveJob(job) else saveJob(job)
        }
    }

    fun getAppliedCount(): Int = 0

    fun getSavedCount(): Int = _savedCount.value ?: 0

    private fun applyFilters() {
        var filtered = allJobs

        if (!currentSearchQuery.isNullOrEmpty()) {
            filtered = UniversalJobSearchEngine.search(currentSearchQuery!!, filtered).map { it.job }
        }

        if (!currentJobTypeFilter.isNullOrEmpty()) {
            filtered = filtered.filter { job ->
                job.jobType.equals(currentJobTypeFilter, ignoreCase = true)
            }
        }

        // Distance from the applicant's profile location (same rule as the Jobs tab).
        val applicantLocation = profileRepository.loadFromPrefs().location
        val jobDistances = filtered.mapNotNull { job ->
            LocationFilter.distanceKm(applicantLocation, job.location)?.let { job.id to it }
        }.toMap()

        // First time jobs arrive: if the applicant has a profile location and branches are nearby,
        // start on "Near Me" so the list is filtered to their area. They can switch to All Jobs.
        if (!defaultFilterDone && allJobs.isNotEmpty()) {
            defaultFilterDone = true
            if (currentLocationFilter.isNullOrEmpty() &&
                jobDistances.values.any { it <= LocationFilter.NEAR_ME_RADIUS_KM }
            ) {
                currentLocationFilter = LocationFilter.NEAR_ME
                _selectedLocation.value = LocationFilter.NEAR_ME
            }
        }

        if (!currentLocationFilter.isNullOrEmpty()) {
            filtered = filtered.filter { job ->
                LocationFilter.matches(currentLocationFilter, job.location, jobDistances[job.id])
            }
        }

        // Nearest first; a search keeps its relevance order. Jobs with an unknown distance go last.
        if (jobDistances.isNotEmpty() && currentSearchQuery.isNullOrEmpty()) {
            filtered = filtered.sortedBy { jobDistances[it.id] ?: Double.MAX_VALUE }
        }

        distances = jobDistances
        _filteredJobs.value = filtered
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        searchJob?.cancel()
    }
}
