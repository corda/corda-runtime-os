package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.ConfigKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class FlowStatusLookupServiceImplTest {
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private val lifecycleEventRegistration = mock<RegistrationHandle>()
    private val cordaSerializationFactory: CordaAvroSerializationFactory = object : CordaAvroSerializationFactory {
        override fun <T : Any> createAvroSerializer(onError: ((ByteArray) -> Unit)?): CordaAvroSerializer<T> {
            return object : CordaAvroSerializer<T> {
                override fun serialize(data: T): ByteArray? {
                    if (data is FlowStatus)
                        return data.toByteBuffer().array()
                    else
                        throw NotImplementedError()
                }
            }
        }

        override fun <T : Any> createAvroDeserializer(
            onError: (ByteArray) -> Unit,
            expectedClass: Class<T>
        ): CordaAvroDeserializer<T> {
            return object : CordaAvroDeserializer<T> {
                override fun deserialize(data: ByteArray): T? {
                    @Suppress("UNCHECKED_CAST")
                    return FlowStatus.fromByteBuffer(ByteBuffer.wrap(data)) as? T
                }

            }
        }

    }

    private val stateManagerFactory = mock<StateManagerFactory>()
    private val stateManager = getMockStateManager()

    private var eventHandler = mock<LifecycleEventHandler>()
    private val topicSubscription = mock<Subscription<FlowKey, FlowStatus>>()
    private lateinit var flowStatusCacheService: FlowStatusLookupServiceImpl
    private val config = mock<SmartConfig> {
        whenever(it.getInt(INSTANCE_ID)).thenReturn(2)
        whenever(it.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)).thenReturn(mock())
    }

    companion object {
        val FLOW_KEY_1 = FlowKey("a1", HoldingIdentity("b1", "c1"))
        val FLOW_KEY_2 = FlowKey("a2", HoldingIdentity("b1", "c1"))
    }

    @BeforeEach
    fun setup() {
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)

        whenever(subscriptionFactory.createDurableSubscription<FlowKey, FlowStatus>(any(), any(), any(), anyOrNull()))
            .thenReturn(topicSubscription)

        whenever(stateManagerFactory.create(any(), any())).thenReturn(stateManager)

        flowStatusCacheService = FlowStatusLookupServiceImpl(
            subscriptionFactory,
            lifecycleTestContext.lifecycleCoordinatorFactory,
            cordaSerializationFactory,
            stateManagerFactory
        )

        eventHandler = lifecycleTestContext.getEventHandler()
    }

    @Test
    fun `Test start starts the lifecycle coordinator`() {
        flowStatusCacheService.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `Test stop stops the lifecycle coordinator`() {
        flowStatusCacheService.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `Test initialise creates new topic subscription and starts it`() {
        flowStatusCacheService.initialise(config)

        val expectedSubscriptionCfg = SubscriptionConfig(
            "flow_status_subscription",
            Schemas.Flow.FLOW_STATUS_TOPIC
        )

        verify(subscriptionFactory).createDurableSubscription(
            eq(expectedSubscriptionCfg),
            any<DurableFlowStatusProcessor>(),
            same(config),
            same(null)
        )

        verify(topicSubscription).start()
    }

    @Test
    fun `Test initialise closes any existing topic subscription`() {
        flowStatusCacheService.initialise(config)
        // second time around we close the existing subscription
        flowStatusCacheService.initialise(config)
        verify(topicSubscription).close()
    }

    @Test
    fun `Test on start event component status up is signaled`() {
        eventHandler.processEvent(StartEvent(), lifecycleCoordinator)
        verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
    }

    @Nested
    inner class AfterInitialise {

        @BeforeEach
        fun prepareFlowStatusCacheService() {
            flowStatusCacheService.initialise(config)
        }

        private fun getStatusForFlowKey1() = flowStatusCacheService.getStatus(FLOW_KEY_1.id, FLOW_KEY_1.identity)
        private fun getStatusForFlowKey2() = flowStatusCacheService.getStatus(FLOW_KEY_2.id, FLOW_KEY_2.identity)

        @Nested
        inner class StateManagerIsEmpty {
            @Test
            fun `getStatus returns null`() = assertNull(getStatusForFlowKey1())
        }

        @Nested
        inner class StateManagerWithContent {

            private val flowStatus1 = FlowStatus(
                FLOW_KEY_1,
                FlowInitiatorType.RPC,
                FLOW_KEY_1.id,
                "FlowClassName",
                FlowStates.START_REQUESTED,
                null,
                null,
                null,
                Instant.EPOCH,
                Instant.EPOCH
            )

            @BeforeEach
            fun addContent() {
                val serializer = cordaSerializationFactory.createAvroSerializer<FlowStatus> {}

                stateManager.create(
                    listOf(
                        State(FLOW_KEY_1.toString(), serializer.serialize(flowStatus1)!!),
                    )
                )
            }

            @Test
            fun `getStatus returns correct state`() = assertEquals(flowStatus1, getStatusForFlowKey1())

            @Test
            fun `getStatus returns null for key not in state manager`() = assertNull(getStatusForFlowKey2())
        }
    }
}
