package net.corda.messaging.config

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.constants.SubscriptionType
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Subscription.COMMIT_RETRIES
import net.corda.schema.configuration.MessagingConfig.Subscription.POLL_TIMEOUT
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_RETRIES
import net.corda.schema.configuration.MessagingConfig.Subscription.PROCESSOR_TIMEOUT
import net.corda.schema.configuration.MessagingConfig.Subscription.SUBSCRIBE_RETRIES
import net.corda.schema.configuration.MessagingConfig.Subscription.THREAD_STOP_TIMEOUT
import java.time.Duration

/**
 * Class to resolve subscription configuration for the messaging layer.
 */
internal data class ResolvedSubscriptionConfig(
    val subscriptionType: SubscriptionType,
    val topic: String,
    val group: String,
    val idCounter: Long,
    val instanceId: Int,
    val pollTimeout: Duration,
    val threadStopTimeout: Duration,
    val processorRetries: Int,
    val subscribeRetries: Int,
    val commitRetries: Int,
    val processorTimeout: Duration,
    val messageBusConfig: SmartConfig
) {
    companion object {

        /**
         * Merge the user configured values in [subscriptionConfig] with the [messagingConfig] and return a concrete class containing all
         * values used by the subscriptions.
         * @param subscriptionType Type of subscription.
         * @param subscriptionConfig User configurable values for a subscription.
         * @param messagingConfig Messaging smart config.
         * @param counter Client counter.
         * @return concrete class containing all config values used by a subscription.
         */
        fun merge(
            subscriptionType: SubscriptionType,
            subscriptionConfig: SubscriptionConfig,
            messagingConfig: SmartConfig,
            idCounter: Long
        ): ResolvedSubscriptionConfig {
            return ResolvedSubscriptionConfig(
                subscriptionType,
                subscriptionConfig.eventTopic,
                subscriptionConfig.groupName,
                idCounter,
                messagingConfig.getInt(INSTANCE_ID),
                Duration.ofMillis(messagingConfig.getLong(POLL_TIMEOUT)),
                Duration.ofMillis(messagingConfig.getLong(THREAD_STOP_TIMEOUT)),
                messagingConfig.getInt(PROCESSOR_RETRIES),
                messagingConfig.getInt(SUBSCRIBE_RETRIES),
                messagingConfig.getInt(COMMIT_RETRIES),
                Duration.ofMillis(messagingConfig.getLong(PROCESSOR_TIMEOUT)),
                messagingConfig
            )
        }
    }

    val clientId = "$subscriptionType-$group-$topic-$idCounter"
    val loggerName = clientId
    val lifecycleCoordinatorName = LifecycleCoordinatorName("$topic-$subscriptionType-$group", clientId)
}
