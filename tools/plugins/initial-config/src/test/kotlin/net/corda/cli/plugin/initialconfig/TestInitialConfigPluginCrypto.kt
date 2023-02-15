package net.corda.cli.plugin.initialconfig

import com.github.stefanbirkner.systemlambda.SystemLambda
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.PrivateKeyPolicy
import net.corda.crypto.config.impl.cryptoConnectionFactory
import net.corda.crypto.config.impl.flowBusProcessor
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.config.impl.hsmMap
import net.corda.crypto.config.impl.hsmRegistrationBusProcessor
import net.corda.crypto.config.impl.hsmService
import net.corda.crypto.config.impl.opsBusProcessor
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.core.CryptoConsts
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.secret.EncryptionSecretsServiceFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TestInitialConfigPluginCrypto {
    @Test
    fun `Should output missing options`() {
        val colorScheme = CommandLine.Help.ColorScheme.Builder().ansi(CommandLine.Help.Ansi.OFF).build()
        val app = InitialConfigPlugin.PluginEntryPoint()
        val outText = SystemLambda.tapSystemErrNormalized {
            CommandLine(
                app
            ).setColorScheme(colorScheme).execute("create-crypto-config")
        }
        println(outText)
        assertThat(outText).contains("Missing required options:")
        assertThat(outText).contains("-l, --location=<location>")
        assertThat(outText).contains("location to write the sql output to.")
        assertThat(outText).contains("-p, --passphrase=<passphrase>")
        assertThat(outText).contains("Passphrase for the encrypting secrets service.")
        assertThat(outText).contains("-s, --salt=<salt>")
        assertThat(outText).contains("Salt for the encrypting secrets service.")
        assertThat(outText).contains("-wp, --wrapping-passphrase=<softHsmRootPassphrase>")
        assertThat(outText).contains("Passphrase for the SOFT HSM root wrapping key.")
        assertThat(outText).contains("-ws, --wrapping-salt=<softHsmRootSalt>")
        assertThat(outText).contains("Salt for the SOFT HSM root wrapping key.")
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
        println(outText)
        assertThat(outText).startsWith(expectedPrefix)
        val outJsonEnd = outText.indexOf("}}}',", expectedPrefix.length)
        val json = outText.substring(expectedPrefix.length until (outJsonEnd + 3))
        assertThat(json).containsSubsequence("\"passphrase\":{\"configSecret\":{\"encryptedSecret\":")
        assertGeneratedJson(json) {
            assertEquals("master-salt", it.getString("wrappingKeyMap.salt"))
            assertEquals("master-passphrase", it.getString("wrappingKeyMap.passphrase"))
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
        assertGeneratedJson(json) {
            assertThat(it.getValue("wrappingKeyMap.salt").render()).contains("configSecret")
            assertThat(it.getValue("wrappingKeyMap.salt").render()).contains("encryptedSecret")
            assertThat(it.getValue("wrappingKeyMap.passphrase").render()).contains("configSecret")
            assertThat(it.getValue("wrappingKeyMap.passphrase").render()).contains("encryptedSecret")
        }
    }

    private fun assertGeneratedJson(json: String, wrappingKeyAssert: (Config) -> Unit) {
        val smartConfigFactory = SmartConfigFactory.createWith(
            ConfigFactory.parseString("""
            ${EncryptionSecretsServiceFactory.SECRET_PASSPHRASE_KEY}=passphrase
            ${EncryptionSecretsServiceFactory.SECRET_SALT_KEY}=salt
        """.trimIndent()
            ),
            listOf(EncryptionSecretsServiceFactory())
        )
        val config = smartConfigFactory.create(ConfigFactory.parseString(json))
        val connectionFactory = config.cryptoConnectionFactory()
        assertEquals(5, connectionFactory.expireAfterAccessMins)
        assertEquals(3, connectionFactory.maximumSize)
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(10000, signingService.cache.maximumSize)
        val hsmService = config.hsmService()
        assertEquals(3, hsmService.downstreamMaxAttempts)
        assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm(CryptoConsts.SOFT_HSM_ID)
        assertEquals("", softWorker.workerTopicSuffix)
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertEquals(CryptoConsts.SOFT_HSM_SERVICE_NAME, softWorker.hsm.name)
        assertThat(softWorker.hsm.categories).hasSize(1)
        assertEquals("*", softWorker.hsm.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.hsm.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.hsm.masterKeyPolicy)
        assertNull(softWorker.hsm.masterKeyAlias)
        assertEquals(-1, softWorker.hsm.capacity)
        assertThat(softWorker.hsm.supportedSchemes).hasSize(8)
        assertThat(softWorker.hsm.supportedSchemes).contains(
            "CORDA.RSA",
            "CORDA.ECDSA.SECP256R1",
            "CORDA.ECDSA.SECP256K1",
            "CORDA.EDDSA.ED25519",
            "CORDA.X25519",
            "CORDA.SM2",
            "CORDA.GOST3410.GOST3411",
            "CORDA.SPHINCS-256"
        )
        val hsmCfg = softWorker.hsm.cfg
        wrappingKeyAssert(hsmCfg)
        assertEquals("CACHING", hsmCfg.getString("keyMap.name"))
        assertEquals(60, hsmCfg.getLong("keyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("keyMap.cache.maximumSize"))
        assertEquals("CACHING", hsmCfg.getString("wrappingKeyMap.name"))
        assertEquals(60, hsmCfg.getLong("wrappingKeyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("wrappingKeyMap.cache.maximumSize"))
        assertEquals("DEFAULT", hsmCfg.getString("wrapping.name"))
        val opsBusProcessor = config.opsBusProcessor()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.flowBusProcessor()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.hsmRegistrationBusProcessor()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }
}
