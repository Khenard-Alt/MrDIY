package com.mrdiy.careers.data.search

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Approximate distance between free-text Philippine locations ("MR.DIY SM Masinag, Antipolo, Rizal",
 * "Angono, Rizal") using a built-in table of city/municipality centre points. No GPS or network needed.
 * Distances are centre-to-centre estimates, good enough to rank nearby branches, not for navigation.
 */
object LocationDistance {

    data class Point(val lat: Double, val lng: Double)

    private data class Place(val point: Point, val area: String)

    const val METRO_MANILA = "Metro Manila"
    const val RIZAL = "Rizal"
    const val CAVITE = "Cavite"
    const val LAGUNA = "Laguna"
    const val BULACAN = "Bulacan"

    private fun MutableMap<String, Place>.area(area: String, vararg entries: Pair<String, Point>) =
        entries.forEach { (name, point) -> put(name, Place(point, area)) }

    // Keys are normalized (lowercase, ñ→n, no dots, "sta"→"santa"); aliases may share a point.
    private val cities: Map<String, Place> = buildMap {
        area(
            METRO_MANILA,
            "manila" to Point(14.5995, 120.9842),
            "quezon city" to Point(14.6760, 121.0437),
            "makati" to Point(14.5547, 121.0244),
            "pasig" to Point(14.5764, 121.0851),
            "taguig" to Point(14.5176, 121.0509),
            "bgc" to Point(14.5509, 121.0503),
            "mandaluyong" to Point(14.5794, 121.0359),
            "marikina" to Point(14.6507, 121.1029),
            "paranaque" to Point(14.4793, 121.0198),
            "las pinas" to Point(14.4445, 120.9939),
            "muntinlupa" to Point(14.4081, 121.0415),
            "caloocan" to Point(14.6510, 120.9720),
            "navotas" to Point(14.6667, 120.9417),
            "malabon" to Point(14.6625, 120.9567),
            "valenzuela" to Point(14.7011, 120.9830),
            "san juan" to Point(14.6019, 121.0355),
            "pasay" to Point(14.5378, 121.0014),
            "pateros" to Point(14.5446, 121.0687)
        )
        area(
            RIZAL,
            "antipolo" to Point(14.5878, 121.1760),
            "cainta" to Point(14.5786, 121.1222),
            "taytay" to Point(14.5574, 121.1324),
            "angono" to Point(14.5266, 121.1536),
            "binangonan" to Point(14.4645, 121.1925),
            "san mateo" to Point(14.6969, 121.1219),
            "rodriguez" to Point(14.7600, 121.1400),
            "montalban" to Point(14.7600, 121.1400),
            "teresa" to Point(14.5600, 121.2083),
            "morong" to Point(14.5119, 121.2389),
            "baras" to Point(14.5230, 121.2650),
            "tanay" to Point(14.4980, 121.2850),
            "cardona" to Point(14.4881, 121.2290),
            "pililla" to Point(14.4850, 121.3070),
            "jalajala" to Point(14.3530, 121.3230)
        )
        area(
            CAVITE,
            "bacoor" to Point(14.4590, 120.9290),
            "imus" to Point(14.4297, 120.9367),
            "dasmarinas" to Point(14.3294, 120.9367),
            "kawit" to Point(14.4446, 120.9020),
            "cavite city" to Point(14.4791, 120.8970),
            "general trias" to Point(14.3869, 120.8817),
            "trece martires" to Point(14.2833, 120.8667),
            "silang" to Point(14.2306, 120.9750),
            "tagaytay" to Point(14.1153, 120.9621)
        )
        area(
            LAGUNA,
            "san pedro" to Point(14.3595, 121.0473),
            "binan" to Point(14.3333, 121.0833),
            "santa rosa" to Point(14.3122, 121.1114),
            "cabuyao" to Point(14.2724, 121.1251),
            "calamba" to Point(14.2117, 121.1653),
            "los banos" to Point(14.1699, 121.2441),
            "santa cruz" to Point(14.2785, 121.4163),
            "san pablo" to Point(14.0683, 121.3256)
        )
        area(
            BULACAN,
            "meycauayan" to Point(14.7344, 120.9572),
            "marilao" to Point(14.7578, 120.9483),
            "bocaue" to Point(14.7983, 120.9261),
            "santa maria" to Point(14.8183, 120.9563),
            "san jose del monte" to Point(14.8139, 121.0453),
            "malolos" to Point(14.8527, 120.8160),
            "baliuag" to Point(14.9547, 120.8969)
        )
        area("Cebu", "cebu city" to Point(10.3157, 123.8854))
        area("Davao", "davao city" to Point(7.1907, 125.4553))
    }

