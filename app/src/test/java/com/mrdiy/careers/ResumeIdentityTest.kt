package com.mrdiy.careers

import com.mrdiy.careers.data.repository.ResumeIdentity
import com.mrdiy.careers.data.repository.ResumeIdentity.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeIdentityTest {

    private fun resume(name: String) = "$name\nAngono, Rizal | 09396326936 | someone@gmail.com\n\nEXPERIENCE\nCashier 2021 - 2024\n"

    @Test fun theSameNameMatches() {
        assertEquals(Result.MATCH, ResumeIdentity.check("Joshua Allen P. Balasta", resume("JOSHUA ALLEN P. BALASTA")).result)
    }

    @Test fun firstAndLastNameAreEnoughWhenTheMiddleNameIsLeftOut() {
        assertEquals(Result.MATCH, ResumeIdentity.check("Joshua Allen P. Balasta", resume("Joshua Balasta")).result)
    }

    @Test fun accentsAndCapitalsDoNotMatter() {
        assertEquals(Result.MATCH, ResumeIdentity.check("Jose Pineda", resume("JOSÉ PIÑEDA")).result)
        assertEquals(Result.MATCH, ResumeIdentity.check("khenard vince albaytar", resume("Khenard Vince Albaytar")).result)
    }

    @Test fun anotherPersonsResumeIsRefused() {
        val verdict = ResumeIdentity.check("Khenard Vince Albaytar", resume("Maria Santos"))
        assertEquals(Result.MISMATCH, verdict.result)
        assertEquals("Maria Santos", verdict.resumeName)
        assertTrue(verdict.message.contains("Maria Santos") && verdict.message.contains("Khenard Vince Albaytar"))
    }

    @Test fun sharingOnlyOneOfSeveralNameWordsIsNotEnough() {
        // Same first name, different person.
        assertEquals(Result.MISMATCH, ResumeIdentity.check("Joshua Allen Balasta", resume("Joshua Reyes")).result)
    }

    @Test fun aResumeWithNoNameAtAllIsAcceptedWithAReminder() {
        val noName = "Contact: 09396326936 | someone@gmail.com\n\nEXPERIENCE\nCashier 2021 - 2024 at a store in Taytay\n\nSKILLS\nCash handling"
        val verdict = ResumeIdentity.check("Joshua Allen Balasta", noName)
        assertEquals(Result.UNKNOWN, verdict.result)
        assertTrue(verdict.message.contains("make sure it is your own resume"))
    }

    @Test fun headingsAreNotMistakenForTheNameOnTheResume() {
        val text = "Curriculum Vitae\nCashier, 2021 - 2024, Taytay Rizal\nSKILLS\nCash handling"
        val verdict = ResumeIdentity.check("Joshua Allen Balasta", text)
        assertEquals(Result.UNKNOWN, verdict.result)
        assertNull(verdict.resumeName)
    }

    @Test fun anAccountWithoutAUsableNameIsNotBlocked() {
        assertEquals(Result.UNKNOWN, ResumeIdentity.check("", resume("Maria Santos")).result)
        assertEquals(Result.UNKNOWN, ResumeIdentity.check("J A", resume("Maria Santos")).result)
    }
}
