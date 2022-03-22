package net.corda.messaging.config

import net.corda.libs.configuration.SmartConfig
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

internal data class ResolvedSubscriptionConfig(
    val subscriptionType: SubscriptionType,
    val topic: String,
    val group: String,
    val idCounter: Long,
    val instanceId: Int?,
    val pollTimeout: Duration,
    val threadStopTimeout: Duration,
    val processorRetries: Int,
    val subscribeRetries: Int,
    val commitRetries: Int,
    val processorTimeout: Duration,
    val busConfig: SmartConfig
) {
    companion object {
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
                if (messagingConfig.hasPath(INSTANCE_ID)) messagingConfig.getInt(INSTANCE_ID) else null,
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

    val loggerName = "$subscriptionType-$group-$topic"
    val clientId = "$subscriptionType-$group-$topic-$idCounter"
}
