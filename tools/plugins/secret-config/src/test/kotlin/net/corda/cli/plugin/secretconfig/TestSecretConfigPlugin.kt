package net.corda.cli.plugin.secretconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import picocli.CommandLine
import java.util.stream.Stream

class TestSecretConfigPlugin {
    @Test
    fun testEncryption() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = SecretConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("password", "-p", "not so secure", "-s", "not so secret", "create")
        }

        println(outText)
        assertThat(outText.startsWith("{\"configSecret\":{\"encryptedSecret\")"))
    }

    @Test
    fun testDecryption() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = SecretConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "clCX2yEq3d/F0TvQsnqCp1n5ppEMKp+6WLnCJbufaJIxi4uI",
                "-p",
                "not so secure",
                "-s",
                "not so secret",
                "decrypt"
            )
        }

        Assertions.assertEquals("password\n", outText)
    }

    @Suppress("SpreadOperator")
    @ParameterizedTest
    @MethodSource("badCommandLineInputs")
    fun testBadCommandline(caseName: String, args: Array<String>, expectedError: String) {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = SecretConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                *args
            )
        }

        println("Output:\n$outText")
        println("Expected:\n$expectedError")
        assertThat(outText).isEqualTo(expectedError).withFailMessage(caseName)
    }

    companion object {

        private val helpText =
            "Usage: secret-config [-p=<passphrase>] [-s=<salt>] <value> [COMMAND]\n" +
                "Handle secret config values given the value, a passphrase and a salt\n" +
                "      <value>         The value to secure for configuration\n" +
                "  -p, --passphrase=<passphrase>\n" +
                "                      Passphrase for the encrypting secrets service\n" +
                "  -s, --salt=<salt>   Salt for the encrypting secrets service\n" +
                "Commands:\n" +
                "  create   Create a secret config value given a salt and a passphrase\n" +
                "  decrypt  Decrypt a secret value given salt and passphrase (takes the actual\n" +
                "             value, not the config)\n"

        @JvmStatic
        private fun badCommandLineInputs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "passphrase missing create", arrayOf("value", "-s", "salt", "create"),
                    "A passphrase must be provided\n$helpText"
                ),
                Arguments.of(
                    "passphrase missing decrypt", arrayOf("value", "-s", "salt", "decrypt"),
                    "A passphrase must be provided\n$helpText"
                ),
                Arguments.of(
                    "salt missing decrypt", arrayOf("value", "-p", "passphrase", "decrypt"),
                    "A salt must be provided\n$helpText"
                ),
                Arguments.of(
                    "salt missing create", arrayOf("value", "-p", "passphrase", "create"),
                    "A salt must be provided\n$helpText"
                ),
                Arguments.of(
                    "command missing", arrayOf("value", "-p", "passphrase", "-s", "salt"),
                    "Missing required subcommand\n$helpText"
                ),
                Arguments.of(
                    "no arguments", arrayOf(""),
                    "Missing required subcommand\n$helpText"
                ),
                Arguments.of(
                    "value missing", arrayOf("-p", "passphrase", "-s", "salt", "create"),
                    "Missing required parameter: '<value>'\n$helpText"
                )
            )
        }
    }
}
