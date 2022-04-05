package net.corda.crypto.service.impl.flow

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.crypto.service.impl._utils.TestServicesFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
import net.corda.test.util.eventually
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CryptoFlowOpsServiceTests {
    private lateinit var factory: TestServicesFactory
    private lateinit var subscription: Subscription<String, FlowOpsRequest>
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var emptyConfig: SmartConfig
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var clientCoordinator: LifecycleCoordinator
    private lateinit var client: CryptoOpsProxyClient
    private lateinit var component: CryptoFlowOpsServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        emptyConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        subscription = mock()
        subscriptionFactory = mock {
            on { createDurableSubscription<String, FlowOpsRequest>(any(), any(), any(), any()) } doReturn subscription
        }
        configurationReadService = factory.createConfigurationReadService()
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
        component = CryptoFlowOpsServiceImpl(
            factory.coordinatorFactory,
            subscriptionFactory,
            client,
            configurationReadService
        )
    }

    @Test
    fun `Should create subscription only after the component is up`() {
        assertFalse(component.isRunning)
        assertNull(component.subscription)
        component.start()
        component.lifecycleCoordinator.postEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to emptyConfig,
                    ConfigKeys.MESSAGING_CONFIG to emptyConfig
                )
            )
        )
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
        }
        component.lifecycleCoordinator.postEvent(
            ConfigChangedEvent(
                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                mapOf(
                    ConfigKeys.BOOT_CONFIG to emptyConfig,
                    ConfigKeys.MESSAGING_CONFIG to emptyConfig
                )
            )
        )
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
}