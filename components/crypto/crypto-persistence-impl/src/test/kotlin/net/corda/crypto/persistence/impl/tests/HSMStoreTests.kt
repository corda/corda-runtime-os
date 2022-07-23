package net.corda.crypto.persistence.impl.tests

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.impl.HSMStoreImpl
import net.corda.crypto.persistence.impl.tests.infra.TestCryptoConnectionsFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class HSMStoreTests {
    private lateinit var connectionsFactory: TestCryptoConnectionsFactory
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var component: HSMStoreImpl

    @BeforeEach
    fun setup() {
        coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        connectionsFactory = TestCryptoConnectionsFactory(coordinatorFactory).also {
            it.start()
            eventually {
                assertEquals(LifecycleStatus.UP, it.lifecycleCoordinator.status)
            }
        }
        whenever(connectionsFactory._mock.getEntityManagerFactory(CryptoTenants.CRYPTO)).doReturn(
            mock()
        )
        component = HSMStoreImpl(
            coordinatorFactory,
            connectionsFactory,
            CipherSchemeMetadataImpl()
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
    fun `Should go UP and DOWN as its upstream dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> { component.impl }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
        connectionsFactory.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertThrows<IllegalStateException> { component.impl }
        connectionsFactory.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertNotNull(component.impl)
    }
}
