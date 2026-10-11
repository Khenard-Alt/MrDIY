package com.mrdiy.careers.data.ml

/** Skills from retail, store operations, logistics and office roles as well as IT. */
object ResumeSkills {
    private val vocabulary = listOf(
        "customer service", "cash handling", "cashier", "point of sale", "POS",
        "inventory", "stock management", "merchandising", "sales", "retail",
        "leadership", "teamwork", "communication", "supervision", "staff scheduling",
        "warehouse", "logistics", "forklift", "picking", "packing", "delivery",
        "recruitment", "payroll", "human resources", "accounting", "bookkeeping",
        "Excel", "Microsoft Office", "data entry", "administration", "training",
        "Kotlin", "Java", "Android", "Python", "JavaScript", "React", "SQL",
        "Git", "Docker", "AWS", "Firebase", "machine learning", "data analysis",
        "HTML", "CSS", "Node", "Flutter"
    )

    /** Other ways people write the same skill; each maps to the skill name above (lower case). */
    private val aliases = mapOf(
        "pos" to "point of sale",
        "customer care" to "customer service",
        "client service" to "customer service",
        "cashiering" to "cashier",
        "cash register" to "cash handling",
        "stock control" to "stock management",
        "inventory management" to "inventory",
        "ms office" to "microsoft office",
        "office applications" to "microsoft office",
        "spreadsheets" to "excel",
        "team work" to "teamwork",
        "team player" to "teamwork",
        "people management" to "supervision",
        "hr" to "human resources"
    )

    private val canonicalPatterns: List<Pair<String, Regex>> =
        (vocabulary.map { it.lowercase() to it.lowercase() } + aliases.map { (alias, canonical) -> canonical to alias })
            .map { (canonical, term) ->
                canonical to Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            }

    /**
     * The skills in the text as lower-case canonical names, whichever way they are written
     * ("POS" and "point of sale" are the same skill). Same list as lib/skills.ts in the admin.
     */
    fun extractCanonical(text: String): Set<String> =
        canonicalPatterns.filter { (_, regex) -> regex.containsMatchIn(text) }.map { it.first }.toSet()

    fun extract(text: String): List<String> = vocabulary.filter { skill ->
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(skill)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
    }
}
