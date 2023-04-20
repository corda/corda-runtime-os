package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import picocli.CommandLine

@Execution(ExecutionMode.SAME_THREAD)
@Isolated("Tests using tapSystemErrNormalized cannot run in parallel.")
class TestInitialConfigPlugin {

    @Test
    fun testSetupScriptMissingCommand() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute()
        }
        assertThat(outText).startsWith("Missing required subcommand")
    }

}