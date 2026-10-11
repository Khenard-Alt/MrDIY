package com.mrdiy.careers.data.repository

import android.content.Context
import android.util.Log
import com.mrdiy.careers.data.auth.AuthManager
import com.mrdiy.careers.data.auth.SupabaseProvider
import com.mrdiy.careers.model.UserProfile
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json

// ─────────────────────────────────────────────────────────────────────────────
// WHY THIS CRASH HAPPENED — full explanation
// ─────────────────────────────────────────────────────────────────────────────
//
// The Supabase `profiles` table was created with TEXT columns that default to
// NULL at the DB level.  A brand-new user who has never saved a profile will
// get a row where those columns are NULL, so Postgrest returns:
//
//   { "full_name": null, "location": null, "desired_position": null, ... }
//
// The OLD DTO declared every field as non-nullable String with a Kotlin default:
//
//   @SerialName("full_name") val fullName: String = ""   ← WRONG
//
// kotlinx.serialization DEFAULT VALUE SEMANTICS:
//   • Field absent from JSON  →  use Kotlin default ("")  ✅
//   • Field present, value null  →  crash: cannot assign null to String  ❌
//
// The default `= ""` does NOT protect against an explicit `null` in JSON for a
// non-nullable type. That's what caused:
//
//   "Unexpected JSON token at offset 63: Expected string literal but 'null'
//    literal was found at path: $[0].full_name"
//
// ─────────────────────────────────────────────────────────────────────────────
// THE FIX — two layers, both applied:
//
//  Layer 1 (SupabaseProvider): coerceInputValues = true on the shared Json
//    instance that is passed to the Postgrest plugin.  This makes the parser
//    treat JSON null as "use the Kotlin default" for non-nullable fields.
//    It is a global safety net for every DTO in the project.
//
//  Layer 2 (here): Every DTO field that the DB can legitimately return as null
//    is declared String? and mapped to "" via the Elvis operator in toDomain().
//    This makes the null-contract explicit and survives even if the Json config
//    is accidentally changed later.
//
//  Which approach is "better practice"?
//    Use BOTH. coerceInputValues is a convenient catch-all but it hides the
//    fact that the DB can return nulls. Nullable types make that contract
//    visible in the type system and prevent subtle "silent default" bugs.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class UserProfileRow(
    @SerialName("user_id")           val userId: String           = "",
    @SerialName("full_name")         val fullName: String?         = null,
    val location: String?                                           = null,
    @SerialName("desired_position")  val desiredPosition: String?  = null,
    @SerialName("years_experience")  val yearsExperience: String?  = null,
    val email: String?                                              = null,
    val phone: String?                                              = null,
    val about: String?                                              = null,
    val skills: String?                                             = null,
    @SerialName("work_experiences") val workExperiences: JsonElement? = null,
    val headline: String?                                           = null,
    @SerialName("resume_name")       val resumeFileName: String?    = null,
    @SerialName("resume_url")         val resumeUrl: String?        = null
)

class ProfileRepository(private val context: Context) {

    private companion object {
        const val AVATAR_BUCKET = "avatars"
    }

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // Reuse the same Json instance that was given to the Postgrest plugin —
    // this guarantees both the client decode path and any manual Json.encode
    // calls use identical settings.
    private val json: Json = SupabaseProvider.supabaseJson

    fun getCurrentUserId(): String = AuthManager(context).getCurrentUserId().orEmpty()

    // ── Map domain model → Supabase DTO ──────────────────────────────────────
    private fun UserProfile.toRow(uid: String) = UserProfileRow(
        userId            = uid,
        fullName          = if (fullName.isNotBlank()) fullName else "$firstName $lastName".trim(),
        location          = location,
        desiredPosition   = desiredPosition,
        yearsExperience   = yearsOfExperience.toString(),
        email             = email,
        phone             = phone,
        about             = about,
        skills            = skills.joinToString(","),
        workExperiences   = json.parseToJsonElement(json.encodeToString(workExperiences)),
        headline          = headline,
        resumeFileName    = resumeName,
        resumeUrl         = resumeUrl
    )

