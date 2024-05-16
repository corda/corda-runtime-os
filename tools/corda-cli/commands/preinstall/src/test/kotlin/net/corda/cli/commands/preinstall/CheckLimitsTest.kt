package net.corda.cli.commands.preinstall

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import picocli.CommandLine


class CheckLimitsTest {

    @Test
    fun testLimitsFileParsing() {
        val path = "./src/test/resources/LimitsTestUnderLimits.yaml"
        val limits = CheckLimits()
        val ret = CommandLine(limits).execute(path)
        assertEquals(0, ret)
    }
}
