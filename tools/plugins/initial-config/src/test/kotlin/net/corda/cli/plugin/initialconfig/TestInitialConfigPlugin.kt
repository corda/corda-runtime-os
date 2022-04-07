package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPlugin {

    @Test
    fun testSetupScriptMissingCommand() {
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).execute()
        }
        assertThat(outText).startsWith("Missing required subcommand")
    }

}