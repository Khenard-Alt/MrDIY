package com.mrdiy.careers

import com.mrdiy.careers.data.repository.PublishedJobRow
import com.mrdiy.careers.data.repository.PublishedJobsRepository
import com.mrdiy.careers.data.ml.JobMatchingEngine
import com.mrdiy.careers.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class PublishedJobsTest {
    private fun row(id: Int, title: String = "Cashier", description: String = "") =
        PublishedJobRow(JsonPrimitive(id), title = title, description = description, status = "published", location = "Makati")

    @Test fun adminRowsAcceptNumericAndUuidIdsAndNullableFields() {
        val numeric = Json.decodeFromString<PublishedJobRow>("""{"id":42,"title":null,"salary":null,"salary_type":null}""").toJob()
        val uuid = Json.decodeFromString<PublishedJobRow>("""{"id":"f27b31dd-449d-46d5-a358-464f5488e222"}""").toJob()
        assertEquals("42", numeric.id)
        assertEquals("", numeric.title)
        assertEquals(0, numeric.salaryMin)
        assertEquals("monthly", numeric.salaryType)
        assertEquals("f27b31dd-449d-46d5-a358-464f5488e222", uuid.id)
    }

    @Test fun adminSalaryRangesKeepDecimalsAndNormalizeMonthlyPay() {
        val job = row(1).copy(salary = "PHP 600.00 – 800.00", salary_type = "Daily").toJob()
        assertEquals(600, job.salaryMin)
        assertEquals(800, job.salaryMax)
        assertEquals(15600, job.getMonthlySalary())
        assertEquals(20800, job.getMonthlySalaryMax())
    }

    @Test fun loadsBeyondFirstPageAndExcludesDrafts() = runBlocking {
        val offsets = mutableListOf<Long>()
        val repository = PublishedJobsRepository { offset ->
            offsets.add(offset)
            if (offset == 0L) (1..200).map { row(it) }
            else listOf(row(201), row(202).copy(status = "draft"))
        }
        val jobs = repository.fetchOpenJobs().getOrThrow()
        assertEquals(listOf(0L, 200L), offsets)
        assertEquals(201, jobs.size)
        assertEquals("201", jobs.last().id)
    }

    @Test fun emptyCatalogDoesNotCreateSampleJobs() = runBlocking {
        assertTrue(PublishedJobsRepository { emptyList() }.fetchOpenJobs().getOrThrow().isEmpty())
    }

    @Test fun pageFailureDoesNotReturnPartialCatalog() = runBlocking {
        val repository = PublishedJobsRepository { offset ->
            if (offset == 0L) (1..200).map { row(it) } else error("Network unavailable")
        }
        assertTrue(repository.fetchOpenJobs().isFailure)
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagates() { runBlocking {
        PublishedJobsRepository { throw CancellationException() }.fetchOpenJobs()
    } }

    @Test fun desiredPositionRanksRelevantPublishedJobFirst() {
        val jobs = listOf(row(1, "Warehouse Loader", "Shipments forklift").toJob(),
            row(2, "Cashier", "Cashier checkout transactions").toJob())
        val results = JobMatchingEngine.matchJobs(UserProfile(desiredPosition = "Cashier"), jobs)
        assertEquals("2", results.first().job.id)
        assertTrue(results.first().matchScore > results.last().matchScore)
    }

    @Test fun resumeTextAndDescriptionSkillsContributeToMatching() {
        val jobs = listOf(row(1, "Warehouse", "Forklift inventory shipments").toJob(),
            row(2, "Cashier", "Checkout transactions").toJob())
        val results = JobMatchingEngine.matchJobs(UserProfile(resumeText = "Forklift inventory shipments", skills = listOf("Forklift")), jobs)
        assertEquals("1", results.first().job.id)
        // The skills the job asks for that the applicant has (lower case, canonical names).
        assertEquals(setOf("forklift", "inventory"), results.first().matchedSkills.toSet())
    }

    @Test fun emptyProfileDoesNotClaimSuitability() {
        val result = JobMatchingEngine.matchJobs(UserProfile(), listOf(row(1).toJob())).single()
        assertEquals(0, result.matchScore)
        assertTrue(result.matchReasons.single().contains("No matching profile terms"))
    }
}
