package net.corda.interop.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.identity.HoldingIdentity
import net.corda.data.interop.InteropMessage
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.data.p2p.app.AppMessage
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.interop.InteropService
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// To run the test outside Intellij:
// ./gradlew :components:interop:interop-service:integrationTest
// ./gradlew :components:interop:interop-service:testOSGi
@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InteropServiceIntegrationTest {

    private companion object {
        const val clientId = "clientId"
    }

    private var setup = false

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var configService: ConfigurationReadService

    @InjectService(timeout = 4000)
    lateinit var cordaAvroSerializationFactory: CordaAvroSerializationFactory

    @InjectService(timeout = 4000)
    lateinit var interopService: InteropService

    private val bootConfig = SmartConfigImpl.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
        .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))

    private val schemaVersion = ConfigurationSchemaVersion(1, 0)

    @BeforeEach
    fun setup() {
        if (!setup) {
            setup = true
            val publisher = publisherFactory.createPublisher(PublisherConfig(clientId), bootConfig)
            setupConfig(publisher)
        }
    }
    @Test
    fun `verify messages from p2p-in are send back to p2p-out`() {
        interopService.start()
        val testId = "test1"
        val payload = "{\"method\": \"org.corda.interop/platform/tokens/v1.0/reserve-tokens\", \"parameters\" : [ { \"abc\" : { \"type\" : \"string\", \"value\" : \"USD\" } } ] }"
        val publisher = publisherFactory.createPublisher(PublisherConfig(testId), bootConfig)
        val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }
        val interopMessageSerializer = cordaAvroSerializationFactory.createAvroSerializer<InteropMessage> { }

        // Test config updates don't break Interop Service
        republishConfig(publisher)

        val identity = HoldingIdentity(testId, testId)
        val flowHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1), "", "", "interop")
        val version = listOf(1)
        val sessionEvent = SessionEvent(
            MessageDirection.INBOUND, Instant.now(), testId, 1, identity, identity, 0, listOf(), SessionInit(
                testId,
                version,
                testId,
                null,
                KeyValuePairList(emptyList()),
                KeyValuePairList(emptyList()),
                ByteBuffer.wrap("".toByteArray())
            )
        )
        val interopMessage = InteropMessage("InteropMessageID-01", payload)

        val interopRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(
                AuthenticatedMessage(
                    flowHeader, ByteBuffer.wrap(interopMessageSerializer.serialize(interopMessage))
                )
            )
        )

        val nonInteropFlowHeader = AuthenticatedMessageHeader(identity, identity, Instant.ofEpochMilli(1), "", "", "flowSession")
        val nonInteropSessionRecord = Record(
            P2P_IN_TOPIC, testId, AppMessage(
                AuthenticatedMessage(
                    nonInteropFlowHeader, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))
                )
            )
        )

        publisher.publish(listOf(interopRecord, interopRecord, nonInteropSessionRecord))

        //TODO 2 extra messages are send automatically by service as seed messages, remove +2 once seed message is removed
        val expectedOutputMessages = 2 + 2
        val mapperLatch = CountDownLatch(expectedOutputMessages)
        val testProcessor = P2POutMessageCounter(testId, mapperLatch, expectedOutputMessages)
        val p2pOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("$testId-p2p-out", P2P_OUT_TOPIC),
            testProcessor,
            bootConfig,
            null
        )
        p2pOutSub.start()
        assertTrue(mapperLatch.await(45, TimeUnit.SECONDS),
            "Fewer P2P output messages were observed (${testProcessor.recordCount}) than expected ($expectedOutputMessages).")
        assertEquals(expectedOutputMessages, testProcessor.recordCount, "More P2P output messages were observed that expected.")
        p2pOutSub.close()

        interopService.stop()
    }

    @Test
    fun `verify messages in memebrship-info topic and hosted-identities topic`() {
        val clearMemberInfoSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("member-info", Schemas.Membership.MEMBER_LIST_TOPIC),
            ClearMemberInfoProcessor(),
            bootConfig,
            null
        )
        val clearHostedIdsSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("hosted-identities", Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC),
            ClearHostedIdentitiesProcessor(),
            bootConfig,
            null
        )
        val latch = CountDownLatch(2)
        clearMemberInfoSub.start()
        clearHostedIdsSub.start()
        latch.await(10, TimeUnit.SECONDS)
        clearMemberInfoSub.close()
        clearHostedIdsSub.close()

        interopService.start()
        val memberExpectedOutputMessages = 4
        val memberMapperLatch = CountDownLatch(memberExpectedOutputMessages)
        val memberProcessor = MemberInfoMessageCounter(memberMapperLatch, memberExpectedOutputMessages)
        val memberOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("member-info", Schemas.Membership.MEMBER_LIST_TOPIC),
            memberProcessor,
            bootConfig,
            null
        )
        memberOutSub.start()
        assertTrue(memberMapperLatch.await(30, TimeUnit.SECONDS),
            "Fewer membership messages were observed (${memberProcessor.recordCount}) than expected ($memberExpectedOutputMessages).")
        assertEquals(memberExpectedOutputMessages, memberProcessor.recordCount, "More membership messages were observed that expected.")
        memberOutSub.close()

        val hostedIdsExpected = 2
        val hostedIdMapperLatch = CountDownLatch(hostedIdsExpected)
        val hostedIdProcessor = HostedIdentitiesMessageCounter(hostedIdMapperLatch, hostedIdsExpected)
        val hostedIdOutSub = subscriptionFactory.createDurableSubscription(
            SubscriptionConfig("hosted-identities", Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC),
            hostedIdProcessor,
            bootConfig,
            null
        )
        hostedIdOutSub.start()
        assertTrue(hostedIdMapperLatch.await(30, TimeUnit.SECONDS),
            "Fewer hosted identities messages were observed (${hostedIdProcessor.recordCount}) than expected ($hostedIdsExpected).")
        assertEquals(hostedIdsExpected, hostedIdProcessor.recordCount, "More hosted identities messages were observed that expected.")
        hostedIdOutSub.close()

        interopService.stop()
    }

    private fun setupConfig(publisher: Publisher) {
        publishConfig(publisher)
        configService.start()
        configService.bootstrapConfig(bootConfig)
    }

    private fun publishConfig(publisher: Publisher) {
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, messagingConf, 0, schemaVersion)
                )
            )
        )
    }

    private fun republishConfig(publisher: Publisher) {
        // Wait for the initial config to be available
        val configLatch = CountDownLatch(1)
        configService.registerForUpdates { _, _ ->
            configLatch.countDown()
        }
        configLatch.await()

        publishConfig(publisher)
    }

    private val messagingConf = """
            componentVersion="5.1"
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
            }
      """
}

