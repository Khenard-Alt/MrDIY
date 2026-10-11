package com.mrdiy.careers.data.repository

import android.content.Context
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.model.Job
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

class ApplicationsRepository(private val context: Context) {
    companion object { private val applyLock = Mutex() }

    private val authManager = AuthManager(context)
    private val table = "applications"

    data class ApplicationRow(
        val id: String,
        val user_id: String,
        val job_id: String,
        val job_title: String,
        val status: String = "submitted",
        val applied_at: String,
        /** The match % saved when the applicant applied, if it was computed. */
        val match_score: Int? = null
    )

    suspend fun getApplications(): Result<List<ApplicationRow>> = withContext(Dispatchers.IO) {
        val userId = authManager.getCurrentUserId()
            ?: return@withContext Result.failure(IllegalStateException("Please sign in first."))
        try {
            val rows = SupabaseProvider.client.postgrest[table].select {
                filter { eq("user_id", userId) }
            }.decodeList<JsonObject>()
            val ids = rows.mapNotNull { it["job_id"]?.jsonPrimitive?.contentOrNull }.distinct()
            val titles = if (ids.isEmpty()) emptyMap() else SupabaseProvider.client.postgrest["jobs"].select {
                filter { isIn("id", ids) }
            }.decodeList<PublishedJobRow>().associate { it.id.content to it.title.orEmpty() }
            Result.success(rows.map { row ->
                val jobId = row["job_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                ApplicationRow(
                    id = row["id"]!!.jsonPrimitive.content,
                    user_id = userId, job_id = jobId,
                    job_title = titles[jobId]?.takeIf { it.isNotBlank() } ?: "Job #$jobId",
                    status = row["status"]?.jsonPrimitive?.contentOrNull ?: "Pending",
                    applied_at = row["created_at"]?.jsonPrimitive?.contentOrNull
                        ?: row["applied_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    match_score = row["match_score"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
                )
            })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Applications could not be loaded."))
        }
    }

    fun observeApplicationsRealtime(): Flow<Result<List<ApplicationRow>>> = flow {
        emit(getApplications())
        try {
            val userId = authManager.getCurrentUserId().orEmpty()
            if (userId.isNotBlank()) {
                val realtime = SupabaseProvider.client.pluginManager.getPlugin(Realtime)
                val channel = realtime.channel("apps_realtime_${UUID.randomUUID()}")
                val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public")
                channel.subscribe()
                changeFlow.collect {
                    this@flow.emit(getApplications())
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            com.mrdiy.careers.data.SafeDiagnostics.record("applications_realtime", e)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun apply(job: Job): Result<Unit> = applyLock.withLock { applyOnce(job) }

    /**
     * Match % for this job, computed against all published jobs so it equals the score shown in the
     * lists. Returns null if it can't be computed; applying must never fail because of scoring.
     */
    private suspend fun matchScoreFor(job: Job): Int? = try {
        val profile = ProfileRepository(context).loadFromPrefs()
        // No resume means no real comparison, so no score is saved (HR sees "no score" instead of a location-only number).
        if (!profile.canShowMatch) {
            null
        } else {
            val jobs = PublishedJobsRepository().fetchOpenJobs().getOrNull().orEmpty()
                .let { if (it.any { published -> published.id == job.id }) it else it + job }
            com.mrdiy.careers.data.ml.HybridMatchingService.matchJobsToProfile(profile, jobs)
                .firstOrNull { it.job.id == job.id }?.matchScore
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        null
    }
    private suspend fun applyOnce(job: Job): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = authManager.getCurrentUserId()
            ?: return@withContext Result.failure(IllegalStateException("Please sign in before applying."))

        try {
            PublishedJobsRepository().getJobById(job.id).getOrThrow()
            val databaseJobId = job.id
            val existing = SupabaseProvider.client.postgrest[table].select {
                filter {
                    eq("user_id", userId)
                    eq("job_id", databaseJobId)
                }
            }.decodeList<JsonObject>()

            if (existing.isNotEmpty()) {
                Result.failure(IllegalStateException("You have already applied for this job."))
            } else {
                val matchScore = matchScoreFor(job)
                SupabaseProvider.client.postgrest[table].insert(buildJsonObject {
                    put("user_id", userId)
                    put("job_id", job.id.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(job.id))
                    put("status", "Pending")
                    // Same score the applicant saw (text similarity + location), so the admin sees it too.
                    if (matchScore != null) put("match_score", matchScore)
                })
                Result.success(Unit)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = error.message.orEmpty()
            com.mrdiy.careers.data.SafeDiagnostics.record("apply", error)
            val safeMessage = when {
                "relation" in message && "does not exist" in message ->
                    "Applications are temporarily unavailable. Please try again later."
                "row-level security" in message || "violates row-level security" in message ->
                    "Your account is not authorized to submit applications."
                "JWT" in message || "not authenticated" in message ->
                    "Please sign in with a real account before applying."
                else -> "Application could not be submitted. Please try again."
            }
            Result.failure(IllegalStateException(safeMessage))
        }
    }
}
