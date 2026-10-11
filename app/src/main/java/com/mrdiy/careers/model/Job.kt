package com.mrdiy.careers.model

data class Job(
    val id: String,
    val title: String,
    val company: String,
    val branch: String,
    val location: String,
    val jobType: String,
    val category: String,
    val level: String,
    val salaryMin: Int,
    val salaryMax: Int,
    val salaryType: String = "monthly",
    val postedAgo: String,
    val aboutRole: String,
    val responsibilities: List<String>,
    val requirements: List<String>,
    val benefits: List<String>,
    val isSaved: Boolean = false,
    /** Total headcount for this job, or null when not set (unlimited). */
    val openings: Int? = null,
    /** How many applicants have already been Hired for this job. */
    val hiredCount: Int = 0
) {
    /** How many slots are left, or null when openings isn't set. Never negative. */
    fun openingsRemaining(): Int? = openings?.let { (it - hiredCount).coerceAtLeast(0) }

    fun isFull(): Boolean = openingsRemaining() == 0

    fun getMonthlySalary(): Int {
        return when (salaryType.lowercase()) {
            "daily" -> salaryMin * 26
            "weekly" -> (salaryMin * 4.33).toInt()
            "yearly" -> salaryMin / 12
            else -> salaryMin
        }
    }

    fun getMonthlySalaryMax(): Int {
        return when (salaryType.lowercase()) {
            "daily" -> salaryMax * 26
            "weekly" -> (salaryMax * 4.33).toInt()
            "yearly" -> salaryMax / 12
            else -> salaryMax
        }
    }
}
