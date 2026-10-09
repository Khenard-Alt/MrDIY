package com.mrdiy.careers.data.repository

import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.model.Job
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** Reads the same published rows written by the admin's Jobs page. */
class PublishedJobsRepository(
    private val allowedLocations: List<String> = com.mrdiy.careers.data.search.MetroManilaScope.allowedTerms,
    private val loadPage: suspend (Long) -> List<PublishedJobRow> = { offset ->
        SupabaseProvider.client.postgrest["jobs"].select {
            filter { eq("status", "published") }
            order("created_at", Order.DESCENDING)
            order("id", Order.ASCENDING)
            range(offset, offset + 199)
        }.decodeList<PublishedJobRow>()
    }
) {
    suspend fun fetchOpenJobs(): Result<List<Job>> = request {
        val rows = mutableListOf<PublishedJobRow>()
        var offset = 0L
        do {
            val page = loadPage(offset)
            rows.addAll(page)
            offset += page.size
        } while (page.size == 200)
        rows.filter { it.status == "published" }.distinctBy { it.id }.map { it.toJob() }
            .filter { com.mrdiy.careers.data.search.MetroManilaScope.matches(it.location, allowedLocations) }
    }

    suspend fun getJobById(id: String): Result<Job> = request {
        SupabaseProvider.client.postgrest["jobs"].select {
            filter {
                eq("id", id)
                eq("status", "published")
            }
        }.decodeList<PublishedJobRow>().firstOrNull()?.toJob()
            ?.takeIf { com.mrdiy.careers.data.search.MetroManilaScope.matches(it.location, allowedLocations) }
            ?: error("This job is no longer available.")
    }

    fun observeJobsRealtime(): Flow<Result<List<Job>>> = flow {
        emit(fetchOpenJobs())
        try {
            val channel = SupabaseProvider.client.realtime.channel("jobs_realtime_${java.util.UUID.randomUUID()}")
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")
            channel.subscribe()
            changeFlow.collect {
                this@flow.emit(fetchOpenJobs())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            com.mrdiy.careers.data.SafeDiagnostics.record("jobs_realtime", error)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun <T> request(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

@Serializable
data class PublishedJobRow(
    val id: JsonPrimitive,
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val salary: String? = null,
    val salary_type: String? = null,
    val status: String? = null,
    val created_at: String? = null,
    val job_type: String? = null,
    val category: String? = null,
    val level: String? = null
) {
    fun toJob(): Job {
        val amounts = Regex("\\d+(?:\\.\\d+)?").findAll(salary.orEmpty().replace(",", ""))
            .take(2).mapNotNull { it.value.toDoubleOrNull()?.toInt() }.toList()
        val minimum = amounts.minOrNull() ?: 0
        val maximum = amounts.maxOrNull() ?: 0
        return Job(
            id = id.content, title = title.orEmpty(), company = "MR.D.I.Y. Philippines",
            branch = location.orEmpty(), location = location.orEmpty(),
            jobType = job_type ?: "Not specified", category = category ?: "Not specified", level = level ?: "Not specified",
            salaryMin = minimum, salaryMax = maximum,
            salaryType = salary_type?.lowercase()?.takeIf { it.isNotBlank() } ?: "monthly",
            postedAgo = created_at?.take(10).orEmpty(), aboutRole = description.orEmpty(),
            responsibilities = emptyList(), requirements = emptyList(), benefits = emptyList()
        )
    }
}
