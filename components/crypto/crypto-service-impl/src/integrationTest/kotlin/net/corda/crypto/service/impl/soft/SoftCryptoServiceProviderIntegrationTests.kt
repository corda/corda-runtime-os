package net.corda.crypto.service.impl.soft

import net.corda.crypto.CryptoConsts
import net.corda.crypto.component.persistence.SoftKeysPersistenceProvider
import net.corda.crypto.service.SoftCryptoServiceConfig
import net.corda.crypto.service.SoftCryptoServiceProvider
import net.corda.lifecycle.Lifecycle
import net.corda.test.util.createTestCase
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.UUID

@ExtendWith(ServiceExtension::class)
class SoftCryptoServiceProviderIntegrationTests {
    companion object {
        private fun Lifecycle.stopAndWait() {
            stop()
            isStopped()
        }

        private fun Lifecycle.startAndWait() {
            start()
            isStarted()
        }

        private fun Lifecycle.isStopped() = eventually {
            assertFalse(isRunning, "Failed waiting to stop for ${this::class.java.name}")
        }

        private fun Lifecycle.isStarted() = eventually {
            assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
        }
    }

    @InjectService(timeout = 5000L)
    lateinit var softKeysPersistentProvider: SoftKeysPersistenceProvider

    @InjectService(timeout = 5000L)
    lateinit var provider: SoftCryptoServiceProvider

    @BeforeEach
    fun setup() {
        softKeysPersistentProvider.startAndWait()
        provider.startAndWait()
    }

    @Test
    @Timeout(30)
    fun `Should be able to create instances concurrently`() {
        assertTrue(provider.isRunning)
        (1..100).createTestCase {
            val context = CryptoServiceContext(
                memberId = UUID.randomUUID().toString(),
                category = CryptoConsts.CryptoCategories.LEDGER,
                cipherSuiteFactory = object  : CipherSuiteFactory {
                    override fun getDigestService(): DigestService {
                        throw NotImplementedError()
                    }
                    override fun getSchemeMap(): CipherSchemeMetadata {
                        throw NotImplementedError()
                    }
                    override fun getSignatureVerificationService(): SignatureVerificationService {
                        throw NotImplementedError()
                    }
                },
                config = SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            )
            assertNotNull(provider.getInstance(context))
        }.runAndValidate()
        provider.stopAndWait()
        assertFalse(provider.isRunning)
    }
}