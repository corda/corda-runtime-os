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
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.schema.configuration.FlowConfig
import net.corda.schema.configuration.StateManagerConfig
import net.corda.session.mapper.messaging.mediator.FlowMapperEventMediatorFactory
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
        .withValue(FlowConfig.PROCESSING_FLOW_MAPPER_CLEANUP_TIME, ConfigValueFactory.fromAnyRef(1000L))

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
            doAnswer { mock<Subscription<String, String>>() }
                .whenever(it).createDurableSubscription<String, String>(any(), any(), any(), anyOrNull())
        }
        val flowMapperEventMediatorFactory = mock<FlowMapperEventMediatorFactory>().also {
            doAnswer { mock<MultiSourceEventMediator<String, String, String>>() }
                .whenever(it).create(any(), any(), any())
        }
        val stateManagerFactory = mock<StateManagerFactory>().also {
            doAnswer { mock<StateManager>() }.whenever(it).create(any(), eq(StateManagerConfig.StateType.FLOW_MAPPING))
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<LocallyHostedIdentitiesService>()

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                flowMapperEventMediatorFactory,
                stateManagerFactory,
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
        val eventMediator = mock<MultiSourceEventMediator<String, FlowMapperState, FlowMapperEvent>>().apply {
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
            whenever(createDurableSubscription<String, ScheduledTaskTrigger>(any(), any(), any(), anyOrNull()))
                .thenReturn(scheduledTaskSubscription)
            whenever(createDurableSubscription<String, ExecuteCleanup>(any(), any(), any(), anyOrNull()))
                .thenReturn(cleanupSubscription)
        }
        val flowMapperEventMediatorFactory = mock<FlowMapperEventMediatorFactory>().apply {
            whenever(create(any(), any(), any()))
                .thenReturn(eventMediator)
        }
        val stateManagerFactory = mock<StateManagerFactory>().also {
            doAnswer { mock<StateManager>() }.whenever(it).create(any(), eq(StateManagerConfig.StateType.FLOW_MAPPING))
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<LocallyHostedIdentitiesService>()
            addDependency(subName)

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                flowMapperEventMediatorFactory,
                stateManagerFactory,
            )
        }.run {
            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

            // Create and start the event mediator (using the message config)
            verify(flowMapperEventMediatorFactory).create(
                any(),
                eq(messagingConfig),
                any(),
            )
            verify(eventMediator).start()
            verifyIsUp<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

            // Close, recreate and start the event mediator (using the message config)
            verify(eventMediator).close()
            verify(flowMapperEventMediatorFactory, times(2)).create(
                any(),
                eq(messagingConfig),
                any(),
            )
            verify(eventMediator, times(2)).start()
            verifyIsUp<FlowMapperService>()
        }
    }

    @Test
    fun `flow mapper service correctly handles bad config`() {
        val subName = LifecycleCoordinatorName("sub")
        val subscriptionFactory = mock<SubscriptionFactory>()
        val flowMapperEventMediatorFactory = mock<FlowMapperEventMediatorFactory>().apply {
            whenever(create(any(), any(), any()))
                .thenThrow(CordaMessageAPIConfigException("Bad config!"))
        }
        val stateManagerFactory = mock<StateManagerFactory>().also {
            doAnswer { mock<StateManager>() }.whenever(it).create(any(), eq(StateManagerConfig.StateType.FLOW_MAPPING))
        }

        LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<LocallyHostedIdentitiesService>()
            addDependency(subName)

            FlowMapperService(
                coordinatorFactory,
                configReadService,
                subscriptionFactory,
                flowMapperEventMediatorFactory,
                stateManagerFactory,
            )
        }.run {
            testClass.start()

            bringDependenciesUp()
            verifyIsDown<FlowMapperService>()

            sendConfigUpdate<FlowMapperService>(configMap)

            // Create and start the subscription (using the message config)
            verify(flowMapperEventMediatorFactory).create(
                any(),
                eq(messagingConfig),
                any(),
            )
            verifyIsInError<FlowMapperService>()
        }
    }

}
