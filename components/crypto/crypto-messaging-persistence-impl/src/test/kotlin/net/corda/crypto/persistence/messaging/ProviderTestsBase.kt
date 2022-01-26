package net.corda.crypto.persistence.messaging

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

abstract class ProviderTestsBase<PROVIDER: Lifecycle> {
    private var coordinatorIsRunning = false
    protected lateinit var sub: CompactedSubscription<String, SoftKeysRecord>
    protected lateinit var pub: Publisher
    protected lateinit var subscriptionFactory: SubscriptionFactory
    protected lateinit var publisherFactory: PublisherFactory
    protected lateinit var config: SmartConfig
    private lateinit var registrationHandle: AutoCloseable
    protected lateinit var configurationReadService: ConfigurationReadService
    protected lateinit var coordinator: LifecycleCoordinator
    protected lateinit var coordinatorFactory: LifecycleCoordinatorFactory
    protected lateinit var provider: PROVIDER

    fun setup(componentFactory: () -> PROVIDER) {
        registrationHandle = mock()
        configurationReadService = mock {
            on { registerForUpdates(any()) } doReturn registrationHandle
        }
        sub = mock()
        pub = mock()
        subscriptionFactory = mock  {
            on {
                createCompactedSubscription(any(), any<CompactedProcessor<*, *>>(), any())
            }.thenReturn(sub)
        }
        publisherFactory = mock {
            on {
                createPublisher(any(), any())
            }.thenReturn(pub)
        }
        config = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
        coordinator = mock {
            on { start() } doAnswer {
                coordinatorIsRunning = true
            }
            on { stop() } doAnswer {
                coordinatorIsRunning = false
            }
            on { isRunning }.thenAnswer { coordinatorIsRunning }
            on { postEvent(any()) } doAnswer {
                val event = it.getArgument(0, LifecycleEvent::class.java)
                provider::class.memberFunctions.first { f -> f.name == "eventHandler" }.let { ff ->
                    ff.isAccessible = true
                    ff.call(provider, event, coordinator)
                }
                Unit
            }
        }
        coordinatorFactory = mock {
            on { createCoordinator(any(), any()) } doReturn coordinator
        }
        provider = componentFactory()
    }

    fun start() {
        provider.start()
    }

    fun postUpEvent() {
        coordinator.postEvent(
            RegistrationStatusChangeEvent(
                registration = mock(),
                status = LifecycleStatus.UP
            )
        )
    }
}