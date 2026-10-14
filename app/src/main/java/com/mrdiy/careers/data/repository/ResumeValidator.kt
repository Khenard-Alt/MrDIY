package com.mrdiy.careers.data.repository

import com.mrdiy.careers.data.ml.ResumeSkills

/**
 * Decides whether the text of an uploaded file is really a resume, before it is stored or used for matching.
 * A file is accepted only when several independent signs of a resume agree (a manual, a recipe or an invoice
 * may contain one or two of them by chance, but not all):
 *
 *   1. SECTIONS   at least two different resume sections written as headings on their own short line
 *                 (experience, education, skills, summary/personal details, references/certifications)
 *   2. CONTACT    an email address or a Philippine mobile number
 *   3. TIMELINE   a date range such as "2021 - 2024 / Present", a "Jan 2022" style date, or "3 years of experience"
 *   4. SKILLS     known job skills in the text
 *
 * and it is refused when it looks like a manual or a long document (many product and manual words,
 * or far more text than any resume).
 *
 * Superseded as the primary check by [com.mrdiy.careers.data.ml.ResumeMlClassifier] (a trained
 * logistic-regression model, 99.5% F1 — see AIplanning.md §5.6), which [ResumeRepository.parseResume]
 * now calls instead. Kept here as the fallback if the trained model's bundled asset fails to load,
 * and as the baseline for comparison testing against the trained model.
 */
object ResumeValidator {

    data class Verdict(val valid: Boolean, val reason: String)

    private const val MIN_CHARS = 60
    private const val MAX_CHARS = 40_000

    private val SECTION_GROUPS: Map<String, List<String>> = mapOf(
        "experience" to listOf(
            "work experience", "professional experience", "relevant experience", "employment history", "work history",
            "career history", "employment", "experience", "karanasan sa trabaho", "karanasan", "trabaho"
        ),
        "education" to listOf(
            "educational background", "educational attainment", "academic background", "education", "academic", "edukasyon"
        ),
        "skills" to listOf(
            "technical skills", "key skills", "skills and abilities", "core competencies", "competencies", "skills",
            "qualifications", "mga kasanayan", "kasanayan"
        ),
        "summary" to listOf(
            "professional summary", "career objective", "objective", "summary", "personal profile", "profile",
            "about me", "personal information", "personal details", "personal data", "contact information"
        ),
        "extras" to listOf(
            "character references", "references", "certifications", "licenses", "seminars", "trainings attended",
            "awards", "achievements", "sanggunian"
        )
    )

    // Words that fill product manuals, brochures and legal papers but almost never a resume.
    private val MANUAL_WORDS = listOf(
        "warranty", "installation", "user guide", "manual", "click", "copyright", "all rights reserved",
        "table of contents", "troubleshooting", "chapter", "download", "figure", "www.", "terms and conditions",
        "privacy policy", "specifications", "battery", "charger", "wireless", "instructions", "product"
    )

    private val EMAIL = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PH_MOBILE = Regex("(?:\\+?63|0)\\s?9\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{4}")
    private val TIMELINE = Regex(
        "(?:19|20)\\d{2}\\s*(?:-|–|—|to)\\s*(?:(?:19|20)\\d{2}|present|current|now)|" +
            "\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s+(?:19|20)\\d{2}\\b|" +
            "\\b\\d+\\+?\\s*(?:years?|yrs?)\\s+(?:of\\s+)?(?:work\\s+)?experience\\b",
        RegexOption.IGNORE_CASE
    )

    fun check(text: String): Verdict {
        val trimmed = text.trim()
        if (trimmed.length < MIN_CHARS) {
            return Verdict(
                false,
                "We could not read enough text in this file. If it is a scanned picture, upload a PDF or Word resume that contains text."
            )
        }
        if (trimmed.length > MAX_CHARS) {
            return Verdict(false, "This looks like a long document, not a resume. Upload your resume (usually 1 to 3 pages).")
        }

        val lower = trimmed.lowercase()
        val manualHits = MANUAL_WORDS.count { Regex("(?<![a-z])${Regex.escape(it)}").containsMatchIn(lower) }
        if (manualHits >= 4) {
            return Verdict(false, "This looks like a manual or a product document, not a resume. Upload your resume.")
        }

        val sections = countSections(trimmed)
        val hasContact = EMAIL.containsMatchIn(trimmed) || PH_MOBILE.containsMatchIn(trimmed)
        val hasTimeline = TIMELINE.containsMatchIn(trimmed)
        val skillCount = ResumeSkills.extract(trimmed).size

        val looksLikeResume =
            (sections >= 2 && (hasContact || hasTimeline)) ||
                (sections >= 1 && hasContact && hasTimeline && skillCount >= 2) ||
                (hasContact && hasTimeline && skillCount >= 3)

        return if (looksLikeResume) {
            Verdict(true, "")
        } else {
            Verdict(
                false,
                "This does not look like a resume. A resume has sections such as Work Experience, Education and Skills, " +
                    "and your contact details (email or mobile number)."
            )
        }
    }

    /** How many different resume sections appear as a heading on their own short line. */
    private fun countSections(text: String): Int {
        val found = mutableSetOf<String>()
        for (rawLine in text.lines()) {
            val line = rawLine.lowercase().replace(Regex("[^\\p{L} ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (line.isEmpty() || line.length > 45 || line.split(" ").size > 6) continue
            for ((group, phrases) in SECTION_GROUPS) {
                if (group in found) continue
                if (phrases.any { phrase -> Regex("(?<![a-z])${Regex.escape(phrase)}(?![a-z])").containsMatchIn(line) }) {
                    found += group
                }
            }
        }
        return found.size
    }
}
