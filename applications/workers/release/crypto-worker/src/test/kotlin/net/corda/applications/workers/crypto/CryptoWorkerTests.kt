package net.corda.applications.workers.crypto

import net.corda.applications.workers.workercommon.WorkerHelpers
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.PrivateKeyPolicy
import net.corda.crypto.config.impl.cryptoConnectionFactory
import net.corda.crypto.config.impl.flowBusProcessor
import net.corda.crypto.config.impl.hsm
import net.corda.crypto.config.impl.hsmConfigBusProcessor
import net.corda.crypto.config.impl.hsmId
import net.corda.crypto.config.impl.hsmMap
import net.corda.crypto.config.impl.hsmRegistrationBusProcessor
import net.corda.crypto.config.impl.hsmService
import net.corda.crypto.config.impl.opsBusProcessor
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.config.impl.toConfigurationSecrets
import net.corda.crypto.core.CryptoConsts
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.validation.ConfigurationValidator
import net.corda.libs.configuration.validation.ConfigurationValidatorFactory
import net.corda.schema.configuration.BootConfig.BOOT_CRYPTO
import net.corda.schema.configuration.ConfigKeys.SECRETS_PASSPHRASE
import net.corda.schema.configuration.ConfigKeys.SECRETS_SALT
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `Should build default bootstrap configs`() {
        val params = WorkerHelpers.getParams(
            emptyArray(),
            CryptoWorkerParams().also {
                it.defaultParams.secretsParams = mapOf(
                    SECRETS_PASSPHRASE to "passphrase",
                    SECRETS_SALT to "salt"
                )
            }
        )
        val config = buildBoostrapConfig(params, validatorFactoryMock).getConfig(BOOT_CRYPTO)
        val connectionFactory = config.cryptoConnectionFactory()
        assertEquals(5, connectionFactory.expireAfterAccessMins)
        assertEquals(3, connectionFactory.maximumSize)
        val signingService = config.signingService()
        assertEquals(60, signingService.cache.expireAfterAccessMins)
        assertEquals(1000, signingService.cache.maximumSize)
        val hsmService = config.hsmService()
        assertEquals(5, hsmService.cache.expireAfterAccessMins)
        assertEquals(10, hsmService.cache.maximumSize)
        assertEquals(3, hsmService.downstreamMaxAttempts)
        assertEquals(CryptoConsts.SOFT_HSM_ID, config.hsmId())
        Assertions.assertThat(config.hsmMap()).hasSize(1)
        val softWorker = config.hsm()
        assertEquals("", softWorker.workerTopicSuffix)
        assertEquals(20000L, softWorker.retry.attemptTimeoutMills)
        assertEquals(3, softWorker.retry.maxAttempts)
        assertEquals(CryptoConsts.SOFT_HSM_SERVICE_NAME, softWorker.hsm.name)
        Assertions.assertThat(softWorker.hsm.categories).hasSize(1)
        assertEquals("*", softWorker.hsm.categories[0].category)
        assertEquals(PrivateKeyPolicy.WRAPPED, softWorker.hsm.categories[0].policy)
        assertEquals(MasterKeyPolicy.UNIQUE, softWorker.hsm.masterKeyPolicy)
        assertNull(softWorker.hsm.masterKeyAlias)
        assertEquals(-1, softWorker.hsm.capacity)
        Assertions.assertThat(softWorker.hsm.supportedSchemes).hasSize(8)
        Assertions.assertThat(softWorker.hsm.supportedSchemes).contains(
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
        assertEquals("CACHING", hsmCfg.getString("keyMap.name"))
        assertEquals(60, hsmCfg.getLong("keyMap.cache.expireAfterAccessMins"))
        assertEquals(1000, hsmCfg.getLong("keyMap.cache.maximumSize"))
        assertEquals("CACHING", hsmCfg.getString("wrappingKeyMap.name"))
        assertEquals("master-salt", hsmCfg.getString("wrappingKeyMap.salt"))
        assertEquals(
            "master-passphrase", hsmCfg.toConfigurationSecrets().getSecret(
                hsmCfg.getConfig("wrappingKeyMap.passphrase").root().unwrapped()
            )
        )
        assertEquals(60, hsmCfg.getLong("wrappingKeyMap.cache.expireAfterAccessMins"))
        assertEquals(100, hsmCfg.getLong("wrappingKeyMap.cache.maximumSize"))
        assertEquals("DEFAULT", hsmCfg.getString("wrapping.name"))
        val opsBusProcessor = config.opsBusProcessor()
        assertEquals(3, opsBusProcessor.maxAttempts)
        assertEquals(1, opsBusProcessor.waitBetweenMills.size)
        assertEquals(200L, opsBusProcessor.waitBetweenMills[0])
        val flowBusProcessor = config.flowBusProcessor()
        assertEquals(3, flowBusProcessor.maxAttempts)
        assertEquals(1, flowBusProcessor.waitBetweenMills.size)
        assertEquals(200L, flowBusProcessor.waitBetweenMills[0])
        val hsmConfigBusProcessor = config.hsmConfigBusProcessor()
        assertEquals(3, hsmConfigBusProcessor.maxAttempts)
        assertEquals(1, hsmConfigBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmConfigBusProcessor.waitBetweenMills[0])
        val hsmRegistrationBusProcessor = config.hsmRegistrationBusProcessor()
        assertEquals(3, hsmRegistrationBusProcessor.maxAttempts)
        assertEquals(1, hsmRegistrationBusProcessor.waitBetweenMills.size)
        assertEquals(200L, hsmRegistrationBusProcessor.waitBetweenMills[0])
    }
}