package com.mrdiy.careers

import com.mrdiy.careers.data.auth.AccountRules
import com.mrdiy.careers.data.repository.ResumeRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeValidationTest {

    @Test
    fun universalEmailValidationAcceptsSchoolAndPersonalEmails() {
        // School & Academic emails (.edu, .edu.ph, subdomains)
        assertTrue(AccountRules.email("student@dlsu.edu.ph"))
        assertTrue(AccountRules.email("juan.dela.cruz@ust.edu.ph"))
        assertTrue(AccountRules.email("applicant@up.edu.ph"))
        assertTrue(AccountRules.email("student@sub.univ.edu"))

        // Personal & Corporate emails
        assertTrue(AccountRules.email("candidate@gmail.com"))
        assertTrue(AccountRules.email("user.name+tag@yahoo.com"))
        assertTrue(AccountRules.email("john.doe@company.com.ph"))
        assertTrue(AccountRules.email("STUDENT@DLSU.EDU.PH"))
        assertTrue(AccountRules.email("Juan.DelaCruz@UST.edu.ph"))

        // Invalid email formats
        assertFalse(AccountRules.email(""))
        assertFalse(AccountRules.email("invalid.email"))
        assertFalse(AccountRules.email("user@"))
        assertFalse(AccountRules.email("@domain.com"))
        assertFalse(AccountRules.email("user@domain."))
    }

    @Test
    fun validatesValidResumeContentSuccessfully() {
        val validResume = """
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

        assertTrue(ResumeRepository.validateResumeContent(validResume))

        val filipinoResume = """
            Juan dela Cruz
            Email: juan@yahoo.com | Telepono: 09181234567
            
            KARANASAN SA TRABAHO
            Sales Associate | Retail Store Pasig | 2020 - 2023
            - Taga-alaga ng mga paninda, cash handling, at customer service.
            
            EDUKASYON
            High School Graduate | Pasig High School | 2016 - 2020
            
            MGA KASANAYAN
            Cashier, POS, Customer Service, Inventory
        """.trimIndent()

        assertTrue(ResumeRepository.validateResumeContent(filipinoResume))
    }

    @Test
    fun rejectsNonResumeFilesAndRandomDocuments() {
        // Food Recipe
        val recipeText = """
            Adobo Recipe
            Ingredients: 1kg Chicken, 1/2 cup Soy Sauce, 1/2 cup Vinegar, Garlic, Bay Leaves, Pepper.
            Instructions:
            1. Marinate chicken in soy sauce and garlic for 30 minutes.
            2. Heat pan and cook chicken until brown.
            3. Add marinade, vinegar, and bay leaves. Simmer for 20 minutes until tender.
            4. Serve warm with rice.
        """.trimIndent()

        assertFalse(ResumeRepository.validateResumeContent(recipeText))

        // Short random note
        val randomText = "Meeting notes for tomorrow at 10 AM. Don't forget to buy coffee."
        assertFalse(ResumeRepository.validateResumeContent(randomText))

        // Empty document
        assertFalse(ResumeRepository.validateResumeContent(""))
    }
}
