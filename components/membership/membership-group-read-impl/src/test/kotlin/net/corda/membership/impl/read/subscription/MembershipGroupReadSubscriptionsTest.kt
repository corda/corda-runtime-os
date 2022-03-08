package net.corda.membership.impl.read.subscription

import net.corda.data.membership.SignedMemberInfo
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.subscription.MembershipGroupReadSubscriptions.Impl.Companion.CONSUMER_GROUP
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.exceptions.CordaRuntimeException
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

    private val messageConfig = mock<SmartConfig>()
    private val config = mock<SmartConfig>().apply {
        doReturn(messageConfig).whenever(this).getConfig(eq(MESSAGING_CONFIG))
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

    @BeforeEach
    fun setUp() {
        membershipGroupReadSubscriptions = MembershipGroupReadSubscriptions.Impl(
            subscriptionFactory,
            groupReadCache,
            mock()
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
    fun `Topic name and group name are as expected`() {
        lateinit var subConfig: SubscriptionConfig
        doAnswer {
            subConfig = it.arguments[0] as SubscriptionConfig
            memberListSubscription
        }
            .whenever(subscriptionFactory)
            .createCompactedSubscription(any(), any<CompactedProcessor<*, *>>(), any())

        membershipGroupReadSubscriptions.start(config)

        assertEquals(Schemas.Membership.MEMBER_LIST_TOPIC, subConfig.eventTopic)
        assertEquals(CONSUMER_GROUP, subConfig.groupName)
    }
}
