package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPluginUser {

    @Test
    fun testSetupScript() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-user-config", "-u", "user1", "-p", "password")
        }

        // can only compare the first bit as timestamp and salted hash will change.
        assertThat(outText).startsWith(
            "insert into rbac_user (enabled, full_name, hashed_password, id, login_name, " +
                "salt_value, update_ts, version) values (true, 'Default Admin',"
        ).contains(
            "'user1'"
        )
    }

    @Test
    fun testSetupScriptMissingUser() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-user-config")
        }
        assertThat(outText).startsWith("A user id must be specified.")
    }

    @Test
    fun testSetupScriptNoPassword() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()

        val outText = tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-user-config", "-u", "user1")
        }

        assertThat(outText).startsWith(
            "insert into rbac_user (enabled, full_name, id, login_name, " +
                "update_ts, version) values (true, 'Default Admin',"
        ).contains(
            "'user1'"
        )
    }
}
