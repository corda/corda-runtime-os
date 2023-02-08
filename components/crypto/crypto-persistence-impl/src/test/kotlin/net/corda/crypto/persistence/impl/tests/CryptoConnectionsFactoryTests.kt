package net.corda.crypto.persistence.impl.tests

import net.corda.crypto.component.test.utils.TestConfigurationReadService
import net.corda.crypto.config.impl.createDefaultCryptoConfig
import net.corda.crypto.persistence.impl.CryptoConnectionsFactoryImpl
import net.corda.crypto.persistence.impl.tests.infra.TestDbConnectionManager
import net.corda.crypto.persistence.impl.tests.infra.TestVirtualNodeInfoReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoConnectionsFactoryTests {
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var dbConnectionManager: TestDbConnectionManager
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var virtualNodeInfoReadService: TestVirtualNodeInfoReadService
    private lateinit var component: CryptoConnectionsFactoryImpl

    @BeforeEach
    fun setup() {
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        configurationReadService = TestConfigurationReadService(
            coordinatorFactory,
            configUpdates = listOf(
                CRYPTO_CONFIG to SmartConfigFactory.createWithoutSecurityServices().create(createDefaultCryptoConfig("pass", "salt"))
            )
        ).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        dbConnectionManager = TestDbConnectionManager(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        virtualNodeInfoReadService = TestVirtualNodeInfoReadService(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        component = CryptoConnectionsFactoryImpl(
            coordinatorFactory,
            configurationReadService,
            dbConnectionManager,
            mock(),
            virtualNodeInfoReadService
        )
    }

    @Test
    fun `Should create ActiveImpl only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }

    @Test
    fun `Should use InactiveImpl when component is stopped`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl }
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
        dbConnectionManager.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl }
        dbConnectionManager.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }
}

