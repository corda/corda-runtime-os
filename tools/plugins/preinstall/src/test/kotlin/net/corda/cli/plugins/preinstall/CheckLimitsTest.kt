package net.corda.cli.plugins.preinstall

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine


class CheckLimitsTest {

    @Test
    fun testLimitsFileParsing() {
        val path = "./src/test/resources/LimitsTestUnderLimits.yaml"
        val limits = CheckLimits()
        val ret = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("Parse resource properties from YAML: PASSED"))
        assertTrue(limits.report.toString().contains("Parse resource properties from YAML: PASSED"))
        assertEquals(0, ret)
    }

    @Test
    fun testParserUnderLimits() {
        val path = "./src/test/resources/LimitsTestUnderLimits.yaml"
        val limits = CheckLimits()
        val ret = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("bootstrap cpu requests do not exceed limits: PASSED"))
        assertTrue(limits.report.toString().contains("bootstrap memory requests do not exceed limits: PASSED"))
        assertEquals(0, ret)
    }

    @Test
    fun testParserOverLimits() {
        val path = "./src/test/resources/LimitsTestOverLimits.yaml"
        val limits = CheckLimits()
        val ret = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("bootstrap cpu requests do not exceed limits: FAILED"))
        assertTrue(limits.report.toString().contains("bootstrap memory requests do not exceed limits: FAILED"))
        assertEquals(1, ret)
    }

    @Test
    fun testParserBadValues() {
        val path = "./src/test/resources/LimitsTestBadValues.yaml"
        val limits = CheckLimits()
        val ret = CommandLine(limits).execute(path)

        println(limits.report.toString())
        assertTrue(limits.report.toString().contains("Parse \"bootstrap\" cpu resource strings: FAILED"))
        assertTrue(limits.report.toString().contains("Parse \"bootstrap\" memory resource strings: FAILED"))
        assertEquals(1, ret)
    }

    @Test
    fun testParserOverrideValues() {
        val path = "./src/test/resources/LimitsTestOverrideValues.yaml"
        val limits = CheckLimits()
        val ret = CommandLine(limits).execute(path)

        assertTrue(limits.report.toString().contains("Parse \"bootstrap\" cpu resource strings: PASSED"))
        assertTrue(limits.report.toString().contains("Parse \"bootstrap\" memory resource strings: PASSED"))
        assertEquals(0, ret)
    }

}