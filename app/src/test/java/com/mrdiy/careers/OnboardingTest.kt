package com.mrdiy.careers

import com.mrdiy.careers.data.auth.AccountRules
import com.mrdiy.careers.data.search.LocationDistance
import org.junit.Assert.*
import org.junit.Test

class OnboardingTest {

    @Test fun mobileNumbersAreAcceptedInCommonWritings() {
        assertEquals("09171234567", AccountRules.phoneNumber("09171234567"))
        assertEquals("09171234567", AccountRules.phoneNumber("+63 917 123 4567"))
        assertEquals("09171234567", AccountRules.phoneNumber("639171234567"))
        assertEquals("09171234567", AccountRules.phoneNumber("0917-123-4567"))
    }

    @Test fun invalidMobileNumbersAreRejected() {
        assertNull(AccountRules.phoneNumber(""))
        assertNull(AccountRules.phoneNumber("12345"))
        assertNull(AccountRules.phoneNumber("0817123456"))      // not a mobile prefix
        assertNull(AccountRules.phoneNumber("091712345678"))    // too long
        assertNull(AccountRules.phoneNumber("abc"))
    }

    @Test fun everyLocationSuggestionIsRecognisedByTheDistanceCalculation() {
        val suggestions = LocationDistance.suggestions()
        assertTrue(suggestions.size > 40)
        assertEquals(suggestions.size, suggestions.toSet().size)
        suggestions.forEach { place ->
            assertNotNull("$place is not recognised", LocationDistance.locate(place))
        }
        assertTrue("Taytay, Rizal" in suggestions)
        assertTrue("Las Piñas, Metro Manila" in suggestions)
        assertTrue("Quezon City, Metro Manila" in suggestions)
    }

    @Test fun suggestionsAreAssignedToTheRightArea() {
        assertEquals("Rizal", LocationDistance.areaOf("Angono, Rizal"))
        assertEquals("Metro Manila", LocationDistance.areaOf("Pasig, Metro Manila"))
        assertEquals("Cavite", LocationDistance.areaOf("Bacoor, Cavite"))
    }
}
