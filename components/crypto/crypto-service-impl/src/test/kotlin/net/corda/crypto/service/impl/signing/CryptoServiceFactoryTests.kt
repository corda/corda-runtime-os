package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.service.impl._utils.TestServicesFactory
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
}