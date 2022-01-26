package net.corda.cli.application

import com.github.stefanbirkner.systemlambda.SystemLambda
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine

class BootTest {

    @Test
    fun testNoArgs() {

        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                App()
            ).execute("")
        }

        assertTrue(outText.contains("Usage: corda [COMMAND]"))
    }
}