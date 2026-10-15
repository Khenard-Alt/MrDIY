package com.mrdiy.careers

import com.google.gson.Gson
import com.mrdiy.careers.data.repository.ResumeValidator
import org.junit.Test
import java.io.File
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Side-by-side accuracy/precision/recall/F1 of the old rule-based [ResumeValidator] versus the
 * new trained model (CapstoneGuidePaper/resume-validator-training, 99.5% F1 on its own held-out
 * split), evaluated on the hand-written sample corpus already used by [ResumeValidatorTest] and
 * [ResumeValidationTest]. This is a small, labeled-by-hand set (not the training corpus), so it
 * doubles as an independent sanity check of the exported model on text styles (Tagalog headers,
 * paragraph-style resumes, long documents) the Hugging Face training data didn't necessarily cover.
 *
 * Reimplements the trained model's math locally for the same reason [ResumeMlClassifierTest]
 * does: a plain JVM unit test has no Android Context/AssetManager to call
 * [com.mrdiy.careers.data.ml.ResumeMlClassifier] directly.
 */
class ValidatorComparisonTest {

    private data class Sample(val text: String, val isResume: Boolean, val label: String)

    private val samples = listOf(
        Sample(RESUME_ONE_PAGE, true, "typical one-page resume"),
        Sample(RESUME_MOBILE_ONLY, true, "resume with mobile number, no email"),
        Sample(RESUME_TAGALOG, true, "resume with Tagalog section headers"),
        Sample(RESUME_IT, true, "IT resume mentioning installation/wireless"),
        Sample(RESUME_PARAGRAPH, true, "paragraph-style resume, no headings"),
        Sample(NOT_RESUME_MANUAL, false, "laptop user manual"),
        Sample(NOT_RESUME_INVOICE, false, "invoice"),
        Sample(NOT_RESUME_LOREM, false, "lorem ipsum filler text"),
        Sample(NOT_RESUME_RECIPE, false, "adobo recipe"),
        Sample(NOT_RESUME_ARTICLE, false, "article quoting a few resume-like words"),
        Sample("", false, "empty document"),
    )

    /**
     * Held-out Taglish/Filipino samples (§5.6 checklist item 5): the training data sourced from
     * Hugging Face (Kaggle "Resume Dataset" mirror + AG News) is 100% English, but real MR.D.I.Y.
     * applicants may write resumes in Taglish or heavily-localized English. These are evaluated
     * separately from [samples] — a different distribution than training, so a lower score here
     * is expected and informative rather than a bug, and is reported on its own rather than mixed
     * into the main accuracy/F1 numbers above.
     */
    private val taglishSamples = listOf(
        Sample(RESUME_TAGALOG_HEAVY, true, "Taglish resume, heavy code-switching"),
        Sample(RESUME_TAGALOG_OBJECTIVE, true, "Taglish resume, objective + experience in Filipino"),
        Sample(NOT_RESUME_TAGALOG_RECIPE, false, "Filipino recipe (sinigang)"),
        Sample(NOT_RESUME_TAGALOG_NOTE, false, "Filipino meeting/reminder note"),
    )

    @Test
    fun reportsAccuracyOnHeldOutTaglishSamples() {
        var ruleCorrect = 0
        var modelCorrect = 0

        println("Taglish held-out set | actual | rules | model | model_p")
        for (sample in taglishSamples) {
            val ruleValid = ResumeValidator.check(sample.text).valid
            val modelProbability = classifyWithTrainedModel(sample.text)
            val modelValid = modelProbability >= 0.5

            if (ruleValid == sample.isResume) ruleCorrect++
            if (modelValid == sample.isResume) modelCorrect++

            println(
                "${sample.label.padEnd(45)} | ${sample.isResume.toString().padEnd(6)} | " +
                    "${ruleValid.toString().padEnd(5)} | ${modelValid.toString().padEnd(5)} | " +
                    "%.3f".format(modelProbability),
            )
        }

        println()
        println(
            "Rule-based accuracy on Taglish set: %.2f (%d/%d)"
                .format(ruleCorrect.toDouble() / taglishSamples.size, ruleCorrect, taglishSamples.size),
        )
        println(
            "Trained model accuracy on Taglish set: %.2f (%d/%d)"
                .format(modelCorrect.toDouble() / taglishSamples.size, modelCorrect, taglishSamples.size),
        )
        // No pass/fail assertion: this is a known-limitation measurement, not a release gate.
        // Paste the printed numbers into AIplanning.md §5.6 as the documented distribution-
        // mismatch result (checklist item 5) rather than silently assuming parity with English.
    }

