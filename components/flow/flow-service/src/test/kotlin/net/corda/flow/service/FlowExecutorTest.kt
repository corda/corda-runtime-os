package net.corda.flow.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.FlowEventProcessor
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.service.stubs.StateAndEventSubscriptionStub
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FlowExecutorTest {

    companion object {
        private const val GROUP_NAME_KEY = "manager.consumer.group"
    }

    private val coordinatorFactory: LifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl(LifecycleRegistryImpl())
    private val config: Map<String, SmartConfig> = mapOf(
        MESSAGING_CONFIG to SmartConfigFactory.create(ConfigFactory.empty()).create(
            ConfigFactory.empty().withValue(
                INSTANCE_ID, ConfigValueFactory
                    .fromAnyRef(1)
            ).withValue(GROUP_NAME_KEY, ConfigValueFactory.fromAnyRef("Group1"))
        ),
        BOOT_CONFIG to SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty()),
        FLOW_CONFIG to SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty())
    )
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val flowEventProcessor: FlowEventProcessor = mock()
    private val flowEventProcessorFactory = mock<FlowEventProcessorFactory>().apply {
        whenever(create(any())).thenReturn(flowEventProcessor)
    }

    @Test
    fun testFlowExecutor() {
        val startLatch = CountDownLatch(1)
        val stopLatch = CountDownLatch(1)
        val messageSubscription: StateAndEventSubscription<FlowKey, Checkpoint, FlowEvent> =
            StateAndEventSubscriptionStub(startLatch, stopLatch)

        doReturn(messageSubscription).whenever(subscriptionFactory).createStateAndEventSubscription<FlowKey, Checkpoint, FlowEvent>(
            any(),
            any(),
            any(),
            anyOrNull()
        )

        val flowExecutor = FlowExecutor(coordinatorFactory, config, subscriptionFactory, flowEventProcessorFactory)

        flowExecutor.start()
        assertTrue(startLatch.await(5, TimeUnit.SECONDS))
        flowExecutor.stop()
        assertTrue(stopLatch.await(5, TimeUnit.SECONDS))

        verify(subscriptionFactory, times(1)).createStateAndEventSubscription<FlowKey, Checkpoint, FlowEvent>(
            any(),
            any(),
            any(),
            anyOrNull()
        )
    }
}
