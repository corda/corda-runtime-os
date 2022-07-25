package net.corda.crypto.service.impl.bus

import net.corda.crypto.component.test.utils.reportDownComponents
import net.corda.crypto.service.impl.infra.TestRPCSubscription
import net.corda.crypto.service.impl.infra.TestServicesFactory
import net.corda.crypto.service.impl.SigningServiceFactoryImpl
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import net.corda.v5.base.util.contextLogger
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

class CryptoOpsBusServiceTests {
    companion object {
        private val logger = contextLogger()
    }

    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: TestRPCSubscription<RpcOpsRequest, RpcOpsResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var component: CryptoOpsBusServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        subscription = TestRPCSubscription(factory.coordinatorFactory)
        subscriptionFactory = mock {
            on { createRPCSubscription<RpcOpsRequest, RpcOpsResponse>(any(), any(), any()) } doReturn subscription
        }
        component = CryptoOpsBusServiceImpl(
            factory.coordinatorFactory,
            subscriptionFactory,
            SigningServiceFactoryImpl(
                factory.coordinatorFactory,
                factory.schemeMetadata,
                factory.signingKeyStore,
                factory.createCryptoServiceFactory()
            ).also {
                it.start()
                eventually {
                    assertTrue(it.isRunning)
                }
            },
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
            assertEquals(
                LifecycleStatus.UP,
                component.lifecycleCoordinator.status,
                factory.coordinatorFactory.reportDownComponents(logger)
            )
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