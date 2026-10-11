package com.mrdiy.careers

import com.mrdiy.careers.data.ml.HiringVelocity
import com.mrdiy.careers.data.ml.JobMatchingEngine
import com.mrdiy.careers.data.ml.ResumeSkills
import com.mrdiy.careers.model.Job
import com.mrdiy.careers.model.UserProfile
import com.mrdiy.careers.model.WorkExperience
import com.mrdiy.careers.ui.base.DateText
import org.junit.Assert.*
import org.junit.Test

class RecruitmentPipelineTest {

    // ---------------------------------------------------------------- pipeline
    @Test fun statusNamesAreMappedToPipelineSteps() {
        assertEquals("Hired", DateText.normalizeStatus("Approved"))
        assertEquals("Hired", DateText.normalizeStatus("accepted"))
        assertEquals("Reviewed", DateText.normalizeStatus("under review"))
        assertEquals("Interview Scheduled", DateText.normalizeStatus("Interview"))
        assertEquals("On Hold", DateText.normalizeStatus("on-hold"))
        assertEquals("Pending", DateText.normalizeStatus("something odd"))
        assertEquals("Under review", DateText.statusLabel("Reviewed"))
        assertEquals("Shortlisted", DateText.statusLabel("shortlisted"))
    }

    // ---------------------------------------------------------- hiring velocity
    @Test fun hiringVelocityFollowsTheFormula() {
        // 7 base days; strong match -2; complete profile -1; verified documents -1 => 3 days, shown as 2 - 4
        val best = HiringVelocity.estimate(matchScore = 90, profileComplete = true, documentsVerified = 7, documentsTotal = 7)
        assertEquals(2, best.minDays)
        assertEquals(4, best.maxDays)
        assertEquals("Estimated start: 2 - 4 business days", best.label)

        // Low match +3 => 10 days, shown as 9 - 11
        val weak = HiringVelocity.estimate(matchScore = 30, profileComplete = false, documentsVerified = 0, documentsTotal = 0)
        assertEquals(9, weak.minDays)
        assertEquals(11, weak.maxDays)

        // Unknown match and no extras => the 7 day base, shown as 6 - 8
        val base = HiringVelocity.estimate(matchScore = null, profileComplete = false, documentsVerified = 0, documentsTotal = 0)
        assertEquals(6, base.minDays)
        assertEquals(8, base.maxDays)
    }

    @Test fun hiringVelocityNeverGoesBelowOneDay() {
        val estimate = HiringVelocity.estimate(100, true, 7, 7)
        assertTrue(estimate.minDays >= 1)
    }

    // --------------------------------------------------------------- matching
    @Test fun relatedWordsAreMatchedByTheirStem() {
        assertEquals(JobMatchingEngine.normalizeToken("cashier"), JobMatchingEngine.normalizeToken("cashiering"))
        assertEquals(JobMatchingEngine.normalizeToken("casher"), JobMatchingEngine.normalizeToken("cashier"))
        assertEquals(JobMatchingEngine.normalizeToken("kahera"), JobMatchingEngine.normalizeToken("cashier"))
        assertEquals(JobMatchingEngine.normalizeToken("customers"), JobMatchingEngine.normalizeToken("customer"))
        assertEquals("sale", JobMatchingEngine.normalizeToken("sales"))
    }

    @Test fun skillsAreFoundWhicheverWayTheyAreWritten() {
        assertTrue("point of sale" in ResumeSkills.extractCanonical("Experienced with POS systems"))
        assertTrue("customer service" in ResumeSkills.extractCanonical("Strong customer care background"))
        assertFalse("kotlin" in ResumeSkills.extractCanonical("Sold shoes at a store"))
    }

    @Test fun compositeScoreReweightsMissingParts() {
        // Only the cosine part is known: the score is just the cosine.
        assertEquals(40, JobMatchingEngine.compositeTextScore(0.40, null, null))
        // All three parts: 60% cosine + 25% skills + 15% title.
        assertEquals(70, JobMatchingEngine.compositeTextScore(0.5, 1.0, 1.0))
    }

    private fun job(title: String, about: String, requirements: List<String>) = Job(
        id = title, title = title, company = "MR.DIY", branch = "SM City Taytay, Taytay, Rizal",
        location = "SM City Taytay, Taytay, Rizal", jobType = "Full-time", category = "Retail", level = "Entry",
        salaryMin = 0, salaryMax = 0, postedAgo = "", aboutRole = about,
        responsibilities = emptyList(), requirements = requirements, benefits = emptyList()
    )

    @Test fun anApplicantWhoFitsRanksAboveOneWhoDoesNot() {
        val cashier = job("Cashier", "Handle cash handling and point of sale for customers", listOf("Customer service", "POS experience"))
        val driver = job("Delivery Driver", "Deliver goods with a motorcycle", listOf("Driver license", "Delivery experience"))

        val profile = UserProfile(
            skills = listOf("customer service", "POS", "cash handling"),
            desiredPosition = "Cashier",
            location = "Angono, Rizal",
            workExperiences = listOf(WorkExperience(role = "Cashiering", company = "Local store", description = "Handled the register"))
        )

        val results = JobMatchingEngine.matchJobs(profile, listOf(driver, cashier))
        assertEquals("Cashier", results.first().job.title)
        assertTrue(results.first().matchScore > results.last().matchScore)
        assertTrue("point of sale" in results.first().matchedSkills)
    }
}