    @Test
    fun comparesOldRulesAgainstTrainedModel() {
        var ruleCorrect = 0
        var modelCorrect = 0
        var ruleTruePositive = 0
        var ruleFalsePositive = 0
        var ruleFalseNegative = 0
        var modelTruePositive = 0
        var modelFalsePositive = 0
        var modelFalseNegative = 0

        println("sample                                            | actual | rules | model | model_p")
        for (sample in samples) {
            val ruleValid = ResumeValidator.check(sample.text).valid
            val modelProbability = classifyWithTrainedModel(sample.text)
            val modelValid = modelProbability >= 0.5

            if (ruleValid == sample.isResume) ruleCorrect++
            if (modelValid == sample.isResume) modelCorrect++

            if (sample.isResume && ruleValid) ruleTruePositive++
            if (!sample.isResume && ruleValid) ruleFalsePositive++
            if (sample.isResume && !ruleValid) ruleFalseNegative++

            if (sample.isResume && modelValid) modelTruePositive++
            if (!sample.isResume && modelValid) modelFalsePositive++
            if (sample.isResume && !modelValid) modelFalseNegative++

            println(
                "${sample.label.padEnd(50)} | ${sample.isResume.toString().padEnd(6)} | " +
                    "${ruleValid.toString().padEnd(5)} | ${modelValid.toString().padEnd(5)} | " +
                    "%.3f".format(modelProbability),
            )
        }

        fun f1(tp: Int, fp: Int, fn: Int): Double {
            val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
            val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
            return if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        }

        val ruleAccuracy = ruleCorrect.toDouble() / samples.size
        val modelAccuracy = modelCorrect.toDouble() / samples.size
        val ruleF1 = f1(ruleTruePositive, ruleFalsePositive, ruleFalseNegative)
        val modelF1 = f1(modelTruePositive, modelFalsePositive, modelFalseNegative)

        println()
        println("Rule-based  — accuracy: %.3f, F1: %.3f".format(ruleAccuracy, ruleF1))
        println("Trained model — accuracy: %.3f, F1: %.3f".format(modelAccuracy, modelF1))

        // Not a strict pass/fail gate — this corpus is tiny (11 hand-picked samples) and both
        // validators are expected to do well on it. The real evidence is the printed table above,
        // meant to be pasted into AIplanning.md §5.6's "Ikukumpara" comparison writeup.
    }

    // ---- trained-model math (mirrors ResumeMlClassifier; see its own parity test) ----
    // Uses Gson, not org.json, for the same reason as ResumeMlClassifierTest: a plain JVM unit
    // test gets unmocked android.jar stubs for org.json.JSONObject, which throw "Method ... not
    // mocked" on any real call.

    private val weightsFile = File("src/main/assets/resume_validator_weights.json")
    private val model: WeightsFile by lazy {
        Gson().fromJson(weightsFile.readText(Charsets.UTF_8), WeightsFile::class.java)
    }

