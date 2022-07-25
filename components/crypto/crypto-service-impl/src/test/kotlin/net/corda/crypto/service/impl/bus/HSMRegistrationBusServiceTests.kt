package net.corda.crypto.service.impl.bus

import net.corda.crypto.service.impl.bus.HSMRegistrationBusServiceImpl
import net.corda.crypto.service.impl.infra.TestRPCSubscription
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMRegistrationBusServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: TestRPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var component: HSMRegistrationBusServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        subscription = TestRPCSubscription(factory.coordinatorFactory)
        subscriptionFactory = mock {
            on { createRPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>(any(), any(), any()) } doReturn subscription
        }
        component = HSMRegistrationBusServiceImpl(
            factory.coordinatorFactory,
            factory.readService,
            subscriptionFactory,
            factory.hsmService
        )
    }

    @Test
    fun `Should create subscription only after the component is up`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.impl.subscription)
    }

    @Test
    fun `Should close subscription when component is stopped`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.impl.subscription)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertEquals(1, subscription.stopped.get())
    }

    @Test
    fun `Should go UP and DOWN as its upstream dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.impl.subscription)
        factory.readService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        factory.readService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.impl.subscription)
    }

    @Test
    fun `Should go UP and DOWN as its downstream dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.impl.subscription)
        subscription.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        subscription.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.impl.subscription)
    }

    @Test
    fun `Should recreate subscription on config change`() {
        assertFalse(component.isRunning)
        assertThrows<IllegalStateException>() {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        val originalImpl = component.impl
        assertNotNull(component.impl.subscription)
        factory.readService.lifecycleCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        factory.readService.lifecycleCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        factory.readService.reissueConfigChangedEvent(component.lifecycleCoordinator)
        eventually {
            assertNotSame(originalImpl, component.impl)
        }
        assertEquals(1, subscription.stopped.get())
    }
}