    // ── Map Supabase DTO → domain model ──────────────────────────────────────
    // Every String? field is mapped with ?: "" — nulls are treated as empty.
    private fun UserProfileRow.toDomain(): UserProfile {
        val safeFullName = fullName ?: ""
        val nameParts    = safeFullName.split(" ")
        return UserProfile(
            id                = userId,
            firstName         = nameParts.getOrElse(0) { "" },
            lastName          = nameParts.drop(1).joinToString(" "),
            fullName          = safeFullName,
            email             = email             ?: "",
            phone             = phone             ?: "",
            location          = location          ?: "",
            desiredPosition   = desiredPosition   ?: "",
            workExperiences = runCatching { json.decodeFromString<List<com.mrdiy.careers.model.WorkExperience>>(
                (workExperiences as? JsonPrimitive)?.content ?: workExperiences?.toString() ?: "[]") }.getOrDefault(emptyList()),
            photoPath = loadFromPrefs(userId).photoPath,
            education = loadFromPrefs(userId).education,
            headline          = headline          ?: "",
            about             = about             ?: "",
            skills            = (skills ?: "").split(",")
                .map { it.trim() }.filter { it.isNotBlank() },
            yearsOfExperience = yearsExperience?.toIntOrNull() ?: 0,
            resumeUrl         = resumeUrl         ?: "",
            resumeName        = resumeFileName    ?: "",
            resumeUploadedAt  = ""
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE
    // ─────────────────────────────────────────────────────────────────────────
    fun saveProfile(profile: UserProfile, onComplete: ((Boolean) -> Unit)? = null) {
        val uid = getCurrentUserId()
        if (profile.id.isNotBlank() && profile.id != uid) { onComplete?.invoke(false); return }

        if (uid.isEmpty()) {
            Log.e("ProfileRepo", "saveProfile: no user ID — aborting")
            onComplete?.invoke(false)
            return
        }


        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = SupabaseProvider.client
                // Keep the onboarding write compatible with existing profiles tables.
                // Resume metadata is optional and is written by the resume flow; sending
                // obsolete resume columns here made a brand-new profile fail before the
                // user could finish onboarding.
                // Upload the profile picture so the admin can see it. A failed upload never blocks saving.
                val uploadedPhoto = uploadPhoto(uid, profile.photoPath)
                val payload = kotlinx.serialization.json.buildJsonObject {
                    put("user_id", JsonPrimitive(uid))
                    put("full_name", JsonPrimitive(profile.fullName.ifBlank { "${profile.firstName} ${profile.lastName}".trim() }))
                    put("location", JsonPrimitive(profile.location))
                    put("desired_position", JsonPrimitive(profile.desiredPosition))
                    put("years_experience", JsonPrimitive(profile.yearsOfExperience.toString()))
                    put("email", JsonPrimitive(profile.email))
                    put("phone", JsonPrimitive(profile.phone))
                    // The rest of the profile, so HR can see it (and print the biodata) in the admin.
                    put("about", JsonPrimitive(profile.about))
                    put("headline", JsonPrimitive(profile.headline))
                    put("skills", JsonPrimitive(profile.skills.joinToString(",")))
                    put("work_experiences", json.parseToJsonElement(json.encodeToString(profile.workExperiences)))
                    put("education", json.parseToJsonElement(json.encodeToString(profile.education)))
                    if (uploadedPhoto != null) put("photo_path", JsonPrimitive(uploadedPhoto))
                }
                // Do not require a database-level UNIQUE(user_id) constraint here.
                // Some existing installations predate that constraint, and PostgREST
                // rejects ON CONFLICT when the constraint is absent.
                val existing = client.from("profiles")
                    .select { filter { eq("user_id", uid) } }
                    .decodeSingleOrNull<UserProfileRow>()
                if (existing == null) {
                    client.from("profiles").insert(payload)
                } else {
                    client.from("profiles").update(payload) { filter { eq("user_id", uid) } }
                }
                saveLocally(uid, profile)
                context.getSharedPreferences("profile_$uid", Context.MODE_PRIVATE)
                    .edit().putBoolean("profile_sync_pending", false).apply()
                withContext(Dispatchers.Main) { onComplete?.invoke(true) }
            } catch (e: Exception) {
                com.mrdiy.careers.data.SafeDiagnostics.record("backend_request", e)
                saveLocally(uid, profile)
                context.getSharedPreferences("profile_$uid", Context.MODE_PRIVATE)
                    .edit().putBoolean("profile_sync_pending", true).apply()
                withContext(Dispatchers.Main) { onComplete?.invoke(true) }
            }
        }
    }

