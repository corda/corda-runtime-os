package net.corda.sdk.preinstall

import net.corda.sdk.preinstall.checker.LimitsChecker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LimitsCheckerTest {

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

    @Test
    fun `test parseMemoryString with valid inputs`() {
        val limitsChecker = LimitsChecker("")
        assertEquals(1000.0, limitsChecker.parseMemoryString("1K"))
        assertEquals(1000000.0, limitsChecker.parseMemoryString("1M"))
        assertEquals(1000000000.0, limitsChecker.parseMemoryString("1G"))
        assertEquals(1000000000000.0, limitsChecker.parseMemoryString("1T"))
        assertEquals(1000000000000000.0, limitsChecker.parseMemoryString("1P"))
        assertEquals(1000000000000000000.0, limitsChecker.parseMemoryString("1E"))
        assertEquals(1024.0, limitsChecker.parseMemoryString("1Ki"))
        assertEquals(1048576.0, limitsChecker.parseMemoryString("1Mi"))
        assertEquals(1073741824.0, limitsChecker.parseMemoryString("1Gi"))
        assertEquals(1099511627776.0, limitsChecker.parseMemoryString("1Ti"))
        assertEquals(1125899906842624.0, limitsChecker.parseMemoryString("1Pi"))
        assertEquals(1.15292150460684698E18, limitsChecker.parseMemoryString("1Ei"))
    }

    @Test
    fun `test parseMemoryString with invalid inputs`() {
        val limitsChecker = LimitsChecker("")
        assertThrows(IllegalArgumentException::class.java) { limitsChecker.parseMemoryString("KiB") }
        assertThrows(IllegalArgumentException::class.java) { limitsChecker.parseMemoryString("Mb") }
        assertThrows(IllegalArgumentException::class.java) { limitsChecker.parseMemoryString("GB") }
        assertThrows(IllegalArgumentException::class.java) { limitsChecker.parseMemoryString("123") }
        assertThrows(IllegalArgumentException::class.java) { limitsChecker.parseMemoryString("1000i") }
    }
}
