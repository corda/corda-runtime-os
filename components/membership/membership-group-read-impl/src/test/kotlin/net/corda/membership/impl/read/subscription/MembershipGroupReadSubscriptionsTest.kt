package net.corda.membership.impl.read.subscription

import net.corda.data.membership.SignedMemberInfo
import net.corda.membership.config.MembershipConfig
import net.corda.membership.config.MembershipConfigConstants
import net.corda.membership.config.MembershipKafkaConfig
import net.corda.membership.config.MembershipKafkaMemberListConfig
import net.corda.membership.config.MembershipKafkaPersistenceConfig
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.cipher.suite.KeyEncodingService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipGroupReadSubscriptionsTest {
    lateinit var membershipGroupReadSubscriptions: MembershipGroupReadSubscriptions

    private val testTopicName = "TEST_TOPIC"
    private val testGroupName = "TEST_GROUP"

    private val memberListConfig = mock<MembershipKafkaMemberListConfig>().apply {
        doReturn(testTopicName).whenever(this)[eq(MembershipConfigConstants.Kafka.TOPIC_NAME_KEY)]
        doReturn(testGroupName).whenever(this)[eq(MembershipConfigConstants.Kafka.GROUP_NAME_KEY)]
    }
    private val persistenceConfig = mock<MembershipKafkaPersistenceConfig>().apply {
        doReturn(memberListConfig)
            .whenever(this)[eq(MembershipConfigConstants.Kafka.Persistence.MemberList.CONFIG_KEY)]
    }
    private val kafkaConfig = mock<MembershipKafkaConfig>().apply {
        doReturn(persistenceConfig)
            .whenever(this)[eq(MembershipConfigConstants.Kafka.Persistence.CONFIG_KEY)]
    }
    private val config = mock<MembershipConfig>().apply {
        doReturn(kafkaConfig)
            .whenever(this)[eq(MembershipConfigConstants.Kafka.CONFIG_KEY)]
    }

    private var memberListSubscriptionStarted = false
    private val memberListSubscription =
        mock<CompactedSubscription<String, SignedMemberInfo>>().apply {
            doAnswer { memberListSubscriptionStarted = true }.whenever(this).start()
            doAnswer { memberListSubscriptionStarted = false }.whenever(this).stop()
            doAnswer { memberListSubscriptionStarted }.whenever(this).isRunning
        }

    private val subscriptionFactory = mock<SubscriptionFactory>().apply {
        doReturn(memberListSubscription).whenever(this)
            .createCompactedSubscription(
                any(),
                any<CompactedProcessor<*, *>>(),
                any()
            )
    }

    private val memberListCache: MemberListCache = mock()
    private val groupReadCache = mock<MembershipGroupReadCache>().apply {
        doReturn(this@MembershipGroupReadSubscriptionsTest.memberListCache).whenever(this).memberListCache
    }
    private val keyEncodingService: KeyEncodingService = mock()

    @BeforeEach
    fun setUp() {
        membershipGroupReadSubscriptions = MembershipGroupReadSubscriptions.Impl(
            subscriptionFactory,
            groupReadCache,
            keyEncodingService
        )
    }

    @Test
    fun `Subscriptions cannot start without configuration`() {
        assertThrows<CordaRuntimeException> { membershipGroupReadSubscriptions.start() }
    }

    @Test
    fun `Subscriptions start with configuration`() {
        membershipGroupReadSubscriptions.start(config)

        verify(memberListSubscription).start()
    }

    @Test
    fun `Subscription service is running after starting and not running after stopping`() {
        assertFalse(membershipGroupReadSubscriptions.isRunning)
        membershipGroupReadSubscriptions.start(config)
        assertTrue(membershipGroupReadSubscriptions.isRunning)
        membershipGroupReadSubscriptions.stop()
        assertFalse(membershipGroupReadSubscriptions.isRunning)
    }

    @Test
    fun `Topic name and group name default if member list sub config missing those configurations`() {
        memberListConfig.apply {
            doReturn(null).whenever(this)[eq(MembershipConfigConstants.Kafka.TOPIC_NAME_KEY)]
            doReturn(null).whenever(this)[eq(MembershipConfigConstants.Kafka.GROUP_NAME_KEY)]
        }
        commonMemberListSubConfigTest()
    }

    @Test
    fun `Topic name and group name default if persistence config missing member list config`() {
        persistenceConfig.apply {
            doReturn(null)
                .whenever(this)[eq(MembershipConfigConstants.Kafka.Persistence.MemberList.CONFIG_KEY)]
        }
        commonMemberListSubConfigTest()
    }

    @Test
    fun `Topic name and group name default if kafka config missing persistence config`() {
        kafkaConfig.apply {
            doReturn(null)
                .whenever(this)[eq(MembershipConfigConstants.Kafka.Persistence.CONFIG_KEY)]
        }
        commonMemberListSubConfigTest()
    }

    @Test
    fun `Topic name and group name default if membership config missing kafka config`() {
        config.apply {
            doReturn(null)
                .whenever(this)[eq(MembershipConfigConstants.Kafka.CONFIG_KEY)]
        }
        commonMemberListSubConfigTest()
    }

    @Test
    fun `Topic name and group name are taken from config if exists`() {
        commonMemberListSubConfigTest(false)
    }

    private fun commonMemberListSubConfigTest(defaultNames: Boolean = true) {
        lateinit var subConfig: SubscriptionConfig
        doAnswer {
            subConfig = it.arguments[0] as SubscriptionConfig
            memberListSubscription
        }
            .whenever(subscriptionFactory)
            .createCompactedSubscription(any(), any<CompactedProcessor<*, *>>(), any())

        membershipGroupReadSubscriptions.start(config)

        if (defaultNames) {
            assertEquals(Schemas.Membership.MEMBER_LIST_TOPIC, subConfig.eventTopic)
            assertEquals(MembershipConfigConstants.Kafka.Persistence.MemberList.DEFAULT_GROUP_NAME, subConfig.groupName)
        } else {
            assertEquals(testTopicName, subConfig.eventTopic)
            assertEquals(testGroupName, subConfig.groupName)
        }
    }
}