    /**
     * Uploads the local profile picture (downscaled JPEG) to the private `avatars` bucket at
     * `<uid>/avatar.jpg` and returns that storage path, or null if there is no photo or the upload fails.
     */
    private suspend fun uploadPhoto(uid: String, localPath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(localPath)
            if (localPath.isBlank() || !file.isFile) return@withContext null
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(localPath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
            val bitmap = android.graphics.BitmapFactory.decodeFile(
                localPath, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@withContext null
            val bytes = java.io.ByteArrayOutputStream().also {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
            }.toByteArray()
            val path = "$uid/avatar.jpg"
            SupabaseProvider.client.storage.from(AVATAR_BUCKET).upload(path, bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.Image.JPEG
            }
            path
        } catch (e: Exception) {
            com.mrdiy.careers.data.SafeDiagnostics.record("backend_request", e)
            null
        }
    }

    fun isProfileSyncPending(userId: String = getCurrentUserId()): Boolean =
        userId.isNotBlank() && context.getSharedPreferences("profile_$userId", Context.MODE_PRIVATE)
            .getBoolean("profile_sync_pending", false)

    fun saveResumeLocally(uid: String, name: String, parsed: UserProfile, text: String): UserProfile {
        val current = loadFromPrefs(uid)
        val merged = current.copy(
            resumeName = name,
            resumeText = text,
            skills = (current.skills + parsed.skills).distinctBy { it.lowercase() }
        )
        saveLocally(uid, merged)
        context.getSharedPreferences("profile_$uid", Context.MODE_PRIVATE)
            .edit().putBoolean("resume_sync_pending", true).apply()
        return merged
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────────────────
    fun loadProfile(userId: String = "", onResult: (UserProfile?) -> Unit) {
        val uid = userId.ifEmpty { getCurrentUserId() }

        if (uid.isEmpty() || uid != getCurrentUserId()) { onResult(null); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client  = SupabaseProvider.client
                val profileRow = client.from("profiles")
                    .select { filter { eq("user_id", uid) } }
                    .decodeSingleOrNull<UserProfileRow>()

                if (profileRow != null) {
                    val profile = profileRow.toDomain()
                    if (profile.fullName.isNotBlank()) {
                        saveLocally(uid, profile)
                        withContext(Dispatchers.Main) { onResult(profile) }
                    } else {
                        withContext(Dispatchers.Main) { onResult(loadFromPrefs(uid)) }
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(loadFromPrefs(uid)) }
                }
            } catch (e: Exception) {
                com.mrdiy.careers.data.SafeDiagnostics.record("backend_request", e)
                withContext(Dispatchers.Main) { onResult(loadFromPrefs(uid)) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Local cache helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun saveLocally(uid: String, profile: UserProfile) {
        if (uid != getCurrentUserId()) return
        context.getSharedPreferences("profile_$uid", Context.MODE_PRIVATE).edit().apply {
            putString("user_id",          uid)
            putString("full_name",        profile.fullName.ifBlank { "${profile.firstName} ${profile.lastName}".trim() })
            putString("first_name",       profile.firstName)
            putString("last_name",        profile.lastName)
            putString("email",            profile.email)
            putString("phone",            profile.phone)
            putString("location",         profile.location)
            putString("desired_position", profile.desiredPosition)
            putString("headline",         profile.headline)
            putString("about",            profile.about)
            putString("skills",           profile.skills.joinToString(","))
            putInt   ("years_experience", profile.yearsOfExperience)
            putString("work_experiences", json.encodeToString(profile.workExperiences))
            putString("education", json.encodeToString(profile.education))
            putString("photo_path", profile.photoPath)
            putString("resume_text",       profile.resumeText)
            putString("resume_url",        profile.resumeUrl)
            putString("resume_name",       profile.resumeName)
            apply()
        }
    }

    fun loadFromPrefs(userId: String = ""): UserProfile {
        val uid       = userId.ifEmpty { getCurrentUserId() }
        if (uid.isEmpty() || uid != getCurrentUserId()) return UserProfile()
        val p         = context.getSharedPreferences("profile_$uid", Context.MODE_PRIVATE)
        val firstName = p.getString("first_name", "") ?: ""
        val lastName  = p.getString("last_name",  "") ?: ""
        val fullName  = p.getString("full_name",  "") ?: "$firstName $lastName".trim()
        val skillsRaw = p.getString("skills",     "") ?: ""
        return UserProfile(
            id                = uid,
            firstName         = firstName,
            lastName          = lastName,
            fullName          = fullName,
            email             = p.getString("email",            "") ?: "",
            phone             = p.getString("phone",            "") ?: "",
            location          = p.getString("location",         "") ?: "",
            desiredPosition   = p.getString("desired_position", "") ?: "",
            headline          = p.getString("headline",         "") ?: "",
            about             = p.getString("about",            "") ?: "",
            skills            = skillsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() },
            yearsOfExperience = p.getInt("years_experience", 0),
            workExperiences = runCatching { json.decodeFromString<List<com.mrdiy.careers.model.WorkExperience>>(p.getString("work_experiences", "[]") ?: "[]") }.getOrDefault(emptyList()),
            education = runCatching { json.decodeFromString<List<com.mrdiy.careers.model.Education>>(p.getString("education", "[]") ?: "[]") }.getOrDefault(emptyList()),
            photoPath = p.getString("photo_path", "") ?: "",
            resumeText        = p.getString("resume_text", "") ?: "",
            resumeUrl         = p.getString("resume_url",       "") ?: "",
            resumeName        = p.getString("resume_name",      "") ?: "",
            resumeUploadedAt = ""
        )
    }

    fun loadProfileFromPrefs(): UserProfile = loadFromPrefs()

    suspend fun persistResume(uid: String, name: String, path: String, parsed: UserProfile, text: String) = withContext(Dispatchers.IO) {
        check(uid == getCurrentUserId() && ResumeFiles.ownedPath(path, uid))
        val client = SupabaseProvider.client
        val existing = client.from("profiles").select { filter { eq("user_id", uid) } }.decodeSingleOrNull<UserProfileRow>()
            ?: error("Complete your profile before uploading a resume")
        val mergedSkills = (existing.skills.orEmpty().split(",") + parsed.skills).map { it.trim() }
            .filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        // Send only resume fields; never overwrite the user's personal information.
        client.from("profiles").update(kotlinx.serialization.json.buildJsonObject {
            put("resume_url", JsonPrimitive(path))
            put("skills", JsonPrimitive(mergedSkills.joinToString(",")))
        }) { filter { eq("user_id", uid) } }
        check(uid == getCurrentUserId())
        val local = loadFromPrefs(uid).copy(
            resumeUrl = path,
            resumeName = name,
            resumeText = text,
            skills = mergedSkills
        )
        saveLocally(uid, local)
    }
}
