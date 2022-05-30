package net.corda.crypto.service.impl.bus.flow

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoFlowOpsBusServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: Subscription<String, FlowOpsRequest>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var clientCoordinator: LifecycleCoordinator
    private lateinit var client: CryptoOpsProxyClient
    private lateinit var component: CryptoFlowOpsBusServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        subscription = mock()
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
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.ActiveImpl::class.java, component.impl)
    }

    @Test
    fun `Should close subscription when component is stopped`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.InactiveImpl::class.java, component.impl)
        assertThrows<IllegalStateException> {
            component.impl.subscription
        }
        Mockito.verify(subscription, times(1)).close()
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.InactiveImpl::class.java, component.impl)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
        clientCoordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.InactiveImpl::class.java, component.impl)
        clientCoordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertInstanceOf(CryptoFlowOpsBusServiceImpl.ActiveImpl::class.java, component.impl)
        assertSame(subscription, component.impl.subscription)
        Mockito.verify(subscription, atLeast(1)).close()
    }
}