    // Used only when no city/municipality is named.
    private val provinces: Map<String, Place> = mapOf(
        "metro manila" to Place(Point(14.5995, 120.9842), METRO_MANILA),
        "ncr" to Place(Point(14.5995, 120.9842), METRO_MANILA),
        "rizal" to Place(Point(14.6037, 121.3084), RIZAL),
        "cavite" to Place(Point(14.2829, 120.8686), CAVITE),
        "laguna" to Place(Point(14.2691, 121.4113), LAGUNA),
        "bulacan" to Place(Point(14.7943, 120.8799), BULACAN),
        "cebu" to Place(Point(10.3157, 123.8854), "Cebu"),
        "davao" to Place(Point(7.1907, 125.4553), "Davao")
    )

    private fun normalize(text: String): String =
        text.lowercase()
            .replace('ñ', 'n')
            .replace(".", " ")
            .replace(Regex("\\bsta\\b"), "santa")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /**
     * Branch names often contain a city too ("SM City Manila"), so the city mentioned last wins,
     * since locations are written "branch, city, province". Falls back to a named province.
     */
    private fun place(location: String): Place? {
        val text = " ${normalize(location)} "
        if (text.isBlank()) return null
        return lastMatch(text, cities) ?: lastMatch(text, provinces)
    }

    private fun lastMatch(text: String, table: Map<String, Place>): Place? =
        table.mapNotNull { (name, place) ->
            val index = text.lastIndexOf(" $name ")
            if (index >= 0) Triple(index + name.length, name.length, place) else null
        }.maxWithOrNull(compareBy({ it.first }, { it.second }))?.third

    fun locate(location: String): Point? = place(location)?.point

    /** The area ("Metro Manila", "Rizal", "Cavite", …) a location belongs to, or null if unknown. */
    fun areaOf(location: String): String? = place(location)?.area

    /** Great-circle distance in kilometres. */
    fun distanceKm(a: Point, b: Point): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    /** "Near you" for the same city, otherwise a rounded estimate such as "~6 km". */
    fun formatDistance(km: Double): String =
        if (km < 1.5) "Near you" else "~${km.toInt().coerceAtLeast(1)} km"
}

/** Location filter chips shared by the Home and Jobs screens so both behave the same. */
object LocationFilter {
    const val NEAR_ME = "Near Me"
    const val NEAR_ME_RADIUS_KM = 15.0

    /** Chip label to filter value, in display order (after "All Jobs"). */
    val options = listOf(
        NEAR_ME,
        LocationDistance.METRO_MANILA,
        LocationDistance.RIZAL,
        LocationDistance.CAVITE,
        LocationDistance.LAGUNA,
        LocationDistance.BULACAN
    )

    fun matches(filter: String?, location: String, distanceKm: Double?): Boolean = when {
        filter.isNullOrEmpty() -> true
        filter == NEAR_ME -> (distanceKm ?: Double.MAX_VALUE) <= NEAR_ME_RADIUS_KM
        else -> LocationDistance.areaOf(location) == filter || location.contains(filter, ignoreCase = true)
    }

    /** Distance from the applicant's profile location to [jobLocation], or null if either is unknown. */
    fun distanceKm(applicantLocation: String, jobLocation: String): Double? {
        val from = LocationDistance.locate(applicantLocation) ?: return null
        val to = LocationDistance.locate(jobLocation) ?: return null
        return LocationDistance.distanceKm(from, to)
    }
}
