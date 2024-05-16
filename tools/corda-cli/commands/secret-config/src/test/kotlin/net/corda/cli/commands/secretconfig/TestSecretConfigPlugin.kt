package net.corda.cli.commands.secretconfig

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
    fun `create CORDA type secret Config`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = SecretConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("password", "-p", "not so secure", "-s", "not so secret", "create")
        }

        println(outText)
        assertThat(outText.startsWith("{\"configSecret\":{\"encryptedSecret\"")).isTrue()

    }

    @Test
    fun `create VAULT type secret Config`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = SecretConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("passwordKey", "-v", "myPath", "-t", "VAULT", "create")
        }

        println(outText)
        val resultMatch = """(?s)\{"configSecret":\{"vaultKey":".*","vaultPath":"myPath"}}.*""".toRegex()
        assertThat(resultMatch.matches(outText)).isTrue()
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
        args.hashCode()
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = SecretConfigPlugin.PluginEntryPoint()

        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(*args)
        }

        println("Output:\n$outText")
        println("Expected:\n$expectedError")
        assertThat(outText).contains(expectedError).withFailMessage(caseName)
    }

    companion object {

        @JvmStatic
        private fun badCommandLineInputs(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    "passphrase missing create, CORDA type",
                    arrayOf("value", "-s", "salt", "create"),
                    "'passphrase' must be set for CORDA type secrets"
                ), Arguments.of(
                    "salt missing create, CORDA type",
                    arrayOf("value", "-p", "passphrase", "create"),
                    "'salt' must be set for CORDA type secrets"
                ), Arguments.of(
                    "passphrase missing decrypt, CORDA type",
                    arrayOf("value", "-s", "salt", "decrypt"),
                    "'passphrase' must be set for CORDA type secrets"
                ), Arguments.of(
                    "salt missing decrypt, CORDA type",
                    arrayOf("value", "-p", "passphrase", "decrypt"),
                    "'salt' must be set for CORDA type secrets"
                ), Arguments.of(
                    "command missing, CORDA type",
                    arrayOf("value", "-p", "passphrase", "-s", "salt"),
                    "Missing required subcommand"
                ), Arguments.of(
                    "value missing, CORDA type",
                    arrayOf("-p", "passphrase", "-s", "salt", "create"),
                    "Missing required parameter: '<value>'"
                ), Arguments.of(
                    "vaultPath missing create, VAULT type",
                    arrayOf("value", "-t", "VAULT", "create"),
                    "'vault-path' must be set for VAULT type secrets"
                ), Arguments.of(
                    "command missing, VAULT type",
                    arrayOf("value", "-t", "VAULT", "-v", "vaultPath"),
                    "Missing required subcommand"
                ), Arguments.of(
                    "value missing, VAULT type",
                    arrayOf("-t", "VAULT", "-v", "vaultPath", "create"),
                    "Missing required parameter: '<value>'"
                )
            )
        }
    }
}
