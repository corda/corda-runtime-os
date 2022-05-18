package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.crypto.core.aes.AesEncryptor
import net.corda.crypto.core.aes.AesKey
import net.corda.crypto.impl.config.hsmPersistence
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.signingPersistence
import net.corda.crypto.impl.config.softPersistence
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.SECRETS_PASSPHRASE
import net.corda.schema.configuration.ConfigKeys.SECRETS_SALT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CryptoWorkerTests {
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
        val config = buildBoostrapConfig(params)
        val cryptoConfig = config.getConfig(CRYPTO_CONFIG)
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
        assertEquals(0, sofPersistence.retries)
        assertEquals(20000, sofPersistence.timeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.expireAfterAccessMins)
        assertEquals(20, signingPersistence.maximumSize)
        val hsmPersistence = cryptoConfig.hsmPersistence()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)

        assertTrue(config.hasPath("dir"))
        assertTrue(config.hasPath("instance"))
        assertTrue(config.hasPath("topic"))
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
        val config = buildBoostrapConfig(params)
        val cryptoConfig = config.getConfig(CRYPTO_CONFIG)
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
        assertEquals(0, sofPersistence.retries)
        assertEquals(20000, sofPersistence.timeoutMills)
        val signingPersistence = cryptoConfig.signingPersistence()
        assertEquals(90, signingPersistence.expireAfterAccessMins)
        assertEquals(20, signingPersistence.maximumSize)
        val hsmPersistence = cryptoConfig.hsmPersistence()
        assertEquals(240, hsmPersistence.expireAfterAccessMins)
        assertEquals(1000, hsmPersistence.maximumSize)

        assertTrue(config.hasPath("dir"))
        assertTrue(config.hasPath("instance"))
        assertTrue(config.hasPath("topic"))
    }
}