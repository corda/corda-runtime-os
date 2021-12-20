package net.corda.membership.impl.read.subscription

import net.corda.data.membership.SignedMemberInfo
import net.corda.lifecycle.Lifecycle
import net.corda.membership.config.MembershipConfig
import net.corda.membership.config.MembershipConfigConstants
import net.corda.membership.config.groupName
import net.corda.membership.config.kafkaConfig
import net.corda.membership.config.memberList
import net.corda.membership.config.persistence
import net.corda.membership.config.topicName
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.impl.read.processor.MemberListProcessor
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas
import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Implementations of this interface manage the subscriptions required for the membership group read service component.
 */
interface MembershipGroupReadSubscriptions : Lifecycle {

    /**
     * Start all subscriptions.
     */
    fun start(config: MembershipConfig)

    /**
     * Default implementation.
     */
    class Impl(
        private val subscriptionFactory: SubscriptionFactory,
        private val groupReadCache: MembershipGroupReadCache
    ) : MembershipGroupReadSubscriptions {
        override val isRunning: Boolean
            get() = subscriptions.all { it?.isRunning ?: false }

        private var memberListSubscription: CompactedSubscription<String, SignedMemberInfo>? = null

        private val subscriptions
            get() = listOf(
                memberListSubscription
            )

        override fun start(config: MembershipConfig) {
            startMemberListSubscription(config)
        }

        override fun start() {
            throw CordaRuntimeException("Must provide membership configuration in order to start the subscriptions.")
        }

        override fun stop() = subscriptions.forEach { it?.stop() }

        /**
         * Start the member list subscription. Group and topic names are provided by configuration but will be set to
         * default values if configuration leaves out these names.
         */
        private fun startMemberListSubscription(config: MembershipConfig) {
            val memberListKafkaConfig = config.kafkaConfig?.persistence?.memberList
            val memberListGroupName = memberListKafkaConfig?.groupName
                ?: MembershipConfigConstants.Kafka.Persistence.MemberList.DEFAULT_GROUP_NAME
            val memberListTopicName = memberListKafkaConfig?.topicName ?: Schemas.Membership.MEMBER_LIST_TOPIC

            memberListSubscription = subscriptionFactory.createCompactedSubscription(
                SubscriptionConfig(memberListGroupName, memberListTopicName),
                MemberListProcessor(groupReadCache.memberListCache)
            ).also {
                it.start()
            }
        }

    }
}