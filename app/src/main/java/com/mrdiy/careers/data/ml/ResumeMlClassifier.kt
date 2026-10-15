package com.mrdiy.careers.data.ml

import android.content.Context
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Resume/not-resume classifier. Advisory only — [com.mrdiy.careers.data.repository.ResumeRepository]
 * logs its verdict alongside the rule-based [com.mrdiy.careers.data.repository.ResumeValidator],
 * which remains the actual gate. On the capstone's own hand-labeled test corpus (short, casual,
 * Taglish-inclusive resumes) this model scored only 72.7% accuracy / 40% recall, well below the
 * rule-based validator — see [ResumeValidator]'s doc comment and AIplanning.md §5.6.
 *
 * Loads a logistic-regression model trained offline in
 * CapstoneGuidePaper/resume-validator-training/train_resume_validator.py (scikit-learn
 * TfidfVectorizer + LogisticRegression, 99.5% F1 on its own, easier, held-out test split) and
 * reimplements its math here so the same weights work without any Python runtime in production.
 *
 * The tokenizer/IDF/normalization steps below must match scikit-learn's
 * TfidfVectorizer(lowercase=True, stop_words="english") defaults exactly, or the dot product
 * against the trained [weights] will not mean what it meant during training.
 */
object ResumeMlClassifier {

    data class MlVerdict(
        val valid: Boolean,
        val confidence: Double,
        val source: String,
        val reason: String,
    )

    private const val ASSET_NAME = "resume_validator_weights.json"
    private const val LOW_CONFIDENCE_THRESHOLD = 0.70

    @Volatile
    private var model: Model? = null

    private class Model(
        val vocabulary: Map<String, Int>,
        val idf: DoubleArray,
        val weights: DoubleArray,
        val bias: Double,
    )

    // No explicit stop-word list is needed here: TfidfVectorizer(stop_words="english") never
    // puts stop words into the fitted vocabulary, so the vocabulary lookup below already skips
    // them for free — looking them up again would be redundant.

    /** sklearn's default token pattern: runs of 2+ word characters. */
    private val TOKEN_PATTERN = Regex("\\b\\w\\w+\\b")

    private fun loadModel(context: Context): Model {
        model?.let { return it }

        synchronized(this) {
            model?.let { return it }

            val json = context.assets.open(ASSET_NAME).use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
            val root = JSONObject(json)

            val vocabJson = root.getJSONObject("vocabulary")
            val vocabulary = HashMap<String, Int>(vocabJson.length())
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val term = keys.next()
                vocabulary[term] = vocabJson.getInt(term)
            }

            val idfJson = root.getJSONArray("idf")
            val idf = DoubleArray(idfJson.length()) { idfJson.getDouble(it) }

            val weightsJson = root.getJSONArray("weights")
            val weights = DoubleArray(weightsJson.length()) { weightsJson.getDouble(it) }

            val bias = root.getDouble("bias")

            val loaded = Model(vocabulary, idf, weights, bias)
            model = loaded
            return loaded
        }
    }

    /**
     * Runs the trained model on [text]. Throws if the bundled asset is missing/corrupt — callers
     * (see [com.mrdiy.careers.data.repository.ResumeRepository]) must catch this and fall back to
     * the rule-based [com.mrdiy.careers.data.repository.ResumeValidator].
     */
    fun classify(context: Context, text: String): MlVerdict {
        val m = loadModel(context)

        val termCounts = HashMap<Int, Int>()
        for (match in TOKEN_PATTERN.findAll(text.lowercase())) {
            val index = m.vocabulary[match.value] ?: continue
            termCounts[index] = (termCounts[index] ?: 0) + 1
        }

        if (termCounts.isEmpty()) {
            return MlVerdict(
                valid = false,
                confidence = 0.0,
                source = "Own model",
                reason = "Document does not contain any recognizable resume vocabulary.",
            )
        }

        // raw term frequency x idf, then L2-normalize (TfidfVectorizer defaults: use_idf=True,
        // sublinear_tf=False, norm="l2").
        var sumOfSquares = 0.0
        val tfidf = HashMap<Int, Double>(termCounts.size)
        for ((index, count) in termCounts) {
            val value = count * m.idf[index]
            tfidf[index] = value
            sumOfSquares += value * value
        }
        val norm = sqrt(sumOfSquares)

        var logit = m.bias
        if (norm > 0.0) {
            for ((index, value) in tfidf) {
                logit += (value / norm) * m.weights[index]
            }
        }

        val probability = sigmoid(logit)
        val valid = probability >= 0.5
        val reason = when {
            valid && probability < LOW_CONFIDENCE_THRESHOLD ->
                "Classified as a resume with low confidence (${formatPercent(probability)}) — manual review recommended."
            !valid && probability > (1 - LOW_CONFIDENCE_THRESHOLD) ->
                "Classified as not a resume with low confidence (${formatPercent(1 - probability)}) — manual review recommended."
            valid -> "Classified as a resume (${formatPercent(probability)} confidence)."
            else -> "Classified as not a resume (${formatPercent(1 - probability)} confidence)."
        }

        return MlVerdict(
            valid = valid,
            confidence = if (valid) probability else 1 - probability,
            source = "Own model",
            reason = reason,
        )
    }

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    private fun formatPercent(fraction: Double): String = "${(fraction * 100).toInt()}%"
}
