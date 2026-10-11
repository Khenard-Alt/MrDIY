package com.mrdiy.careers.model

import kotlinx.serialization.Serializable

data class UserProfile(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val location: String = "",
    val desiredPosition: String = "",
    val headline: String = "",
    val about: String = "",
    val resumeText: String = "",
    val resumeUrl: String = "",
    val resumeName: String = "",
    val resumeUploadedAt: String = "",
    val skills: List<String> = emptyList(),
    val education: List<Education> = emptyList(),
    val workExperiences: List<WorkExperience> = emptyList(),
    val yearsOfExperience: Int = 0,
    val preferredCategories: List<String> = emptyList(),
    val preferredLevel: String = "",
    val preferredSalaryMin: Int = 0,
    val photoPath: String = ""
) {
    /**
     * The match % compares the job with the applicant's resume, so it is only shown once a resume has been
     * read. Without one, only the distance to the branch is known, and that alone is not a match.
     */
    val canShowMatch: Boolean get() = resumeText.isNotBlank()

    val initials: String get() = buildString {
        if (firstName.isNotEmpty()) append(firstName.first().uppercaseChar())
        if (lastName.isNotEmpty()) append(lastName.first().uppercaseChar())
    }.ifEmpty { fullName.take(2).uppercase() }
}
