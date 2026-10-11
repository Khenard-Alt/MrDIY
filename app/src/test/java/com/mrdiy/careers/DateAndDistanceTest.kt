package com.mrdiy.careers

import com.mrdiy.careers.data.search.LocationDistance
import com.mrdiy.careers.data.search.LocationFilter
import com.mrdiy.careers.ui.base.DateText
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DateAndDistanceTest {
    private lateinit var zone: TimeZone
    private lateinit var locale: Locale

    @Before fun fixLocaleAndZone() {
        zone = TimeZone.getDefault()
        locale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Manila"))
        Locale.setDefault(Locale.US)
    }

    @After fun restore() {
        TimeZone.setDefault(zone)
        Locale.setDefault(locale)
    }

    @Test fun databaseTimestampsShowInLocalTime() {
        assertEquals("Oct 11, 2026 · 10:45 PM", DateText.dateTime("2026-10-11T14:45:05.508503+00:00"))
        assertEquals("Oct 11, 2026", DateText.date("2026-10-11T14:45:05Z"))
        // An explicit offset is honoured.
        assertEquals("Oct 11, 2026 · 10:45 PM", DateText.dateTime("2026-10-11T22:45:05+08:00"))
    }

    @Test fun unreadableTimestampsAreLeftAsIs() {
        assertEquals("not a date", DateText.dateTime("not a date"))
        assertEquals("", DateText.relative(""))
    }

    @Test fun relativeTimeUsesShortUnits() {
        val now = System.currentTimeMillis()
        fun iso(millisAgo: Long): String {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return format.format(java.util.Date(now - millisAgo))
        }
        assertEquals("Just now", DateText.relative(iso(10_000)))
        assertEquals("5m ago", DateText.relative(iso(5 * 60_000L)))
        assertEquals("3h ago", DateText.relative(iso(3 * 3_600_000L)))
        assertEquals("2d ago", DateText.relative(iso(2 * 86_400_000L)))
    }

    @Test fun statusNamesMatchWhatApplicantsSee() {
        assertEquals("Under review", DateText.statusLabel("Reviewed"))
        assertEquals("Accepted", DateText.statusLabel("Approved"))
        assertEquals("Rejected", DateText.statusLabel("rejected"))
        assertEquals("Pending", DateText.statusLabel("submitted"))
    }

    @Test fun nearestBranchIsFoundFromAnAddress() {
        val angono = "Angono, Rizal"
        val taytay = LocationFilter.distanceKm(angono, "MR.DIY SM City Taytay, Taytay, Rizal")!!
        val pasig = LocationFilter.distanceKm(angono, "MR.DIY Ayala Malls Feliz, Pasig")!!
        val manila = LocationFilter.distanceKm(angono, "MR.DIY SM City Manila, Manila")!!
        assertTrue(taytay < pasig && pasig < manila)
        assertTrue(taytay in 3.0..5.0)
    }

    @Test fun unknownOrBlankLocationsHaveNoDistance() {
        assertNull(LocationFilter.distanceKm("", "MR.DIY SM City Taytay, Taytay, Rizal"))
        assertNull(LocationFilter.distanceKm("Angono, Rizal", "Somewhere unknown"))
    }

    @Test fun nearMeUsesTheRadiusAndAreaFiltersUseTheProvince() {
        assertTrue(LocationFilter.matches(LocationFilter.NEAR_ME, "x", 14.9))
        assertFalse(LocationFilter.matches(LocationFilter.NEAR_ME, "x", 15.1))
        assertFalse(LocationFilter.matches(LocationFilter.NEAR_ME, "x", null))
        assertTrue(LocationFilter.matches(LocationDistance.RIZAL, "MR.DIY SM City Taytay, Taytay, Rizal", 4.0))
        assertFalse(LocationFilter.matches(LocationDistance.CAVITE, "MR.DIY SM City Taytay, Taytay, Rizal", 4.0))
        assertTrue(LocationFilter.matches(null, "anything", null))
    }

    @Test fun distanceTextIsReadable() {
        assertEquals("Near you", LocationDistance.formatDistance(0.8))
        assertEquals("~4 km", LocationDistance.formatDistance(4.1))
    }
}
