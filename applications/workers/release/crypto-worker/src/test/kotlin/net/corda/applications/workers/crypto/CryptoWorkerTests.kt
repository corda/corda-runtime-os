package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.impl.config.hsmPersistence
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.signingPersistence
import net.corda.crypto.impl.config.softPersistence
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys.SECRETS_PASSPHRASE
import net.corda.schema.configuration.ConfigKeys.SECRETS_SALT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CryptoWorkerTests {

    companion object {
        val validator = mock<ConfigurationValidator>().apply {
                whenever(validate(any(), any<SmartConfig>(), any(), any())).thenAnswer { it.arguments[2] }
            }

        val validatorFactoryMock = mock<ConfigurationValidatorFactory>().apply {
            whenever(createConfigValidator()).thenReturn(validator)
        }
    }
    
    @Test
    fun `Should build default bootstrap config with fallback credentials`() {
        val params = WorkerHelpers.getParams(
            emptyArray(),
            CryptoWorkerParams().also {
                it.defaultParams.secretsParams = mapOf(
                    SECRETS_PASSPHRASE to "passphrase",
                    SECRETS_SALT to "salt"
                )
            }
        )
        val config = buildBoostrapConfig(params, validatorFactoryMock)
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "root-passphrase",
                salt = "root-salt"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(1000, sofPersistence.maximumSize)
        assertEquals("soft-salt", sofPersistence.salt)
        assertEquals("soft-passphrase", sofPersistence.passphrase)
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)

        assertTrue(config.hasPath("dir"))
        assertTrue(config.hasPath("instanceId"))
        assertTrue(config.hasPath("topicPrefix"))
    }

    @Test
    fun `Should build default bootstrap config with provided credentials`() {
        val params = WorkerHelpers.getParams(
            emptyArray(),
            CryptoWorkerParams().also {
                it.defaultParams.secretsParams = mapOf(
                    SECRETS_PASSPHRASE to "passphrase",
                    SECRETS_SALT to "salt"
                )
                it.cryptoParams = mapOf(
                    "rootKey.passphrase" to "p1",
                    "rootKey.salt" to "s1",
                    "softPersistence.passphrase" to "p2",
                    "softPersistence.salt" to "s2"
                )
            }
        )
        val config = buildBoostrapConfig(params, validatorFactoryMock)
        val cryptoConfig = config.getConfig(BOOT_CRYPTO)
        val encryptorFromConfig = cryptoConfig.rootEncryptor()
        val testEncryptor = AesEncryptor(
            AesKey.derive(
                passphrase = "p1",
                salt = "s1"
            )
        )
        assertEquals(testEncryptor, encryptorFromConfig)
        val sofPersistence = cryptoConfig.softPersistence()
        assertEquals(240, sofPersistence.expireAfterAccessMins)
        assertEquals(1000, sofPersistence.maximumSize)
        assertEquals("s2", sofPersistence.salt)
        assertEquals("p2", sofPersistence.passphrase)
        assertEquals(0, sofPersistence.maxAttempts)
        assertEquals(20000, sofPersistence.attemptTimeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.keysExpireAfterAccessMins)
        assertEquals(20, signingPersistence.keyNumberLimit)
        assertEquals(120, signingPersistence.vnodesExpireAfterAccessMins)
        assertEquals(100, signingPersistence.vnodeNumberLimit)
        assertEquals(15, signingPersistence.connectionsExpireAfterAccessMins)
        assertEquals(2, signingPersistence.connectionNumberLimit)
        val hsmPersistence = cryptoConfig.hsmPersistence()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)

        assertTrue(config.hasPath("dir"))
        assertTrue(config.hasPath("instanceId"))
        assertTrue(config.hasPath("topicPrefix"))
    }
}