package net.corda.session.mapper.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.FlowConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FlowMapperServiceTest {

    private val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))

    private val messagingConfig = configFactory.create(
        ConfigFactory.parseString(
            """
                    """.trimIndent()
        )
    )

    @Test
    fun `flow mapper service correctly responds to dependencies changes`() {
        val subscriptionFactory = mock<SubscriptionFactory>().also {
            doAnswer { mock<StateAndEventSubscription<String, String, String>>() }
                .whenever(it).createStateAndEventSubscription<String, String, String>(any(), any(), any(), any())
        }
        LifecycleTest {
            addDependency<ConfigurationReadService>()

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock()
            )
        }.run {

            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(mapOf(FLOW_CONFIG to flowConfig, MESSAGING_CONFIG to messagingConfig))

            verifyIsUp<FlowMapperService>()

            repeat(5) {
                val iteration = it + 1 // start from one
                toggleDependency<ConfigurationReadService>(
                    verificationWhenDown = {
                        verify(lastConfigHandle).close()
                    },
                    verificationWhenUp = {
                        verify(configReadService, times(iteration + 1)).registerComponentForUpdates(any(), any())
                    }
                )
            }
            bringDependencyDown<ConfigurationReadService>()
        }
    }

    @Test
    fun `flow mapper service correctly reacts to config changes`() {
        val subName = LifecycleCoordinatorName("sub")
        val subscription = mock<StateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>>().apply {
            whenever(subscriptionName).thenReturn(subName)
        }
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(any(), any(), any(), any()))
                .thenReturn(subscription)
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency(subName)

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock()
            )
        }.run {
            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(mapOf(FLOW_CONFIG to flowConfig, MESSAGING_CONFIG to messagingConfig))

            // Create and start the subscription (using the message config)
            verify(subscriptionFactory).createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(
                any(),
                any(),
                eq(messagingConfig),
                any()
            )
            verify(subscription).start()
            verifyIsUp<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(mapOf(FLOW_CONFIG to flowConfig, MESSAGING_CONFIG to messagingConfig))

            // Close, recreate and start the subscription (using the message config)
            verify(subscription).close()
            verify(subscriptionFactory, times(2)).createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(
                any(),
                any(),
                eq(messagingConfig),
                any()
            )
            verify(subscription, times(2)).start()
            verifyIsUp<FlowMapperService>()
        }
    }

    @Test
    fun `flow mapper service correctly handles bad config`() {
        val subName = LifecycleCoordinatorName("sub")
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(any(), any(), any(), any()))
                .thenThrow(CordaMessageAPIConfigException("Bad config!"))
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency(subName)

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock()
            )
        }.run {
            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(mapOf(FLOW_CONFIG to flowConfig, MESSAGING_CONFIG to messagingConfig))

            // Create and start the subscription (using the message config)
            verify(subscriptionFactory).createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(
                any(),
                any(),
                eq(messagingConfig),
                any()
            )
            verifyIsInError<FlowMapperService>()
        }
    }

}
