package net.corda.crypto.service.impl.bus.registration

import net.corda.crypto.service.impl.infra.TestConfigurationReadService
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationRequest
import net.corda.data.crypto.wire.hsm.registration.HSMRegistrationResponse
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HSMRegistrationBusServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: RPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var component: HSMRegistrationBusServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        subscription = mock()
        subscriptionFactory = mock {
            on { createRPCSubscription<HSMRegistrationRequest, HSMRegistrationResponse>(any(), any(), any()) } doReturn subscription
        }
        configurationReadService = factory.createConfigurationReadService()
        component = HSMRegistrationBusServiceImpl(
            factory.coordinatorFactory,
            configurationReadService,
            subscriptionFactory,
            factory.hsmService
        )
    }

    @Test
    fun `Should create subscription only after the component is up`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMRegistrationBusServiceImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
    }

    @Test
    fun `Should close subscription when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMRegistrationBusServiceImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationBusServiceImpl.InactiveImpl::class.java, component.impl)
        Mockito.verify(subscription, times(1)).close()
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(HSMRegistrationBusServiceImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationBusServiceImpl.InactiveImpl::class.java, component.impl)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(HSMRegistrationBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
        Mockito.verify(subscription, atLeast(1)).close()
    }
}