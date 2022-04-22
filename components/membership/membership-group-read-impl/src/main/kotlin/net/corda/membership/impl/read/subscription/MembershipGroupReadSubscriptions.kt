package net.corda.membership.impl.read.subscription

import net.corda.data.membership.PersistentMemberInfo
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.schema.messaging.INSTANCE_ID
import net.corda.lifecycle.Lifecycle
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Membership.Companion.MEMBER_LIST_TOPIC
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
        private val layeredPropertyMapFactory: LayeredPropertyMapFactory
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

        override fun stop() = subscriptions.forEach { it?.stop() }

        /**
         * Start the member list subscription.
         */
        private fun startMemberListSubscription(config: SmartConfig) {
            val instanceId = when {
                config.hasPathOrNull(INSTANCE_ID) -> {
                    when {
                        config.getIsNull(INSTANCE_ID) -> null
                        else -> config.getInt(INSTANCE_ID)
                    }
                }
                else -> null
            }

            val subscriptionConfig = SubscriptionConfig(
                CONSUMER_GROUP,
                MEMBER_LIST_TOPIC,
                instanceId
            )

            val processor = MemberListProcessor(groupReadCache, layeredPropertyMapFactory)

            subscriptionFactory.createCompactedSubscription(
                subscriptionConfig,
                processor,
                config
            ).apply {
                start()
                memberListSubscription?.close()
                memberListSubscription = this
            }
        }

    }
}
