package net.corda.messaging.api.publisher.config

/**
 * Class to store the required params to create a Publisher.
 * @property clientId Is an identifier of the source of a record.
 * @property transactional True to publish records as a transaction, false to send asynchronously.
 */
data class PublisherConfig (
    val clientId: String,
    val transactional: Boolean = true,
)