class P2POutMessageCounter(
    private val key: String,
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, AppMessage> {
    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java
    var recordCount = 0
    override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
        for (event in events) {
            println("Event : $event")
            if (event.key == key) {
                recordCount++
                if (recordCount > expectedRecordCount) {
                    fail("Expected record count exceeded in events processed for this key")
                }
                latch.countDown()
            }
        }
        return emptyList()
    }
}

class MemberInfoMessageCounter(
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, PersistentMemberInfo> {
    override val keyClass = String::class.java
    override val valueClass = PersistentMemberInfo::class.java
    var recordCount = 0
    override fun onNext(events: List<Record<String, PersistentMemberInfo>>): List<Record<*, *>> {
        for (event in events) {
            println("Member Info : $event")
            recordCount++
            if (recordCount > expectedRecordCount) {
                fail("Expected record count exceeded in events processed for this key")
            }
            latch.countDown()
        }
        return emptyList()
    }
}
class HostedIdentitiesMessageCounter(
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, HostedIdentityEntry> {
    override val keyClass = String::class.java
    override val valueClass = HostedIdentityEntry::class.java
    var recordCount = 0
    override fun onNext(events: List<Record<String, HostedIdentityEntry>>): List<Record<*, *>> {
        for (event in events) {
            println("Hosted Identity : $event")
            recordCount++
            if (recordCount > expectedRecordCount) {
                fail("Expected record count exceeded in events processed for this key")
            }
            latch.countDown()
        }
        return emptyList()
    }
}
class ClearHostedIdentitiesProcessor : DurableProcessor<String, HostedIdentityEntry> {
    override val keyClass = String::class.java
    override val valueClass = HostedIdentityEntry::class.java
    override fun onNext(events: List<Record<String, HostedIdentityEntry>>): List<Record<*, *>> {
        for (event in events) {
            println("Hosted identity cleared : $event")
        }
        return emptyList()
    }
}
class ClearMemberInfoProcessor : DurableProcessor<String, PersistentMemberInfo> {
    override val keyClass = String::class.java
    override val valueClass = PersistentMemberInfo::class.java
    override fun onNext(events: List<Record<String, PersistentMemberInfo>>): List<Record<*, *>> {
        for (event in events) {
            println("Member info cleared : $event")
        }
        return emptyList()
    }
}