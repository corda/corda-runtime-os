package net.corda.cli.application

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.PrintWriter
import java.io.StringWriter

class BootTest {

    @Test
    fun testNoArgs() {

        val cmd = CommandLine(App())
        val sw = StringWriter()
        cmd.err = PrintWriter(sw)

        cmd.execute("")

        assertTrue(sw.toString().contains("Usage: corda [COMMAND]"))
    }
}