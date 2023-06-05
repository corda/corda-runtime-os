package net.corda.messaging.api.publisher.config

/**
 * Class to store the required params to create a Publisher.
 * @property clientId Is an identifier of the source of a record.
 * @property transactional True to publish records as a transaction, false to send asynchronously.
 * @property topic If there will only be one topic that this publisher sends to then it can put in the clientId for metrics
 */
data class PublisherConfig (
    val clientId: String,
    val transactional: Boolean = true,
    val topic: String = "general",
)
