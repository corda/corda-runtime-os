package net.corda.membership.impl.read.subscription

import net.corda.data.membership.PersistentMemberInfo
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Implementations of this interface manage the subscriptions required for the membership group read service component.
 */
interface MembershipGroupReadSubscriptions : Lifecycle {

    /**
     * Start all subscriptions.
     */
    fun start(config: SmartConfig)

    /**
     * Default implementation.
     */
    class Impl(
        private val subscriptionFactory: SubscriptionFactory,
        private val groupReadCache: MembershipGroupReadCache,
        private val memberInfoFactory: MemberInfoFactory
    ) : MembershipGroupReadSubscriptions {

        companion object {
            const val CONSUMER_GROUP = "MEMBERSHIP_GROUP_READER"
        }

        private var memberListSubscription: CompactedSubscription<String, PersistentMemberInfo>? = null

        private val subscriptions
            get() = listOf(
                memberListSubscription
            )

        override val isRunning: Boolean
            get() = subscriptions.all { it?.isRunning ?: false }

        override fun start(config: SmartConfig) {
            startMemberListSubscription(config)
        }

        override fun start() {
            throw CordaRuntimeException("Must provide membership configuration in order to start the subscriptions.")
        }

        override fun stop() = subscriptions.forEach { it?.close() }

        /**
         * Start the member list subscription.
         */
        private fun startMemberListSubscription(config: SmartConfig) {
            memberListSubscription?.close()

            val subscriptionConfig = SubscriptionConfig(
                CONSUMER_GROUP,
                MEMBER_LIST_TOPIC
            )

            val processor = MemberListProcessor(groupReadCache, memberInfoFactory)

            subscriptionFactory.createCompactedSubscription(
                subscriptionConfig,
                processor,
                config
            ).apply {
                start()
                memberListSubscription = this
            }
        }

    }
}
