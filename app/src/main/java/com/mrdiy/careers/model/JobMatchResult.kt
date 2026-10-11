package com.mrdiy.careers.model

data class JobMatchResult(
    val job: Job,
    val matchScore: Int,          // 0–100
    val matchLabel: String,       // "Excellent Match", "Good Match", "Fair Match"
    val matchReasons: List<String>,     // Why this job fits the user
    val missingSkills: List<String>,    // Skills the user lacks
    val matchedSkills: List<String>,    // Skills the user already has
    val distanceKm: Double? = null      // Estimated distance from the applicant; null if unknown
)

data class ResumeParseResult(
    val success: Boolean,
    val skills: List<String> = emptyList(),
    val education: List<Education> = emptyList(),
    val workExperiences: List<WorkExperience> = emptyList(),
    val yearsOfExperience: Int = 0,
    val summary: String = "",
    val error: String = ""
)
