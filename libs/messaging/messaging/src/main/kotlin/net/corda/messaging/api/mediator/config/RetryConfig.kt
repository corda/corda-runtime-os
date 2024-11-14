package net.corda.messaging.api.mediator.config

import net.corda.messaging.api.mediator.MediatorMessage

/**
 * Config used to setup retry events in the mediator.
 * These are used to retry transient errors that occur.
 * @property retryTopic The topic to send retry events to
 * @property buildRetryRequest lambda used to generate the retry requests
 */
data class RetryConfig<K: Any>(
    val retryTopic: String,
    val buildRetryRequest: ((K, MediatorMessage<Any>) -> List<MediatorMessage<Any>>)? = null,
)
