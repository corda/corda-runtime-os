package net.corda.session.mapper.service.integration

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionCounterpartyInfoRequest
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.identity.HoldingIdentity
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManagerFactory
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Config.CONFIG_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_CLEANUP_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_IN
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_OUT
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_START
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.Schemas.ScheduledTask
import net.corda.schema.configuration.BootConfig.BOOT_MAX_ALLOWED_MSG_SIZE
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.ConfigKeys.STATE_MANAGER_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import net.corda.session.mapper.service.FlowMapperService
import net.corda.session.mapper.service.state.StateMetadataKeys
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.util.eventually
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowMapperServiceIntegrationTest {

    private companion object {
        const val clientId = "clientId"
    }

    private var setup = false

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    // no secrets needed -> empty config
    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var flowEventMediatorFactory: TestFlowEventMediatorFactory

    @InjectService(timeout = 4000)
    lateinit var configService: ConfigurationReadService

    @InjectService(timeout = 4000)
    lateinit var flowMapperService: FlowMapperService

    @InjectService(timeout = 4000)
    lateinit var locallyHostedIdentityService: LocallyHostedIdentitiesService

    @InjectService(timeout = 4000)
    lateinit var stateManagerFactory: StateManagerFactory

    private val messagingConfig = SmartConfigImpl.empty()
        .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
        .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
        .withValue(MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(100000000))

    private val stateManagerConfig = SmartConfigImpl.empty()

    private val schemaVersion = ConfigurationSchemaVersion(1, 0)

    private val aliceHoldingIdentity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group1")
    private val bobHoldingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group1")
    private val charlieHoldingIdentity = HoldingIdentity("CN=Charlie, O=Charlie Corp, L=LDN, C=GB", "group1")


    @BeforeEach
    fun setup() {
        TestStateManagerFactoryImpl.clear()
        if (!setup) {
            setup = true
            val publisher = publisherFactory.createPublisher(PublisherConfig(clientId), messagingConfig)
            setupConfig(publisher)
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val publicKey = keyPairGenerator.generateKeyPair().public
            val alice = aliceHoldingIdentity.toCorda()
            val bob = bobHoldingIdentity.toCorda()
            val aliceIdentityInfo = IdentityInfo(alice, listOf(), publicKey)
            val bobIdentityInfo = IdentityInfo(bob, listOf(), publicKey)

            (locallyHostedIdentityService as DummyLocallyHostedIdentitiesService).setIdentityInfo(
                alice, aliceIdentityInfo
            )
            (locallyHostedIdentityService as DummyLocallyHostedIdentitiesService).setIdentityInfo(
                bob, bobIdentityInfo
            )

            flowMapperService.start()
            locallyHostedIdentityService.start()
        }
    }

    @Test
    fun `Test first session event outbound sets up flow mapper state, verify subsequent messages received are passed to flow event topic`
                () {
        val testId = "test1"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), messagingConfig)

        //send 2 session init, 1 is duplicate
        val sessionDataAndInitEvent = Record<Any, Any>(
            FLOW_MAPPER_SESSION_OUT, testId, FlowMapperEvent(
                buildSessionEvent(
                    MessageDirection.OUTBOUND, testId, 1, SessionData(ByteBuffer.wrap("bytes".toByteArray()), SessionInit(
                        testId, testId, emptyKeyValuePairList(), emptyKeyValuePairList()
                    )),
                    initiatedIdentity = charlieHoldingIdentity,
                    contextSessionProps = emptyKeyValuePairList()
                )
            )
        )

        publisher.publish(listOf(sessionDataAndInitEvent, sessionDataAndInitEvent))

        //validate p2p out only receives 1 init
        val p2pLatch = CountDownLatch(1)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            TestP2POutProcessor(testId, p2pLatch, 1), messagingConfig, null
        )
        p2pOutSub.start()
        assertTrue(p2pLatch.await(20, TimeUnit.SECONDS))
        p2pOutSub.close()

        //send data back
        val sessionDataEvent = Record<Any, Any>(
            FLOW_MAPPER_SESSION_IN, testId, FlowMapperEvent(
                buildSessionEvent(
                    MessageDirection.INBOUND,
                    testId,
                    2,
                    SessionData(ByteBuffer.wrap("".toByteArray()), null),
                    contextSessionProps = emptyKeyValuePairList()
                )
            )
        )
        publisher.publish(listOf(sessionDataEvent))

        //validate flow event topic
        val flowEventLatch = CountDownLatch(1)
        val testProcessor = TestFlowMessageProcessor(flowEventLatch, 1, SessionEvent::class.java)
        val flowEventMediator = flowEventMediatorFactory.create(
            messagingConfig,
            stateManagerConfig,
            testProcessor,
        )

        flowEventMediator.start()
        assertTrue(flowEventLatch.await(5, TimeUnit.SECONDS))
        flowEventMediator.close()
    }

    @Test
    fun testStartRPCDuplicatesAndCleanup() {
        val testId = "test2"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), messagingConfig)

        //2 startRPCRecord, 1 duplicate
        val identity = HoldingIdentity(testId, testId)
        val context = FlowStartContext(
            FlowKey("clientId", identity),
            FlowInitiatorType.RPC,
            "clientId",
            identity,
            "cpi id",
            identity,
            "class name",
            "args",
            emptyKeyValuePairList(),
            Instant.now()
        )

        val startRPCEvent = Record<Any, Any>(
            FLOW_MAPPER_START, testId, FlowMapperEvent(
                StartFlow(
                    context,
                    ""
                )
            )
        )
        publisher.publish(listOf(startRPCEvent, startRPCEvent))

        //flow event subscription to validate outputs
        val flowEventLatch = CountDownLatch(2)
        val testProcessor = TestFlowMessageProcessor(flowEventLatch, 2, StartFlow::class.java)
        val flowEventMediator = flowEventMediatorFactory.create(
            messagingConfig,
            stateManagerConfig,
            testProcessor,
        )

        flowEventMediator.start()

        //assert duplicate start rpc didn't get processed (and also give Execute cleanup time to run)
        assertFalse(flowEventLatch.await(3, TimeUnit.SECONDS))
        assertThat(flowEventLatch.count).isEqualTo(1)

        // Manually publish an execute cleanup event. Temporary until the full solution has been integrated.
        val executeCleanup = Record<Any, Any>(
            FLOW_MAPPER_CLEANUP_TOPIC,
            testId,
            ExecuteCleanup(listOf(testId))
        )
        publisher.publish(listOf(executeCleanup))

        //send same key start rpc again
        publisher.publish(listOf(startRPCEvent))

        //validate went through and not a duplicate
        assertThat(
            flowEventLatch.await(
                5,
                TimeUnit.SECONDS
            )
        ).withFailMessage("latch was ${flowEventLatch.count}").isTrue

        flowEventMediator.close()
    }

    @Test
    fun testNoStateForMapper() {
        val testId = "test3"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), messagingConfig)

        //send data, no state
        val sessionDataEvent = Record<Any, Any>(
            FLOW_MAPPER_SESSION_OUT, testId, FlowMapperEvent(
                buildSessionEvent(
                    MessageDirection.OUTBOUND,
                    testId,
                    1,
                    SessionData(ByteBuffer.wrap("".toByteArray()), null),
                    contextSessionProps = emptyKeyValuePairList()
                )
            )
        )
        publisher.publish(listOf(sessionDataEvent))

        //validate p2p out doesn't have any records
        val p2pLatch = CountDownLatch(1)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            TestP2POutProcessor(testId, p2pLatch, 0), messagingConfig, null
        )
        p2pOutSub.start()
        assertFalse(p2pLatch.await(3, TimeUnit.SECONDS))
        p2pOutSub.close()
    }

    @Test
    fun `flow mapper still works after config update`() {
        val testId = "test4"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), messagingConfig)

        //send 2 session init, 1 is duplicate
        val sessionInitEvent = Record<Any, Any>(
            FLOW_MAPPER_SESSION_OUT, testId, FlowMapperEvent(
                buildSessionEvent(
                    MessageDirection.OUTBOUND, testId, 1, SessionCounterpartyInfoRequest(SessionInit(
                        testId, testId, emptyKeyValuePairList(), emptyKeyValuePairList()
                    )),
                    initiatedIdentity = charlieHoldingIdentity,
                    contextSessionProps = emptyKeyValuePairList()
                )
            )
        )

        publisher.publish(listOf(sessionInitEvent))

        //validate p2p receives the init
        val p2pLatch = CountDownLatch(1)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            TestP2POutProcessor(testId, p2pLatch, 1), messagingConfig, null
        )
        p2pOutSub.start()
        assertTrue(p2pLatch.await(10, TimeUnit.SECONDS))
        p2pOutSub.close()

        // Publish the config again to trigger the update logic
        publishConfig(publisher)

        //send data back
        val sessionDataEvent = Record<Any, Any>(
            FLOW_MAPPER_SESSION_IN, testId, FlowMapperEvent(
                buildSessionEvent(
                    MessageDirection.INBOUND,
                    testId,
                    2,
                    SessionData(ByteBuffer.wrap("".toByteArray()), null),
                    initiatedIdentity = charlieHoldingIdentity,
                    contextSessionProps = emptyKeyValuePairList()
                )
            )
        )
        publisher.publish(listOf(sessionDataEvent))

        //validate flow event topic
        val flowEventLatch = CountDownLatch(1)
        val testProcessor = TestFlowMessageProcessor(flowEventLatch, 1, SessionEvent::class.java)
        val flowEventMediator = flowEventMediatorFactory.create(
            messagingConfig,
            stateManagerConfig,
            testProcessor,
        )

        flowEventMediator.start()
        assertTrue(flowEventLatch.await(5, TimeUnit.SECONDS))
        flowEventMediator.close()
    }


    @Test
    fun `when the flow mapper receives an inbound session message for a non-existent session, an error is returned`() {
        val testId = "test5"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), messagingConfig)

        //send data, no state
        val sessionDataEvent = Record<Any, Any>(
            FLOW_MAPPER_SESSION_IN, testId, FlowMapperEvent(
                buildSessionEvent(
                    MessageDirection.INBOUND,
                    testId,
                    2,
                    SessionData(ByteBuffer.wrap("".toByteArray()), null),
                    initiatingIdentity = aliceHoldingIdentity,
                    initiatedIdentity = bobHoldingIdentity,
                    contextSessionProps = emptyKeyValuePairList()
                )
            )
        )

        val mapperLatch = CountDownLatch(2) // The initial message and the error back.
        val records = mutableListOf<SessionEvent>()
        val mapperSub = subscriptionFactory.createPubSubSubscription(
            SubscriptionConfig("$testId-mapper", FLOW_MAPPER_SESSION_IN),
            TestFlowMapperProcessor(mapperLatch, records),
            messagingConfig
        )
        mapperSub.start()
        try {
            publisher.publish(listOf(sessionDataEvent))
            assertTrue(mapperLatch.await(10, TimeUnit.SECONDS))
        } finally {
            mapperSub.close()
        }
        val requiredSessionID = "$testId-INITIATED"
        val event = records.find {
            it.sessionId == requiredSessionID
        } ?: throw AssertionError("No event matching required session ID $requiredSessionID was found")
        assertThat(event.messageDirection).isEqualTo(MessageDirection.INBOUND)
        assertThat(event.sessionId).isEqualTo("$testId-INITIATED")
        assertThat(event.payload).isInstanceOf(SessionError::class.java)
    }

    @Test
    fun `mapper state cleanup correctly cleans up old states`() {

        // Create a state in the state manager. Note the modified time has to be further in the past than the configured
        // flow processing time.
        val stateKey = "foo"
        val config = SmartConfigImpl.empty()
        val stateManager = stateManagerFactory.create(config)
        stateManager.create(listOf(
            State(
                stateKey,
                byteArrayOf(),
                metadata = Metadata(mapOf(StateMetadataKeys.FLOW_MAPPER_STATUS to FlowMapperStateType.CLOSING.toString())),
                modifiedTime = Instant.now().minusSeconds(20)
            )
        ))

        // Publish a scheduled task trigger.
        val testId = "test6"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), messagingConfig)
        publisher.publish(listOf(
            Record(
                ScheduledTask.SCHEDULED_TASK_TOPIC_MAPPER_PROCESSOR,
                "foo",
                ScheduledTaskTrigger(ScheduledTask.SCHEDULED_TASK_NAME_MAPPER_CLEANUP, Instant.now()))
        ))

        eventually(duration = Duration.ofMinutes(1)) {
            val states = stateManager.get(listOf(stateKey))
            assertThat(states[stateKey]).isNull()
        }
    }

    private fun setupConfig(publisher: Publisher) {
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseString(bootConf))
        publishConfig(publisher)
        configService.start()
        configService.bootstrapConfig(bootConfig)
    }

    private fun publishConfig(publisher: Publisher) {
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC,
                    FLOW_CONFIG,
                    Configuration(flowConf, flowConf, 0, schemaVersion)
                )
            )
        )
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC,
                    MESSAGING_CONFIG,
                    Configuration(messagingConf, messagingConf, 0, schemaVersion)
                )
            )
        )
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC,
                    STATE_MANAGER_CONFIG,
                    Configuration(stateManagerConf, stateManagerConf, 0, schemaVersion)
                )
            )
        )
    }

    private val bootConf = """
        $INSTANCE_ID=1
        $BUS_TYPE = INMEMORY
        $BOOT_MAX_ALLOWED_MSG_SIZE = 1000000

    """

    private val flowConf = """
            session {
                p2pTTL = 500000
            }
            processing {
                cleanupTime = 10000
                poolSize = 1
            }
        """

    private val messagingConf = """
            componentVersion="5.1"
            maxAllowedMessageSize = 1000000
            subscription {
                consumer {
                    close.timeout = 6000
                    poll.timeout = 6000
                    thread.stop.timeout = 6000
                    processor.retries = 3
                    subscribe.retries = 3
                    commit.retries = 3
                }
                producer {
                    close.timeout = 6000
                }
                pollTimeout = 100
            }
      """

    private val stateManagerConf = """
        
    """.trimIndent()
}
