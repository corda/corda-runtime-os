package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.config.impl.retrying
import net.corda.crypto.config.impl.signingService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPluginCrypto {
    @Test
    fun `Should output missing options`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        var outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-crypto-config")
        }
        println(outText)
        assertThat(outText).contains("'passphrase' must be set for CORDA type secrets.")
        assertThat(outText).contains("-l, --location=<location>")
        assertThat(outText).contains("location to write the sql output to.")
        assertThat(outText).contains("-p, --passphrase=<passphrase>")
        assertThat(outText).contains("-s, --salt=<salt>")
        assertThat(outText).contains("Salt for the encrypting secrets service.")
        assertThat(outText).contains("-wp, --wrapping-passphrase=<softHsmRootPassphrase>")
        assertThat(outText).contains("Passphrase for a SOFT HSM unmanaged root wrapping key.")
        assertThat(outText).contains("-ws, --wrapping-salt=<softHsmRootSalt>")
        assertThat(outText).contains("Salt for deriving a SOFT HSM root unmanaged wrapping key")
    }


    @Test
    fun `Should output missing options when targeting Hashicorp Vault`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        var outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-crypto-config", "-t", "VAULT")
        }
        assertThat(outText).contains("'vaultPath' must be set for VAULT type secrets.")
    }

    @Suppress("MaxLineLength")
    private val expectedPrefix =
        "insert into config (config, is_deleted, schema_version_major, schema_version_minor, section, update_actor, update_ts, version) values ('"

    @Test
    fun `Should be able to create default initial crypto configuration with defined wrapping key`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-p", "passphrase",
                "-s", "salt",
                "-wp", "master-passphrase",
                "-ws", "master-salt"
            )
        }
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertThat(json).containsSubsequence("\"passphrase\":{\"configSecret\":{\"encryptedSecret\":")
        assertGeneratedJson(json) { config: ConfigList, factory: SmartConfigFactory ->
            val key1 = factory.create((config[0] as ConfigObject).toConfig())
            assertEquals("master-salt", key1.getString("salt"))
            assertEquals("master-passphrase", key1.getString("passphrase"))
        }
    }

    @Test
    fun `Should be able to create default initial crypto configuration with random wrapping key`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-p", "passphrase",
                "-s", "salt"
            )
        }
        println(outText)
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertGeneratedJson(json) { it: ConfigList, _: SmartConfigFactory ->
            val key1 = it[0] as ConfigObject
            assertThat(key1.getValue("salt").render()).contains("configSecret")
            assertThat(key1.getValue("salt").render()).contains("encryptedSecret")
            assertThat(key1.getValue("passphrase").render()).contains("configSecret")
            assertThat(key1.getValue("passphrase").render()).contains("encryptedSecret")
        }
    }

    @Test
    fun `Should be able to create vault initial crypto configuration with single random wrapping key`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-t", "VAULT",
                "--vault-path", "cryptosecrets",
                "--key-salt", "salt",
                "--key-passphrase", "passphrase",
                "--number-of-unmanaged-root-wrapping-keys", "1"
            )
        }
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertGeneratedJson(json) { it: ConfigList, _: SmartConfigFactory ->
            val key1 = it[0] as ConfigObject
            assertThat(key1.getValue("salt").render()).doesNotContain("encryptedSecret")
            assertThat(key1.getValue("passphrase").render()).doesNotContain("encryptedSecret")
            assertThat(key1.getValue("salt").render()).contains("vaultKey")
            assertThat(key1.getValue("salt").render()).contains("vaultPath")
            assertThat(key1.getValue("passphrase").render()).contains("vaultKey")
            assertThat(key1.getValue("passphrase").render()).contains("vaultPath")
        }
    }


    @Test
    fun `Should be able to create vault initial crypto configuration with two random wrapping keys`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemOutNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-t", "VAULT",
                "--vault-path", "cryptosecrets",
                "--key-salt", "salt",
                "--key-passphrase", "passphrase",
                "--key-salt", "salt2",
                "--key-passphrase", "passphrase2",
            )
        }
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertGeneratedJson(json) { it: ConfigList, _: SmartConfigFactory ->
            assertThat(it.size).isEqualTo(2)
            val key1 = it[0] as ConfigObject
            val key2 = it[1] as ConfigObject
            assertThat(key1.getValue("salt").render()).doesNotContain("encryptedSecret")
            assertThat(key1.getValue("passphrase").render()).doesNotContain("encryptedSecret")
            assertThat(key1.getValue("salt").render()).contains("vaultKey")
            assertThat(key1.getValue("salt").render()).contains("vaultPath")
            assertThat(key1.getValue("passphrase").render()).contains("vaultKey")
            assertThat(key1.getValue("passphrase").render()).contains("vaultPath")
            assertThat(key1.getValue("passphrase")).isNotEqualTo((key2.getValue("passphrase")))
        }
    }
    @Test
    fun `Should fail to create vault initial crypto configuration with insufficient keys specified`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute(
                "create-crypto-config",
                "-t", "VAULT",
                "--vault-path", "cryptosecrets",
                "--key-salt", "salt",
                "--key-passphrase", "passphrase",
            )
        }
        assertThat(outText).contains("Not enough vault wrapping key salt keys passed in")
    }

    private fun assertGeneratedJson(json: String, wrappingKeyAssert: (ConfigList, SmartConfigFactory) -> Unit) {
        val smartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString(
                """
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
        )
        val config = smartConfigFactory.create(ConfigFactory.parseString(json))
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val softWorker = config.hsm()
        assertEquals(20000L, softWorker.retrying.attemptTimeoutMills)
        assertEquals(3, softWorker.retrying.maxAttempts)
        wrappingKeyAssert(softWorker.wrappingKeys, smartConfigFactory)
        val opsBusProcessor = config.retrying()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.retrying()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.retrying()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }
}
