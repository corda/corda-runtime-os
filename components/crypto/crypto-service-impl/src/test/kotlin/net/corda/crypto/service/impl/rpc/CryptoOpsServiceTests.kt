package net.corda.crypto.service.impl.rpc

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.service.impl._utils.TestServicesFactory
import net.corda.crypto.service.impl.signing.SigningServiceFactoryImpl
import net.corda.data.crypto.wire.ops.rpc.RpcOpsRequest
import net.corda.data.crypto.wire.ops.rpc.RpcOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.RPCSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys
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
    private lateinit var emptyConfig: SmartConfig
    private lateinit var configurationReadService: ConfigurationReadService
    private lateinit var component: CryptoOpsServiceImpl

    @BeforeEach
    fun setup() {
        factory = TestServicesFactory()
        emptyConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
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
            ),
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