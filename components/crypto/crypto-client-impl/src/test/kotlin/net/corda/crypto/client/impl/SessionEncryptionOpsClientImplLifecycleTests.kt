package net.corda.crypto.client.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.SessionEncryptionOpsClient
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SessionEncryptionOpsClientImplLifecycleTests {
    private lateinit var sessionEncryptionOpsClientImpl : SessionEncryptionOpsClientImpl

    private val lifecycleTest = LifecycleTest {
        val publisherFactory = mock<PublisherFactory>().also {
            whenever(it.createHttpRpcClient()).doReturn(mock())
        }

        addDependency<ConfigurationReadService>()
        sessionEncryptionOpsClientImpl = SessionEncryptionOpsClientImpl(
            coordinatorFactory,
            publisherFactory,
            configReadService,
            mock()
        )
        sessionEncryptionOpsClientImpl.lifecycleCoordinator
    }

    @Test
    fun `On config read service going UP, creates Impl and goes UP`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<ConfigurationReadService>()

            verify(configReadService).registerComponentForUpdates(
                eq(sessionEncryptionOpsClientImpl.lifecycleCoordinator),
                eq(
                    setOf(
                        ConfigKeys.BOOT_CONFIG,
                    )
                )
            )

            sendConfigUpdate(
                sessionEncryptionOpsClientImpl.lifecycleCoordinator.name,
                mapOf(ConfigKeys.BOOT_CONFIG to SmartConfigImpl.empty())
            )

            assertNotNull(sessionEncryptionOpsClientImpl.impl)
            verifyIsUp<SessionEncryptionOpsClient>()
        }
    }

    @Test
    fun `On config read service going DOWN, goes DOWN`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<ConfigurationReadService>()
            sendConfigUpdate(
                sessionEncryptionOpsClientImpl.lifecycleCoordinator.name,
                mapOf(ConfigKeys.BOOT_CONFIG to SmartConfigImpl.empty())
            )
            verifyIsUp<SessionEncryptionOpsClient>()

            bringDependencyDown<ConfigurationReadService>()
            verifyIsDown<SessionEncryptionOpsClient>()
        }
    }

    @Test
    fun `When is not UP and try to access impl throws IllegalStateException`() {
        lifecycleTest.run {
            testClass.start()
            bringDependencyUp<ConfigurationReadService>()
            sendConfigUpdate(
                sessionEncryptionOpsClientImpl.lifecycleCoordinator.name,
                mapOf(ConfigKeys.BOOT_CONFIG to SmartConfigImpl.empty())
            )
            verifyIsUp<SessionEncryptionOpsClient>()

            bringDependencyDown<ConfigurationReadService>()
            assertThrows<IllegalStateException>("Component SessionEncryptionOpsClient is not ready.") {
                sessionEncryptionOpsClientImpl.impl
            }
        }
    }
}