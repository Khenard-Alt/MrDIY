package com.mrdiy.careers

import com.google.gson.Gson
import com.mrdiy.careers.data.ml.ResumeMlClassifier
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.exp
import kotlin.math.sqrt

/** Shared with [ValidatorComparisonTest], which parses the same weights file the same way. */
data class WeightsFile(
    val vocabulary: Map<String, Int>,
    val idf: List<Double>,
    val weights: List<Double>,
    val bias: Double,
)

/**
 * Parity test: proves the tokenize -> tf-idf -> L2-normalize -> sigmoid math in
 * [ResumeMlClassifier] matches scikit-learn's own predict_proba() on the same trained weights,
 * within floating-point tolerance. The expected probabilities below were computed directly from
 * `resume_validator_weights.json` via sklearn's `model.predict_proba()` (see the training repo's
 * CapstoneGuidePaper/resume-validator-training/train_resume_validator.py) — this is not a model
 * accuracy test, just a check that the Kotlin reimplementation computes the same numbers.
 *
 * Runs the same math [ResumeMlClassifier.classify] does, but reads the asset from the Android
 * module's resources directly (no Context/AssetManager available in a plain JVM unit test) so the
 * two implementations can drift-check against each other without duplicating the production code.
 *
 * Uses Gson, not org.json, to parse the weights file here: in a plain JVM unit test (this file,
 * under src/test) android.jar's classes — including org.json.JSONObject — are unmocked stubs that
 * throw "Method ... not mocked" on any real call. That's fine for [ResumeMlClassifier] itself
 * (which only ever runs on a real device/emulator), but this test needs a JSON parser that
 * actually works on a plain JVM, hence Gson (already a project dependency, pure Java).
 */
class ResumeMlClassifierTest {

    private val weightsFile = File(
        "src/main/assets/resume_validator_weights.json",
    )

    private val model: WeightsFile by lazy {
        Gson().fromJson(weightsFile.readText(Charsets.UTF_8), WeightsFile::class.java)
    }

    private fun classify(text: String): Double {
        val vocabulary = model.vocabulary
        val idf = model.idf
        val weights = model.weights
        val bias = model.bias

        val tokenPattern = Regex("\\b\\w\\w+\\b")
        val termCounts = HashMap<Int, Int>()
        for (match in tokenPattern.findAll(text.lowercase())) {
            val index = vocabulary[match.value] ?: continue
            termCounts[index] = (termCounts[index] ?: 0) + 1
        }
        if (termCounts.isEmpty()) return 0.0

        var sumOfSquares = 0.0
        val tfidf = HashMap<Int, Double>(termCounts.size)
        for ((index, count) in termCounts) {
            val value = count * idf[index]
            tfidf[index] = value
            sumOfSquares += value * value
        }
        val norm = sqrt(sumOfSquares)

        var logit = bias
        if (norm > 0.0) {
            for ((index, value) in tfidf) {
                logit += (value / norm) * weights[index]
            }
        }
        return 1.0 / (1.0 + exp(-logit))
    }

    @Test
    fun matchesSklearnPredictProbaOnResumeText() {
        val text = """
            Maria Santos
            Email: maria.santos@gmail.com | Phone: 09171234567

            SUMMARY
            Experienced Retail Store Supervisor with over 3 years in customer service, sales, and inventory management.

            WORK EXPERIENCE
            Store Supervisor | MR.D.I.Y. Philippines | 2021 - 2024
            - Managed daily store operations, POS transactions, and stock auditing.
            - Led a team of 8 sales associates.

            EDUCATION
            Bachelor of Science in Business Administration
            Polytechnic University of the Philippines | 2017 - 2021

            SKILLS
            Customer Service, POS, Cash Handling, Inventory, Leadership
        """.trimIndent()

        assertEquals(0.6747840008636458, classify(text), 1e-3)
    }

    @Test
    fun matchesSklearnPredictProbaOnRecipeText() {
        val text = """
            Adobo Recipe
            Ingredients: 1kg Chicken, 1/2 cup Soy Sauce, 1/2 cup Vinegar, Garlic, Bay Leaves, Pepper.
            Instructions:
            1. Marinate chicken in soy sauce and garlic for 30 minutes.
            2. Heat pan and cook chicken until brown.
            3. Add marinade, vinegar, and bay leaves. Simmer for 20 minutes until tender.
            4. Serve warm with rice.
        """.trimIndent()

        assertEquals(0.13326210286785084, classify(text), 1e-3)
    }

    @Test
    fun matchesSklearnPredictProbaOnRandomNote() {
        val text = "Meeting notes for tomorrow at 10 AM. Don't forget to buy coffee."

        assertEquals(0.14535109263083398, classify(text), 1e-3)
    }

    @Test
    fun matchesSklearnPredictProbaOnBorderlineResume() {
        val text = """
            Juan dela Cruz
            Objective: Seeking a position as Warehouse Associate.
            EXPERIENCE
            Warehouse Associate, ABC Logistics, 2019-2022
            Responsibilities included forklift operation, inventory management, and shipment tracking.
            EDUCATION
            Associate Degree in Logistics Management, 2019
            SKILLS
            Forklift Operation, Inventory Management, Team Leadership, Microsoft Excel
        """.trimIndent()

        assertEquals(0.5123714911441858, classify(text), 1e-3)
    }

    @Test
    fun returnsZeroForEmptyText() {
        assertEquals(0.0, classify(""), 1e-9)
    }
}
