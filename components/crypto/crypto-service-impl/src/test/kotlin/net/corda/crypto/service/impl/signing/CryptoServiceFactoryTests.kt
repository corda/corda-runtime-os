package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoServiceFactoryTests {
    private lateinit var tenantId: String
    private lateinit var factory: TestServicesFactory
    private lateinit var component: CryptoServiceFactoryImpl

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        factory = TestServicesFactory()
        component = CryptoServiceFactoryImpl(
            factory.coordinatorFactory,
            factory.registration,
            factory.schemeMetadata,
            listOf(
                factory.softCryptoKeyCacheProvider
            )
        )
    }

    @Test
    fun `Should start component and use active implementation only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        assertNotNull(
            component.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER)
        )
    }

    @Test
    fun `getInstance should return same instance which resolves to the same HSM config id`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER)
        val i2 = component.getInstance(UUID.randomUUID().toString(), CryptoConsts.HsmCategories.LEDGER)
        val i3 = component.getInstance(UUID.randomUUID().toString(), CryptoConsts.HsmCategories.LEDGER)
        assertNotNull(i1)
        assertSame(i1, i2)
        assertSame(i1, i3)
    }

    @Test
    fun `getInstance should return different instance which resolves to different HSM config id`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER)
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        val i1 = component.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER)
        val i2 = component.getInstance(tenantId, CryptoConsts.HsmCategories.TLS)
        assertNotNull(i1)
        assertNotSame(i1, i2)
    }

    @Test
    fun `Should deactivate implementation when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
        factory.registration.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.InactiveImpl::class.java, component.impl)
        factory.registration.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoServiceFactoryImpl.ActiveImpl::class.java, component.impl)
    }
}