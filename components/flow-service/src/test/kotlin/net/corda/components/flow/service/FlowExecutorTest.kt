package net.corda.components.flow.service

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.components.flow.service.stubs.StateAndEventSubscriptionStub
import net.corda.components.sandbox.service.SandboxService
import net.corda.configuration.read.ConfigKeys.Companion.BOOTSTRAP_KEY
import net.corda.configuration.read.ConfigKeys.Companion.FLOW_KEY
import net.corda.configuration.read.ConfigKeys.Companion.MESSAGING_KEY
import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowManager
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
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
        private const val GROUP_NAME_KEY = "consumer.group"
        private const val TOPIC_KEY = "consumer.topic"
        private const val INSTANCE_ID_KEY = "instance-id"
    }

    private val coordinatorFactory: LifecycleCoordinatorFactory = LifecycleCoordinatorFactoryImpl()
    private val bootstrapConfig: SmartConfig = SmartConfigImpl(ConfigFactory.empty().withValue(INSTANCE_ID_KEY, ConfigValueFactory
        .fromAnyRef(1)))
    private val flowConfig: SmartConfig = SmartConfigImpl(ConfigFactory.empty().withValue(TOPIC_KEY, ConfigValueFactory.fromAnyRef
        ("Topic1"))
        .withValue(GROUP_NAME_KEY, ConfigValueFactory.fromAnyRef("Group1")))
    private val messagingConfig: SmartConfig = SmartConfigImpl(ConfigFactory.empty())
    private val subscriptionFactory: SubscriptionFactory = mock()
    private val flowManager: FlowManager = mock()
    private val sandboxService: SandboxService = mock()

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

        val configs = mapOf(
            MESSAGING_KEY to messagingConfig,
            FLOW_KEY to flowConfig,
            BOOTSTRAP_KEY to bootstrapConfig
        )

        val flowExecutor =
            FlowExecutor(coordinatorFactory, configs, subscriptionFactory, flowManager, sandboxService)

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
