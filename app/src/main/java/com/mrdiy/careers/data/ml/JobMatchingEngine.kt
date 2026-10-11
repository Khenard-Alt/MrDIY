package com.mrdiy.careers.data.ml

import com.mrdiy.careers.data.search.LocationFilter
import com.mrdiy.careers.model.Job
import com.mrdiy.careers.model.JobMatchResult
import com.mrdiy.careers.model.UserProfile
import com.mrdiy.careers.model.Education
import com.mrdiy.careers.model.WorkExperience
import kotlin.math.sqrt

/**
 * ML-based Job Matching using TF-IDF + Cosine Similarity
 *
 * This is a real ML approach using:
 * - TF-IDF (Term Frequency-Inverse Document Frequency) for feature extraction
 * - Cosine Similarity for measuring match score
 *
 * In a production app, you would:
 * 1. Train a model on labeled resume-job pairs
 * 2. Use more advanced embeddings (BERT, Word2Vec)
 * 3. Deploy as a REST API
 */
object JobMatchingEngine {

    data class TextVector(
        val terms: Map<String, Double>
    )

    /**
     * Main matching function - matches profile against jobs
     */
    fun matchJobs(profile: UserProfile, jobs: List<Job>): List<JobMatchResult> {
        if (jobs.isEmpty()) return emptyList()

        // Create profile document from all profile info
        val profileDoc = buildProfileDocument(profile)

        // Extract TF-IDF vectors. Each document is tokenized once and document frequencies are
        // counted once, so the cost grows linearly with the number of jobs.
        val jobDocs = jobs.map { buildJobDocument(it) }
        val corpusTokens = (listOf(profileDoc) + jobDocs).map { tokenize(it) }
        val documentFrequency = HashMap<String, Int>()
        corpusTokens.forEach { tokens -> tokens.toSet().forEach { documentFrequency.merge(it, 1, Int::plus) } }
        val totalDocs = corpusTokens.size

        val profileVector = computeTFIDF(corpusTokens[0], documentFrequency, totalDocs)
        val jobVectors = jobs.indices.associate { i ->
            jobs[i] to computeTFIDF(corpusTokens[i + 1], documentFrequency, totalDocs)
        }

        val profileSkills = ResumeSkills.extractCanonical(profileDoc)
        val positionTokens = tokenize(buildPositionText(profile)).toSet()

        // Calculate similarity scores
        return jobs.mapIndexed { index, job ->
            val cosine = cosineSimilarity(profileVector, jobVectors[job] ?: emptyMap())

            // Skills the job asks for versus the skills the applicant has.
            val jobSkills = ResumeSkills.extractCanonical(jobDocs[index]).toList()
            val matchedJobSkills = jobSkills.filter { it in profileSkills }
            val missingJobSkills = jobSkills.filter { it !in profileSkills }
            val skillCoverage = if (jobSkills.isNotEmpty()) matchedJobSkills.size.toDouble() / jobSkills.size else null

            // Do the applicant's position and past roles match the job title?
            val titleWords = tokenize(job.title).filter { it !in STOP_WORDS }.toSet()
            val titleFit = if (titleWords.isNotEmpty() && positionTokens.isNotEmpty()) {
                titleWords.count { it in positionTokens }.toDouble() / titleWords.size
            } else null

            val textScore = compositeTextScore(cosine, skillCoverage, titleFit)

            // Location is part of the score only when both locations are known, so a profile
            // without a location is not penalized.
            val distanceKm = LocationFilter.distanceKm(profile.location, job.location)
            val score = if (distanceKm == null) textScore else combine(textScore, distanceKm)

            JobMatchResult(
                job = job,
                matchScore = score,
                matchLabel = getMatchLabel(score),
                matchReasons = listOfNotNull(locationReason(distanceKm)) + generateMatchReasons(profile, job, textScore),
                matchedSkills = matchedJobSkills.ifEmpty { findMatchedSkills(profile.skills, job.requirements + job.aboutRole) },
                missingSkills = missingJobSkills.ifEmpty { findMissingSkills(profile.skills, job.requirements) },
                distanceKm = distanceKm
            )
        }.sortedByDescending { it.matchScore }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Text score: 60% TF-IDF cosine + 25% skill coverage + 15% title fit
    // (components that cannot be computed are left out and the rest are re-weighted).
    // Mirrors lib/matching.ts in the admin; keep the two in sync.
    // ─────────────────────────────────────────────────────────────────────
    private const val WEIGHT_COSINE = 0.60
    private const val WEIGHT_SKILLS = 0.25
    private const val WEIGHT_TITLE = 0.15

    internal fun compositeTextScore(cosine: Double, skillCoverage: Double?, titleFit: Double?): Int {
        var total = WEIGHT_COSINE * cosine
        var weight = WEIGHT_COSINE
        if (skillCoverage != null) { total += WEIGHT_SKILLS * skillCoverage; weight += WEIGHT_SKILLS }
        if (titleFit != null) { total += WEIGHT_TITLE * titleFit; weight += WEIGHT_TITLE }
        return Math.round(total / weight * 100).toInt().coerceIn(0, 100)
    }

    /** The applicant's desired position, headline and past job titles, for the title-fit part of the score. */
    private fun buildPositionText(profile: UserProfile): String =
        (listOf(profile.desiredPosition, profile.headline) + profile.workExperiences.map { it.role })
            .filter { it.isNotBlank() }.joinToString(" ")

    // ─────────────────────────────────────────────────────────────────────
    // Location proximity: 30% of the final score, text similarity is the other 70%
    // ─────────────────────────────────────────────────────────────────────
    private const val LOCATION_WEIGHT = 0.30
    private const val FULL_SCORE_KM = 1.5     // same city
    private const val ZERO_SCORE_KM = 40.0    // too far to commute daily

    private fun proximityScore(distanceKm: Double): Int {
        if (distanceKm <= FULL_SCORE_KM) return 100
        val remaining = 1 - (distanceKm - FULL_SCORE_KM) / (ZERO_SCORE_KM - FULL_SCORE_KM)
        return (remaining * 100).toInt().coerceIn(0, 100)
    }

    private fun combine(textScore: Int, distanceKm: Double): Int =
        (textScore * (1 - LOCATION_WEIGHT) + proximityScore(distanceKm) * LOCATION_WEIGHT)
            .toInt().coerceIn(0, 100)

    private fun locationReason(distanceKm: Double?): String? = when {
        distanceKm == null -> null
        distanceKm <= FULL_SCORE_KM -> "Branch is in your area"
        distanceKm <= 15.0 -> "Branch is about ${distanceKm.toInt()} km from you"
        else -> null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 1: Build text documents
    // ─────────────────────────────────────────────────────────────────────
    private fun buildProfileDocument(profile: UserProfile): String {
        return buildString {
            append(profile.skills.joinToString(" ") { it })
            append(" ")
            append(profile.desiredPosition)
            append(" ")
            append(profile.headline)
            append(" ")
            append(profile.about)
            append(" ")
            append(profile.resumeText)
            append(" ")
            append(profile.workExperiences.joinToString(" ") { exp: WorkExperience -> "${exp.role} ${exp.company} ${exp.description}" })
            append(" ")
            append(profile.education.joinToString(" ") { edu: Education -> "${edu.degree} ${edu.school}" })
            append(" ")
            append(profile.preferredCategories.joinToString(" "))
            append(" ")
            append(profile.preferredLevel)
        }.lowercase()
    }

    private fun buildJobDocument(job: Job): String {
        return buildString {
            append(job.title)
            append(" ")
            if (job.category != "Not specified") append(job.category)
            append(" ")
            if (job.level != "Not specified") append(job.level)
            append(" ")
            append(job.aboutRole)
            append(" ")
            append(job.requirements.joinToString(" "))
            append(" ")
            append(job.responsibilities.joinToString(" "))
            append(" ")
            append(job.benefits.joinToString(" "))
        }.lowercase()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 2: TF-IDF Feature Extraction
    // ─────────────────────────────────────────────────────────────────────
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}+#]+")

    // Very common words never count as the job title's meaning.
    private val STOP_WORDS = (
        "the and for with that this from have has had are was were will would can could may might shall " +
        "you your our their his her its who whom what when where which while into onto over under about " +
        "after before between through during also any all each other such than then them they these those " +
        "not but out off per via etc one two new more most very well able including include includes " +
        "must work works working worked job jobs role roles position positions company companies"
    ).split(" ").toSet()

    // Different spellings, typos and Tagalog words that mean the same thing.
    private val SYNONYMS = mapOf(
        "casher" to "cashier", "cashiering" to "cashier", "kahera" to "cashier", "kahero" to "cashier",
        "benta" to "sale", "tindera" to "sale", "tindero" to "sale", "selling" to "sale", "seller" to "sale",
        "bodega" to "warehouse", "imbentaryo" to "inventory",
        "stockroom" to "stock", "stocking" to "stock", "restocking" to "stock",
        "guard" to "security", "janitor" to "cleaning", "cleaner" to "cleaning",
        "manager" to "management", "managing" to "management",
        "supervising" to "supervision", "supervisor" to "supervision"
    )

    /** Lower-cases a word and reduces it to a rough stem so related forms match each other. */
    internal fun normalizeToken(raw: String): String {
        var word = raw.lowercase()
        SYNONYMS[word]?.let { return it }

        word = when {
            word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 4 && Regex("(ss|x|ch|sh)es$").containsMatchIn(word) -> word.dropLast(2)
            word.length > 4 && word.endsWith("ed") -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
        return SYNONYMS[word] ?: word
    }

    /** Words of at least 3 letters, stemmed. */
    internal fun tokenize(document: String): List<String> =
        document.lowercase().split(TOKEN_SPLIT).filter { it.length > 2 }.map { normalizeToken(it) }

    private fun computeTFIDF(
        tokens: List<String>,
        documentFrequency: Map<String, Int>,
        totalDocs: Int
    ): Map<String, Double> {
        val words = tokens.filter { it.length > 2 }
        val termFreq = words.groupingBy { it }.eachCount()

        // TF-IDF = TF * IDF, where IDF = ln(totalDocs / docsWithTerm) + 1
        return termFreq.mapValues { (term, freq) ->
            val docsWithTerm = documentFrequency[term] ?: 0
            val idf = if (docsWithTerm > 0) kotlin.math.ln(totalDocs.toDouble() / docsWithTerm) + 1 else 0.0
            val tf = freq.toDouble() / words.size
            tf * idf
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 3: Cosine Similarity (ML metric)
    // ─────────────────────────────────────────────────────────────────────
    private fun cosineSimilarity(vec1: Map<String, Double>, vec2: Map<String, Double>): Double {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0

        val commonKeys = vec1.keys.intersect(vec2.keys)
        val dotProduct = commonKeys.sumOf { (vec1[it] ?: 0.0) * (vec2[it] ?: 0.0) }
        
        val mag1 = sqrt(vec1.values.sumOf { it * it })
        val mag2 = sqrt(vec2.values.sumOf { it * it })

        return if (mag1 > 0 && mag2 > 0) dotProduct / (mag1 * mag2) else 0.0
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 4: Generate match label
    // ─────────────────────────────────────────────────────────────────────
    private fun getMatchLabel(score: Int): String = when {
        score >= 85 -> "Excellent Match"
        score >= 65 -> "Good Match"
        score >= 45 -> "Fair Match"
        else -> "Low Match"
    }

    // ─────────────────────────────────────────────────────────────────────
    // Step 5: Generate reasons
    // ─────────────────────────────────────────────────────────────────────
    private fun generateMatchReasons(profile: UserProfile, job: Job, score: Int): List<String> {
        val reasons = mutableListOf<String>()

        if (score == 0) return listOf("No matching profile terms found. Review the job description.")

        // Check skill match
        val matchedSkills = profile.skills.map { it.lowercase() }
        val jobReqs = (job.requirements + job.aboutRole).joinToString(" ").lowercase()
        val skillMatches = matchedSkills.filter { jobReqs.contains(it) }
        if (skillMatches.isNotEmpty()) {
            reasons.add("Mentions ${skillMatches.size} of your skills")
        }

        // Check experience level
        if (profile.yearsOfExperience > 0) {
            reasons.add("${profile.yearsOfExperience} years of experience")
        }

        // Check category preference
        if (profile.preferredCategories.any { it.equals(job.category, ignoreCase = true) }) {
            reasons.add("Matches preferred category")
        }

        // Check level
        if (profile.preferredLevel.isNotEmpty() && profile.preferredLevel.equals(job.level, ignoreCase = true)) {
            reasons.add("Matches preferred level")
        }

        return reasons.ifEmpty { listOf("Based on shared terms in your profile and this job") }
    }

    private fun findMatchedSkills(userSkills: List<String>, jobRequirements: List<String>): List<String> {
        val reqText = jobRequirements.joinToString(" ").lowercase()
        return userSkills.filter { reqText.contains(it.lowercase()) }
    }

    private fun findMissingSkills(userSkills: List<String>, jobRequirements: List<String>): List<String> {
        val userSkillsLower = userSkills.map { it.lowercase() }
        val reqText = jobRequirements.joinToString(" ").lowercase()

        // Simple keyword extraction from requirements
        val reqKeywords = reqText.split(Regex("[,\\s]+"))
            .filter { it.length > 3 }
            .filter { !it.contains("year") && !it.contains("experience") }
            .map { it.lowercase() }
            .distinct()

        return reqKeywords.filter { req ->
            userSkillsLower.none { userSkill -> req.contains(userSkill) || userSkill.contains(req) }
        }.take(3)
    }
}