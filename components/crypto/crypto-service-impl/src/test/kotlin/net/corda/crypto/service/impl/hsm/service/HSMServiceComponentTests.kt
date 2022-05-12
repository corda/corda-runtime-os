package net.corda.crypto.service.impl.hsm.service

import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HSMServiceComponentTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var component: HSMServiceComponent

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        component = HSMServiceComponent(
            factory.coordinatorFactory,
            factory.readService,
            factory.hsmCacheProvider,
            factory.schemeMetadata,
            factory.opsProxyClient
        )
    }

    @Test
    fun `Should create ActiveImpl only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMServiceComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.service
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMServiceComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.service)
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMServiceComponent.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.service
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMServiceComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.service)
        factory.readService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMServiceComponent.InactiveImpl::class.java, component.impl)
        factory.readService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMServiceComponent.ActiveImpl::class.java, component.impl)
        assertNotNull(component.impl.service)
    }
}