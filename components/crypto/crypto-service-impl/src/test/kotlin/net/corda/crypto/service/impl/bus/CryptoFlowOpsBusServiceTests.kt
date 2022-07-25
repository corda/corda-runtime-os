package net.corda.crypto.service.impl.bus

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.service.impl.bus.CryptoFlowOpsBusServiceImpl
import net.corda.crypto.service.impl.infra.TestDurableSubscription
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoFlowOpsBusServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: TestDurableSubscription<String, FlowOpsRequest>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var clientCoordinator: LifecycleCoordinator
    private lateinit var client: CryptoOpsProxyClient
    private lateinit var component: CryptoFlowOpsBusServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        subscription = TestDurableSubscription(factory.coordinatorFactory)
        subscriptionFactory = mock {
            on {
                createDurableSubscription<String, FlowOpsRequest>(any(), any(), any(), anyOrNull())
            } doReturn subscription
        }
        clientCoordinator = factory.coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
        ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }
        client = mock {
            on { isRunning } doAnswer { clientCoordinator.isRunning }
            on { start() } doAnswer { clientCoordinator.start() }
            on { stop() } doAnswer { clientCoordinator.stop() }
        }
        client.start()
        eventually {
            assertTrue(clientCoordinator.isRunning)
            assertEquals(LifecycleStatus.UP, clientCoordinator.status)
        }
        component = CryptoFlowOpsBusServiceImpl(
            factory.coordinatorFactory,
            subscriptionFactory,
            client,
            factory.readService
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