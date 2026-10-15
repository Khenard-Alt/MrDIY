package com.mrdiy.careers.data.repository

import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.mrdiy.careers.data.ml.ResumeMlClassifier
import com.mrdiy.careers.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class ResumeRepository(private val context: Context) {

    suspend fun extractText(bytes: ByteArray, extension: String): String = withContext(Dispatchers.IO) {
        when (extension) {
            "pdf" -> {
                PDFBoxResourceLoader.init(context)
                PDDocument.load(bytes).use { document ->
                    require(!document.isEncrypted)
                    require(document.numberOfPages <= 100)
                    PDFTextStripper().getText(document).take(200_000)
                }
            }
            "docx" -> extractDocxText(bytes).take(200_000)
            else -> bytes.toString(Charsets.UTF_8).take(200_000)
        }
    }

    // DOCX is a ZIP archive; the body text lives in word/document.xml as a series
    // of <w:t> runs. We stream-parse just that entry instead of pulling in Apache POI.
    private fun extractDocxText(bytes: ByteArray): String {
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = android.util.Xml.newPullParser()
                    parser.setInput(InputStreamReader(zip, Charsets.UTF_8))

                    val text = StringBuilder()
                    var inTextRun = false
                    var event = parser.eventType

                    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                                when (parser.name) {
                                    "t" -> inTextRun = true
                                    "p", "br", "tab" -> text.append(if (parser.name == "tab") "\t" else "\n")
                                }
                            }
                            org.xmlpull.v1.XmlPullParser.TEXT -> if (inTextRun) text.append(parser.text)
                            org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "t") inTextRun = false
                        }
                        event = parser.next()
                    }
                    return text.toString()
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("Unsupported file")
    }

    suspend fun parseResume(resumeText: String): UserProfile = withContext(Dispatchers.Default) {
        val verdict = ResumeValidator.check(resumeText)
        logModelComparison(resumeText)
        if (!verdict.valid) throw IllegalArgumentException(verdict.reason)
        parseResumeLocally(resumeText)
    }

    /**
     * The rule-based [ResumeValidator] is the real gate — on the capstone's own hand-labeled test
     * corpus (short, casual, Taglish-inclusive resumes) the trained model (see
     * [ResumeMlClassifier]) scored only 72.7% accuracy / 40% recall, missing 3 of 5 real resumes.
     * It was trained on Hugging Face's full-length, formal, English-only resumes, a much easier
     * and different distribution than real MR.D.I.Y. applicants write in. Promoting it to the
     * primary gate would reject valid applicants that pass today, so this is advisory-only for
     * now: it runs alongside the rule-based check and its verdict is logged for comparison, but
     * never blocks an upload on its own. See AIplanning.md §5.6 for the full writeup.
     */
    private fun logModelComparison(resumeText: String) {
        try {
            val verdict = ResumeMlClassifier.classify(context, resumeText)
            Log.d(TAG, "Trained model (advisory only): ${verdict.reason}")
        } catch (error: Exception) {
            Log.w(TAG, "Trained model unavailable — rule-based validator is unaffected", error)
        }
    }

    /**
     * Same as [parseResume] but skips the validity check — for backfilling resume_text on a
     * file that was already accepted at upload time, where re-validating could only reject it.
     */
    suspend fun parseResumeUnchecked(resumeText: String): UserProfile = withContext(Dispatchers.Default) {
        parseResumeLocally(resumeText)
    }

    companion object {
        private const val TAG = "ResumeRepository"

        /**
         * True if the text is a real resume, per the rule-based [ResumeValidator]. Used by plain
         * JVM unit tests (no Android [Context] available there to load the trained model's bundled
         * asset) and as the fallback path inside [classify]. Real uploads go through [parseResume],
         * which prefers the trained model.
         */
        fun validateResumeContent(text: String): Boolean = ResumeValidator.check(text).valid
    }

    private fun parseResumeLocally(text: String): UserProfile {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = lines.firstOrNull() ?: ""
        val email = lines.find { it.contains("@") }?.extractEmail() ?: ""
        val phone = lines.find { it.contains(Regex("\\d{10,}")) }?.extractPhone() ?: ""

        val skills = com.mrdiy.careers.data.ml.ResumeSkills.extract(text)

        return UserProfile(
            fullName = name,
            firstName = name.split(" ").firstOrNull() ?: "",
            lastName = name.split(" ").drop(1).joinToString(" ").ifEmpty { name },
            email = email,
            phone = phone,
            skills = skills,
            resumeText = text
        )
    }

    private fun String.extractEmail(): String {
        return Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}").find(this)?.value ?: ""
    }

    private fun String.extractPhone(): String {
        return Regex("\\d{10,12}").find(this)?.value?.take(11) ?: ""
    }

}
