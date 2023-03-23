package net.corda.interop.service.integration

import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.config.Configuration
import net.corda.data.config.ConfigurationSchemaVersion
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.db.messagebus.testkit.DBSetup
import net.corda.interop.InteropService
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Config.CONFIG_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import net.corda.schema.configuration.BootConfig.TOPIC_PREFIX
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, DBSetup::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InteropDataSetupIntegrationTest {

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
    lateinit var membershipGroupReaderProvider: MembershipGroupReaderProvider

    @InjectService(timeout = 4000)
    lateinit var interopService: InteropService

    private val bootConfig = SmartConfigImpl.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("INMEMORY"))
        .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
        .withValue(MessagingConfig.MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(100000000))

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
    fun `verify messages in membership-info topic and hosted-identities topic`() {
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
        val memberExpectedOutputMessages = 5
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
        //As this is a test of temporary code, relaxing check on getting more messages
        memberOutSub.close()

        val hostedIdsExpected = 4
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
        membershipGroupReaderProvider.start()
    }

    private fun publishConfig(publisher: Publisher) {
        val messagingConf = """
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
        publisher.publish(
            listOf(
                Record(
                    CONFIG_TOPIC, MESSAGING_CONFIG, Configuration(messagingConf, messagingConf, 0, schemaVersion)
                )
            )
        )
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