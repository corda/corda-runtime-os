package net.corda.crypto.service.impl.soft

import net.corda.crypto.CryptoConsts
import net.corda.crypto.service.CryptoServiceProviderWithLifecycle
import net.corda.lifecycle.Lifecycle
import net.corda.test.util.createTestCase
import net.corda.test.util.eventually
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.mock
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(ServiceExtension::class)
class SoftCryptoServiceProviderIntegrationTests {
    companion object {
        const val CLIENT_ID = "crypto-service-impl-soft-provider-integration-test"

        private fun Lifecycle.stopAndWait() {
            stop()
            isStopped()
        }

        private fun Lifecycle.startAndWait() {
            start()
            isStarted()
        }

        private fun Lifecycle.isStopped() = eventually {
            Assertions.assertFalse(isRunning, "Failed waiting to stop for ${this::class.java.name}")
        }

        private fun Lifecycle.isStarted() = eventually {
            Assertions.assertTrue(isRunning, "Failed waiting to start for ${this::class.java.name}")
        }
    }

    @InjectService(timeout = 5000L)
    lateinit var provider: CryptoServiceProviderWithLifecycle<*>

    private fun CryptoServiceProvider.createCryptoService(category: String): CryptoService = getInstance(
        CryptoServiceContext(
            memberId = services.tenantId,
            category = category,
            cipherSuiteFactory = factory,
            config = SoftCryptoServiceConfig(
                passphrase = "PASSPHRASE",
                salt = "SALT"
            )
        )
    )

    @BeforeEach
    fun setup() {
        provider.startAndWait()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    @Timeout(30)
    fun `Should be able to create instances concurrently`() {
        assertTrue(provider.isRunning)
        (1..100).createTestCase {
            val context = CryptoServiceContext(
                memberId = UUID.randomUUID().toString(),
                category = CryptoConsts.CryptoCategories.LEDGER,
                cipherSuiteFactory = mock(),
                config = SoftCryptoServiceConfig(
                    passphrase = "PASSPHRASE",
                    salt = "SALT"
                )
            )
            assertNotNull((provider as CryptoServiceProvider<SoftCryptoServiceConfig>).getInstance(context))
        }.runAndValidate()
        provider.stop()
        assertFalse(provider.isRunning)
    }
}