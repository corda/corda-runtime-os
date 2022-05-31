package net.corda.session.mapper.service

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FlowMapperServiceTest {
    @Test
    fun `flow mapper service correctly responds to dependencies changes`() {
        LifecycleTest<FlowMapperService>{
            addDependency<ConfigurationReadService>()

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                mock(),
                mock(),
                mock()
            )
        }.run {

            testClass.start()

            verifyIsDown<FlowMapperService>()
            bringDependenciesUp()
            bringDependencyUp<FlowMapperService>()
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

        LifecycleTest<FlowMapperService> {
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

            verifyIsDown<FlowMapperService>()
            bringDependenciesUp()
            bringDependencyUp<FlowMapperService>()
            verifyIsUp<FlowMapperService>()

            val configFactory = SmartConfigFactory.create(ConfigFactory.empty())
            val bootConfig = configFactory.create(
                ConfigFactory.parseString(
                    """
                    """.trimIndent()
                )
            )

            val messagingConfig = configFactory.create(
                ConfigFactory.parseString(
                    """
                    """.trimIndent()
                )
            )

            sendConfigUpdate(mapOf(BOOT_CONFIG to bootConfig, MESSAGING_CONFIG to messagingConfig))

            // Create and start the subscription (using the message config)
            verify(subscriptionFactory).createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(
                any(),
                any(),
                eq(messagingConfig),
                any()
            )
            verify(subscription).start()

            sendConfigUpdate(mapOf(BOOT_CONFIG to bootConfig, MESSAGING_CONFIG to messagingConfig))

            // Close, recreate and start the subscription (using the message config)
            verify(subscription).close()
            verify(subscriptionFactory, times(2)).createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(
                any(),
                any(),
                eq(messagingConfig),
                any()
            )
            verify(subscription, times(2)).start()
        }
    }

}
