package com.mrdiy.careers

import com.mrdiy.careers.data.repository.ResumeValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeValidatorTest {

    private fun valid(text: String) = ResumeValidator.check(text).valid

    // ------------------------------------------------------------ not resumes

    @Test fun aProductManualIsRefused() {
        // The kind of file that was wrongly accepted: a laptop user guide.
        val manual = """
            Congratulations, you and your MacBook Air were made for each other.
            Built-in iSight camera
            Video chat with up to three friends anywhere in the world at the same time.
            www.apple.com/macosx  Mac Help  iSight
            Finder
            Browse the contents of your computer using Cover Flow.
            Time Machine
            Automatically back up your files to an external hard drive.
            iMovie
            Collect all of your video in one library. Create and share movies in minutes.
            iPhoto
            Organize all your photos with Events. Publish them to a Web Gallery with a click.
            Setup instructions, software installation and wireless network troubleshooting are in the user guide.
            Battery and charger specifications. One year limited warranty. Copyright 2008 Apple Inc. All rights reserved.
        """.trimIndent()
        val verdict = ResumeValidator.check(manual)
        assertFalse(verdict.valid)
        assertTrue(verdict.reason.contains("manual", ignoreCase = true))
    }

    @Test fun otherEverydayDocumentsAreRefused() {
        val invoice = """
            INVOICE No. 2026-0412
            Bill to: Santos Hardware Trading, Antipolo City
            Item                Qty   Unit price   Amount
            Paint 4L              10      850.00    8,500.00
            Brush set              5      120.00      600.00
            Total amount due: PHP 9,100.00. Please pay within 30 days. Contact us at sales@trading.ph or 09171234567.
        """.trimIndent()
        assertFalse(valid(invoice))

        val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore " +
            "et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip."
        assertFalse(valid(lorem))

        val recipe = "Adobo Recipe\nIngredients: 1kg Chicken, 1/2 cup Soy Sauce, vinegar, garlic, bay leaves.\n" +
            "Instructions: Marinate the chicken for 30 minutes, then simmer for 20 minutes until tender."
        assertFalse(valid(recipe))
    }

    @Test fun emptyOrNearlyEmptyFilesAreRefused() {
        assertFalse(valid(""))
        assertFalse(valid("   \n  "))
        assertTrue(ResumeValidator.check("Resume").reason.contains("scanned", ignoreCase = true))
    }

    @Test fun veryLongDocumentsAreRefused() {
        val long = (1..3000).joinToString("\n") { "EXPERIENCE 2020 - 2021 skills education line $it with sales@mail.com" }
        val verdict = ResumeValidator.check(long)
        assertFalse(verdict.valid)
        assertTrue(verdict.reason.contains("long document"))
    }

    @Test fun aFewResumeWordsInsideAnArticleAreNotEnough() {
        // Has one heading, years and skills, but no contact details and no real structure.
        val article = "Education\nTeachers say that after many years in the classroom, customer service and teamwork " +
            "matter as much as sales numbers when a store hires new staff members."
        assertFalse(valid(article))
    }

    // ----------------------------------------------------------- real resumes

    @Test fun aTypicalOnePageResumeIsAccepted() {
        val resume = """
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
        assertTrue(valid(resume))
    }

    @Test fun resumesWithoutAnEmailButWithAMobileNumberAreAccepted() {
        val resume = "Maria Reyes\nContact: +63 917 555 1234\n\nEXPERIENCE\nCashier at a grocery, 2021 - 2024\n\nSKILLS\nCash handling, customer service"
        assertTrue(valid(resume))
    }

    @Test fun resumesInTagalogSectionsAreAccepted() {
        val resume = "Juan dela Cruz\njuan@yahoo.com | 09181234567\n\nKARANASAN SA TRABAHO\nTindero, Pasig Market 2020 - 2023\n\nEDUKASYON\nSenior High School 2016 - 2020\n\nMGA KASANAYAN\nCashier, customer service"
        assertTrue(valid(resume))
    }

    @Test fun anItResumeThatMentionsInstallationAndWirelessIsStillAccepted() {
        val resume = """
            Ana Lim | ana.lim@gmail.com | 09171112222

            EXPERIENCE
            IT Support, 2021 - 2024
            Software installation, wireless network troubleshooting, printer setup.

            EDUCATION
            BS Information Technology 2017 - 2021

            SKILLS
            Python, SQL, Git, Excel
        """.trimIndent()
        assertTrue(valid(resume))
    }

    @Test fun aParagraphStyleResumeWithoutHeadingsButWithDetailsIsAccepted() {
        val resume = "Pedro Santos, 09175550000, pedro@gmail.com. I worked as a cashier and sales associate from 2019 to 2023 " +
            "with strong customer service, cash handling and inventory skills at a retail store in Marikina."
        assertTrue(valid(resume))
    }

    @Test fun theMessageExplainsWhatAResumeNeeds() {
        val verdict = ResumeValidator.check("This document is about nothing in particular and has no structure at all. ".repeat(3))
        assertFalse(verdict.valid)
        assertEquals(true, verdict.reason.contains("Work Experience") && verdict.reason.contains("contact"))
    }
}
