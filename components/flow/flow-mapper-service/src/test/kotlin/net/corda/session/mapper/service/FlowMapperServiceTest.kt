package net.corda.session.mapper.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.schema.configuration.FlowConfig
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class FlowMapperServiceTest {

    private val configFactory = SmartConfigFactory.createWithoutSecurityServices()
    private val flowConfig = SmartConfigImpl.empty().withValue(FlowConfig.SESSION_P2P_TTL, ConfigValueFactory.fromAnyRef(10000))
        .withValue(FlowConfig.PROCESSING_FLOW_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(1000L))

    private val messagingConfig = configFactory.create(
        ConfigFactory.parseString(
            ""
        )
    )

    private val stateManagerConfig = configFactory.create(
        ConfigFactory.parseString("")
    )

    private val configMap = mapOf(
        FLOW_CONFIG to flowConfig,
        MESSAGING_CONFIG to messagingConfig,
        STATE_MANAGER_CONFIG to stateManagerConfig
    )

    @Test
    fun `flow mapper service correctly responds to dependencies changes`() {
        val subscriptionFactory = mock<SubscriptionFactory>().also {
            doAnswer { mock<StateAndEventSubscription<String, String, String>>() }
                .whenever(it).createStateAndEventSubscription<String, String, String>(any(), any(), any(), any())
            doAnswer { mock<Subscription<String, String>>() }
                .whenever(it).createDurableSubscription<String, String>(any(), any(), any(), anyOrNull())
        }
        val stateManagerFactory = mock<StateManagerFactory>().also {
            doAnswer { mock<StateManager>() }.whenever(it).create(any())
        }
        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<LocallyHostedIdentitiesService>()

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock(),
                stateManagerFactory
            )
        }.run {

            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

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
        val scheduledTaskSubName = LifecycleCoordinatorName("scheduledSub")
        val scheduledTaskSubscription = mock<Subscription<String, ScheduledTaskTrigger>>().apply {
            whenever(subscriptionName).thenReturn(scheduledTaskSubName)
        }
        val cleanupTaskSubName = LifecycleCoordinatorName("cleanup")
        val cleanupSubscription = mock<Subscription<String, ExecuteCleanup>>().apply {
            whenever(subscriptionName).thenReturn(cleanupTaskSubName)
        }
        val subscriptionFactory = mock<SubscriptionFactory>().apply {
            whenever(createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(any(), any(), any(), any()))
                .thenReturn(subscription)
            whenever(createDurableSubscription<String, ScheduledTaskTrigger>(any(), any(), any(), anyOrNull()))
                .thenReturn(scheduledTaskSubscription)
            whenever(createDurableSubscription<String, ExecuteCleanup>(any(), any(), any(), anyOrNull()))
                .thenReturn(cleanupSubscription)
        }
        val stateManagerFactory = mock<StateManagerFactory>().also {
            doAnswer { mock<StateManager>() }.whenever(it).create(any())
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<LocallyHostedIdentitiesService>()
            addDependency(subName)

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock(),
                stateManagerFactory
            )
        }.run {
            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

            // Create and start the subscription (using the message config)
            verify(subscriptionFactory).createStateAndEventSubscription<String, FlowMapperState, FlowMapperEvent>(
                any(),
                any(),
                eq(messagingConfig),
                any()
            )
            verify(subscription).start()
            verifyIsUp<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

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
        val stateManagerFactory = mock<StateManagerFactory>().also {
            doAnswer { mock<StateManager>() }.whenever(it).create(any())
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<LocallyHostedIdentitiesService>()
            addDependency(subName)

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                mock(),
                mock(),
                stateManagerFactory
            )
        }.run {
            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

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
