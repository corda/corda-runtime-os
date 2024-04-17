package net.corda.sdk.preinstall

import net.corda.sdk.preinstall.checker.LimitsChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckLimitsTest {

    @Test
    fun testLimitsFileParsing() {
        val path = "./src/test/resources/preinstall/LimitsTestUnderLimits.yaml"
        val limitsChecker = LimitsChecker(path)
        val ret = limitsChecker.check()

        assertTrue(limitsChecker.report.toString().contains("Parse resource properties from YAML: PASSED"))
        assertTrue(limitsChecker.report.toString().contains("Parse resource properties from YAML: PASSED"))
        assertEquals(0, ret)
    }

    @Test
    fun testParserUnderLimits() {
        val path = "./src/test/resources/preinstall/LimitsTestUnderLimits.yaml"
        val limitsChecker = LimitsChecker(path)
        val ret = limitsChecker.check()

        assertTrue(limitsChecker.report.toString().contains("bootstrap cpu requests do not exceed limits: PASSED"))
        assertTrue(limitsChecker.report.toString().contains("bootstrap memory requests do not exceed limits: PASSED"))
        assertEquals(0, ret)
    }

    @Test
    fun testParserOverLimits() {
        val path = "./src/test/resources/preinstall/LimitsTestOverLimits.yaml"
        val limitsChecker = LimitsChecker(path)
        val ret = limitsChecker.check()

        assertTrue(limitsChecker.report.toString().contains("bootstrap cpu requests do not exceed limits: FAILED"))
        assertTrue(limitsChecker.report.toString().contains("bootstrap memory requests do not exceed limits: FAILED"))
        assertEquals(1, ret)
    }

    @Test
    fun testParserBadValues() {
        val path = "./src/test/resources/preinstall/LimitsTestBadValues.yaml"
        val limitsChecker = LimitsChecker(path)
        val ret = limitsChecker.check()

        println(limitsChecker.report.toString())
        assertTrue(limitsChecker.report.toString().contains("Parse \"bootstrap\" cpu resource strings: FAILED"))
        assertTrue(limitsChecker.report.toString().contains("Parse \"bootstrap\" memory resource strings: FAILED"))
        assertEquals(1, ret)
    }

    @Test
    fun testParserOverrideValues() {
        val path = "./src/test/resources/preinstall/LimitsTestOverrideValues.yaml"
        val limitsChecker = LimitsChecker(path)
        val ret = limitsChecker.check()

        assertTrue(limitsChecker.report.toString().contains("Parse \"bootstrap\" cpu resource strings: PASSED"))
        assertTrue(limitsChecker.report.toString().contains("Parse \"bootstrap\" memory resource strings: PASSED"))
        assertEquals(0, ret)
    }
}
