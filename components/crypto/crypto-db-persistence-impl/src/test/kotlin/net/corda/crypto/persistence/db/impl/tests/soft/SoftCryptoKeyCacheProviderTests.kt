package net.corda.crypto.persistence.db.impl.tests.soft

import net.corda.crypto.persistence.db.impl.soft.SoftCryptoKeyCacheProviderImpl
import net.corda.crypto.persistence.db.impl.tests.infra.TestConfigurationReadService
import net.corda.crypto.persistence.db.impl.tests.infra.TestDbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class SoftCryptoKeyCacheProviderTests {
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var dbConnectionManager: TestDbConnectionManager
    private lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var component: SoftCryptoKeyCacheProviderImpl

    @BeforeEach
    fun setup() {
        coordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl())
        configurationReadService = TestConfigurationReadService(coordinatorFactory).also {
            it.start()
            eventually {
                assertTrue(it.isRunning)
            }
        }
        dbConnectionManager = TestDbConnectionManager(coordinatorFactory).also {
            it.start()
            eventually {
                kotlin.test.assertTrue(it.isRunning)
            }
        }
        whenever(dbConnectionManager._mock.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)).doReturn(
            mock()
        )
        component = SoftCryptoKeyCacheProviderImpl(
            coordinatorFactory,
            configurationReadService,
            dbConnectionManager,
            mock()
        )
    }

    private fun act() =
        component.impl.getInstance()

    @Test
    fun `Should create ActiveImpl only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> { act() }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.ActiveImpl::class.java, component.impl)
        assertNotNull(act())
    }

    @Test
    fun `Should use InactiveImpl when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> { act() }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.ActiveImpl::class.java, component.impl)
        assertNotNull(act())
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> { act() }
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> { act() }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.ActiveImpl::class.java, component.impl)
        val instance11 = act()
        val instance12 = act()
        assertNotNull(instance11)
        assertSame(instance11, instance12)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.InactiveImpl::class.java, component.impl)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(SoftCryptoKeyCacheProviderImpl.ActiveImpl::class.java, component.impl)
        val instance21 = act()
        val instance22 = act()
        assertNotNull(instance21)
        assertSame(instance21, instance22)
        assertNotSame(instance11, instance21)
    }
}

