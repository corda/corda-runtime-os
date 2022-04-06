package net.corda.crypto.service.impl.rpc

import net.corda.crypto.service.impl._utils.TestConfigurationReadService
import net.corda.crypto.service.impl._utils.TestServicesFactory
import net.corda.crypto.service.impl.signing.SigningServiceFactoryImpl
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoOpsServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: RPCSubscription<RpcOpsRequest, RpcOpsResponse>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var configurationReadService: TestConfigurationReadService
    private lateinit var component: CryptoOpsServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        subscription = mock()
        subscriptionFactory = mock {
            on { createRPCSubscription<RpcOpsRequest, RpcOpsResponse>(any(), any(), any()) } doReturn subscription
        }
        configurationReadService = factory.createConfigurationReadService()
        component = CryptoOpsServiceImpl(
            factory.coordinatorFactory,
            subscriptionFactory,
            SigningServiceFactoryImpl(
                factory.coordinatorFactory,
                factory.schemeMetadata,
                factory.signingCacheProvider,
                factory.createCryptoServiceFactory()
            ).also {
                it.start()
                eventually {
                    assertTrue(it.isRunning)
                }
            },
            configurationReadService
        )
    }

    @Test
    fun `Should create subscription only after the component is up`() {
        assertFalse(component.isRunning)
        assertNull(component.subscription)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.subscription)
    }

    @Test
    fun `Should close subscription when component is stopped`() {
        assertFalse(component.isRunning)
        assertNull(component.subscription)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.subscription)
        component.stop()
        eventually {
            assertFalse(component.isRunning)
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertNull(component.subscription)
        Mockito.verify(subscription, times(1)).close()
    }

    @Test
    fun `Should go UP and DOWN as its dependencies go UP and DOWN`() {
        assertFalse(component.isRunning)
        assertNull(component.subscription)
        component.start()
        eventually {
            assertTrue(component.isRunning)
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.subscription)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.DOWN)
        eventually {
            assertEquals(LifecycleStatus.DOWN, component.lifecycleCoordinator.status)
        }
        assertNull(component.subscription)
        configurationReadService.coordinator.updateStatus(LifecycleStatus.UP)
        eventually {
            assertEquals(LifecycleStatus.UP, component.lifecycleCoordinator.status)
        }
        assertSame(subscription, component.subscription)
    }
}