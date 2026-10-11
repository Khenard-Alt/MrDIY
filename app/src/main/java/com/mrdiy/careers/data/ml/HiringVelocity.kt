package com.mrdiy.careers.data.ml

/**
 * Hiring Velocity Estimator: how many business days it may take until the applicant can start.
 * Same formula as the admin (lib/velocity.ts); keep the two in sync.
 *
 *   base 7 business days; match >= 85% -2; match < 50% +3;
 *   complete profile and resume -1; all pre-employment documents verified -1.
 * Shown as a range one day either side, e.g. "Estimated start: 2 - 4 business days".
 */
object HiringVelocity {
    const val BASE_BUSINESS_DAYS = 7

    data class Estimate(val minDays: Int, val maxDays: Int, val factors: List<String>) {
        val label: String get() = "Estimated start: $minDays - $maxDays business days"
    }

    fun estimate(
        matchScore: Int?,
        profileComplete: Boolean,
        documentsVerified: Int,
        documentsTotal: Int
    ): Estimate {
        var days = BASE_BUSINESS_DAYS
        val factors = mutableListOf("Base time: $BASE_BUSINESS_DAYS business days")

        if (matchScore != null) {
            if (matchScore >= 85) {
                days -= 2
                factors += "Strong match ($matchScore%): -2 days"
            } else if (matchScore < 50) {
                days += 3
                factors += "Lower match ($matchScore%): +3 days for further evaluation"
            }
        }
        if (profileComplete) {
            days -= 1
            factors += "Complete profile and resume: -1 day"
        }
        if (documentsTotal > 0 && documentsVerified >= documentsTotal) {
            days -= 1
            factors += "All pre-employment documents verified: -1 day"
        }

        days = maxOf(1, days)
        return Estimate(minDays = maxOf(1, days - 1), maxDays = days + 1, factors = factors)
    }
}
