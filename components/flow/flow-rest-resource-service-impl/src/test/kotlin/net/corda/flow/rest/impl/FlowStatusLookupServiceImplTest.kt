package net.corda.flow.rest.impl

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.rest.impl.utils.hash
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.Metadata
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant

class FlowStatusLookupServiceImplTest {
    private val subscriptionFactory = mock<SubscriptionFactory>()
    private val lifecycleTestContext = LifecycleTestContext()
    private val lifecycleCoordinator = lifecycleTestContext.lifecycleCoordinator
    private val lifecycleEventRegistration = mock<RegistrationHandle>()


    class TestSerializer<T> : CordaAvroSerializer<T> {
        override fun serialize(data: T): ByteArray = if (data is FlowStatus)
            data.toByteBuffer().array()
        else
            throw NotImplementedError()
    }
    class TestDeserializer<T> : CordaAvroDeserializer<T> {
        @Suppress("UNCHECKED_CAST")
        override fun deserialize(data: ByteArray): T? = FlowStatus.fromByteBuffer(ByteBuffer.wrap(data)) as? T

    }

    private val cordaSerializationFactory: CordaAvroSerializationFactory = mock {
        whenever(it.createAvroSerializer<Any>(any())).thenReturn(TestSerializer())
        whenever(it.createAvroDeserializer<Any>(any(), any())).thenReturn(TestDeserializer())
    }

    private val stateManagerFactory = mock<StateManagerFactory>()
    private val stateManager = getMockStateManager()

    private var eventHandler = mock<LifecycleEventHandler>()
    private val mockSubscription = mock<Subscription<Any, Any>>()
    private lateinit var flowStatusLookupService: FlowStatusLookupServiceImpl

    private val bootConfig = mock<SmartConfig>()
    private val messagingConfig = mock<SmartConfig> {
        whenever(it.getInt(INSTANCE_ID)).thenReturn(2)
        whenever(it.getConfig(ConfigKeys.STATE_MANAGER_CONFIG)).thenReturn(mock())
    }
    private val stateManagerConfig = mock<SmartConfig>()
    private val restConfig = mock<SmartConfig>()

    private val configs = mapOf(
        ConfigKeys.BOOT_CONFIG to bootConfig,
        ConfigKeys.MESSAGING_CONFIG to messagingConfig,
        ConfigKeys.STATE_MANAGER_CONFIG to stateManagerConfig,
        ConfigKeys.REST_CONFIG to restConfig
    )

    companion object {
        const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
        const val BOB_X500 = "CN=Bob, O=Bob Corp, L=LDN, C=GB"

        val FLOW_KEY_1 = FlowKey("a1", HoldingIdentity(ALICE_X500, "c1"))
        val FLOW_KEY_2 = FlowKey("a2", HoldingIdentity(BOB_X500, "c2"))
    }

    @BeforeEach
    fun setup() {
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)
        whenever(stateManagerFactory.create(any(), any())).thenReturn(stateManager)

        val resourceCaptor = argumentCaptor<() -> Subscription<Any, Any>>()
        whenever(lifecycleCoordinator.createManagedResource(any(), resourceCaptor.capture())).thenAnswer {
            resourceCaptor.firstValue.invoke()
            mockSubscription
        }

        flowStatusLookupService = FlowStatusLookupServiceImpl(
            subscriptionFactory,
            lifecycleTestContext.lifecycleCoordinatorFactory,
            cordaSerializationFactory,
            stateManagerFactory
        )

        eventHandler = lifecycleTestContext.getEventHandler()
    }

    @Test
    fun `Test start starts the lifecycle coordinator`() {
        flowStatusLookupService.start()
        verify(lifecycleCoordinator).start()
    }

    @Test
    fun `Test stop stops the lifecycle coordinator`() {
        flowStatusLookupService.stop()
        verify(lifecycleCoordinator).stop()
    }

    @Test
    fun `Test initialise creates new topic subscription and starts it`() {
        flowStatusLookupService.initialise(messagingConfig, stateManagerConfig, restConfig)

        val expectedSubscriptionCfg = SubscriptionConfig(
            "flow.status.subscription",
            Schemas.Flow.FLOW_STATUS_TOPIC
        )

        verify(subscriptionFactory, times(3)).createDurableSubscription(
            eq(expectedSubscriptionCfg),
            any<DurableFlowStatusProcessor>(),
            same(messagingConfig),
            same(null)
        )

        verify(mockSubscription, times(3)).start()
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
            flowStatusLookupService.initialise(messagingConfig, stateManagerConfig, restConfig)
        }

        private fun getStatusForFlowKey1() = flowStatusLookupService.getStatus(FLOW_KEY_1.id, FLOW_KEY_1.identity)
        private fun getStatusForFlowKey2() = flowStatusLookupService.getStatus(FLOW_KEY_2.id, FLOW_KEY_2.identity)
        private fun getStatusesPerIdentityForFlowKey1() = flowStatusLookupService.getStatusesPerIdentity(FLOW_KEY_1.identity)
        private fun getStatusesPerIdentityForFlowKey2() = flowStatusLookupService.getStatusesPerIdentity(FLOW_KEY_2.identity)

        @Nested
        inner class StateManagerIsEmpty {
            @Test
            fun `getStatus returns null`() = assertNull(getStatusForFlowKey1())
            @Test
            fun `getStatusesPerIdentity returns empty list`() = assertEquals(emptyList<FlowStatus>(), getStatusesPerIdentityForFlowKey2())
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
                        State(FLOW_KEY_1.hash(), serializer.serialize(flowStatus1)!!, metadata = Metadata(
                            mapOf(
                                HOLDING_IDENTITY_METADATA_KEY to FLOW_KEY_1.identity.toString(),
                                FLOW_STATUS_METADATA_KEY to flowStatus1.flowStatus.name
                            )
                        )),
                    )
                )
            }

            @Test
            fun `getStatus returns correct state`() = assertEquals(flowStatus1, getStatusForFlowKey1())

            @Test
            fun `getStatus returns null for key not in state manager`() = assertNull(getStatusForFlowKey2())

            @Test
            fun `getStatusesPerIdentity returns correct state`() = assertEquals(listOf(flowStatus1), getStatusesPerIdentityForFlowKey1())

            @Test
            fun `getStatusesPerIdentity returns empty list for key not in state manager`() =
                assertEquals(emptyList<FlowStatus>(), getStatusesPerIdentityForFlowKey2())

        }
    }
}