    private fun classifyWithTrainedModel(text: String): Double {
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

    companion object {
        private val RESUME_ONE_PAGE = """
            JOSHUA ALLEN P. BALASTA
            Angono, Rizal | 09396326936 | joshuabalasta00@gmail.com

            OBJECTIVE
            To work as a cashier where I can use my customer service skills.

            WORK EXPERIENCE
            Sales Associate - Sample Hardware Store, Taytay
            Jan 2024 - Present
            - Assisted customers and operated the POS.

            EDUCATION
            BS Information Technology, Our Lady of Fatima University, 2022 - 2026

            SKILLS
            Customer service, POS, inventory, cash handling
        """.trimIndent()

        private val RESUME_MOBILE_ONLY =
            "Maria Reyes\nContact: +63 917 555 1234\n\nEXPERIENCE\nCashier at a grocery, 2021 - 2024\n\n" +
                "SKILLS\nCash handling, customer service"

        private val RESUME_TAGALOG =
            "Juan dela Cruz\njuan@yahoo.com | 09181234567\n\nKARANASAN SA TRABAHO\nTindero, Pasig Market " +
                "2020 - 2023\n\nEDUKASYON\nSenior High School 2016 - 2020\n\nMGA KASANAYAN\nCashier, customer service"

        private val RESUME_IT = """
            Ana Lim | ana.lim@gmail.com | 09171112222

            EXPERIENCE
            IT Support, 2021 - 2024
            Software installation, wireless network troubleshooting, printer setup.

            EDUCATION
            BS Information Technology 2017 - 2021

            SKILLS
            Python, SQL, Git, Excel
        """.trimIndent()

        private val RESUME_PARAGRAPH =
            "Pedro Santos, 09175550000, pedro@gmail.com. I worked as a cashier and sales associate from 2019 " +
                "to 2023 with strong customer service, cash handling and inventory skills at a retail store in Marikina."

        private val NOT_RESUME_MANUAL = """
            Congratulations, you and your MacBook Air were made for each other.
            Built-in iSight camera
            Video chat with up to three friends anywhere in the world at the same time.
            www.apple.com/macosx  Mac Help  iSight
            Finder
            Browse the contents of your computer using Cover Flow.
            Time Machine
            Automatically back up your files to an external hard drive.
            Setup instructions, software installation and wireless network troubleshooting are in the user guide.
            Battery and charger specifications. One year limited warranty. Copyright 2008 Apple Inc. All rights reserved.
        """.trimIndent()

        private val NOT_RESUME_INVOICE = """
            INVOICE No. 2026-0412
            Bill to: Santos Hardware Trading, Antipolo City
            Item                Qty   Unit price   Amount
            Paint 4L              10      850.00    8,500.00
            Brush set              5      120.00      600.00
            Total amount due: PHP 9,100.00. Please pay within 30 days. Contact us at sales@trading.ph or 09171234567.
        """.trimIndent()

        private val NOT_RESUME_LOREM =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore " +
                "et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip."

        private val NOT_RESUME_RECIPE =
            "Adobo Recipe\nIngredients: 1kg Chicken, 1/2 cup Soy Sauce, vinegar, garlic, bay leaves.\n" +
                "Instructions: Marinate the chicken for 30 minutes, then simmer for 20 minutes until tender."

        private val NOT_RESUME_ARTICLE =
            "Education\nTeachers say that after many years in the classroom, customer service and teamwork " +
                "matter as much as sales numbers when a store hires new staff members."

        private val RESUME_TAGALOG_HEAVY = """
            Angelica Ramos
            09209876543 | angelica.ramos@gmail.com | Taguig City

            TUNGKOL SA AKIN
            Masipag at may 2 years experience bilang sales associate. Mahilig makipag-usap sa customers
            at marunong gumamit ng POS system.

            KARANASAN SA TRABAHO
            Sales Associate — SM Hypermarket, Taguig
            Marso 2022 - Kasalukuyan
            - In-charge sa pag-restock ng paninda at pag-assist sa mga customer.
            - Nag-handle ng cash register at nag-reconcile ng daily sales.

            EDUKASYON
            Senior High School Graduate, ABM Strand — 2020-2022

            KASANAYAN
            Customer service, cash handling, basic inventory, Microsoft Excel
        """.trimIndent()

        private val RESUME_TAGALOG_OBJECTIVE = """
            Rommel Bautista
            Email: rommel.bautista@yahoo.com | 09171239876

            LAYUNIN
            Naghahanap ng trabaho bilang warehouse staff kung saan magagamit ko ang aking
            karanasan sa paghahandle ng imbentaryo at pagpapadala ng mga paninda.

            KARANASAN
            Warehouse Helper, Puregold Distribution Center
            Hunyo 2021 - Disyembre 2023
            Responsable sa pag-ayos ng stock, pag-pack ng order, at pag-record ng inventory count.

            EDUKASYON
            High School Graduate — 2019

            KASANAYAN
            Inventory management, forklift (basic), pagiging maasikaso sa detalye
        """.trimIndent()

        private val NOT_RESUME_TAGALOG_RECIPE =
            "Sinigang na Baboy Recipe\nSangkap: 1/2 kilo baboy, kamatis, sibuyas, gabi, sitaw, " +
                "labanos, gabi, sampalok mix.\nParaan: Pakuluan ang baboy hanggang lumambot, " +
                "pagkatapos ilagay ang gulay at timplahin ng sampalok. Lutuin ng 20 minuto."

        private val NOT_RESUME_TAGALOG_NOTE =
            "Paalala para sa miting bukas ng 10 AM. Huwag kalimutan dalhin ang mga dokumento " +
                "at bumili ng kape para sa lahat."
    }
}
