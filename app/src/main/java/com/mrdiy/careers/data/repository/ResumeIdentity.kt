package com.mrdiy.careers.data.repository

import java.text.Normalizer

/**
 * Checks that an uploaded resume belongs to the person who owns the account, by comparing the name on the
 * resume with the full name they signed up with. A resume that someone else wrote would give wrong matches and
 * wrong information to HR, so it is refused; if no name can be found at all, it is accepted with a reminder.
 */
object ResumeIdentity {

    enum class Result { MATCH, UNKNOWN, MISMATCH }

    data class Verdict(val result: Result, val resumeName: String?, val message: String)

    private val NOT_A_NAME = setOf(
        "resume", "curriculum", "vitae", "cv", "objective", "summary", "profile", "education", "experience",
        "skills", "contact", "personal", "information", "references", "work", "history", "career", "address",
        "email", "phone", "mobile", "page", "biodata", "bio", "data"
    )

    fun check(accountName: String, resumeText: String): Verdict {
        val accountWords = words(accountName)
        // Initials such as the "P." in "Joshua Allen P. Balasta" are too short to compare.
        val nameWords = accountWords.filter { it.length >= 3 }.distinct()
        if (nameWords.isEmpty()) return Verdict(Result.UNKNOWN, null, "")

        val textWords = words(resumeText).toSet()
        val matched = nameWords.count { it in textWords }
        val required = if (nameWords.size >= 2) 2 else 1
        if (matched >= required) return Verdict(Result.MATCH, null, "")

        val resumeName = nameOnTop(resumeText)
        val shown = accountName.trim()
        return if (resumeName != null) {
            Verdict(
                Result.MISMATCH, resumeName,
                "The name on this resume ($resumeName) does not match your account name ($shown). " +
                    "Please upload your own resume, or correct your name in Edit Profile so it matches your resume and IDs."
            )
        } else {
            Verdict(
                Result.UNKNOWN, null,
                "We could not find your name ($shown) in this resume. Please make sure it is your own resume " +
                    "and that your name is written on it, so your matches are accurate."
            )
        }
    }

    /** Lower-case letters only, accents removed ("Piñas" becomes "pinas"). */
    private fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z]+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotEmpty() }

    /** The name written at the top of a resume: one of the first lines, 2 to 4 capitalised words, no digits. */
    private fun nameOnTop(text: String): String? =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(6).firstOrNull { line ->
            val cleaned = line.replace(Regex("[.,]"), " ").trim()
            val parts = cleaned.split(Regex("\\s+"))
            line.length <= 45 &&
                parts.size in 2..4 &&
                line.none { it.isDigit() || it == '@' || it == '|' || it == ':' } &&
                parts.all { it.firstOrNull()?.isUpperCase() == true || it.length <= 2 } &&
                parts.none { it.lowercase() in NOT_A_NAME }
        }?.replace(Regex("\\s+"), " ")
}
