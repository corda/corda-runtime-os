package net.corda.crypto.persistence.db.impl.tests.signing

import net.corda.crypto.persistence.db.impl.signing.SigningKeyStoreProviderImpl
import net.corda.crypto.persistence.db.impl.tests.infra.TestConfigurationReadService
import net.corda.crypto.persistence.db.impl.tests.infra.TestDbConnectionManager
import net.corda.crypto.persistence.db.impl.tests.infra.TestVirtualNodeInfoReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SigningKeyStoreProviderTests {
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var dbConnectionManager: TestDbConnectionManager
    private lateinit var vNodeInfoReadService: VirtualNodeInfoReadService
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var component: SigningKeyStoreProviderImpl

    @BeforeEach
    fun setup() {
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        configurationReadService = TestConfigurationReadService(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
        dbConnectionManager = TestDbConnectionManager(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
        vNodeInfoReadService = TestVirtualNodeInfoReadService(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
        component = SigningKeyStoreProviderImpl(
            coordinatorFactory,
            configurationReadService,
            dbConnectionManager,
            mock(),
            mock(),
            mock(),
            vNodeInfoReadService
        )
    }

    @Test
    fun `Should create ActiveImpl only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl.getInstance() }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.getInstance())
    }

    @Test
    fun `Should use InactiveImpl when component is stopped`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl.getInstance() }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl.getInstance())
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl.getInstance() }
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl.getInstance() }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val instance11 = component.impl.getInstance()
        val instance12 = component.impl.getInstance()
        assertNotNull(instance11)
        assertSame(instance11, instance12)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val instance21 = component.impl.getInstance()
        val instance22 = component.impl.getInstance()
        assertNotNull(instance21)
        assertSame(instance21, instance22)
    }
}

