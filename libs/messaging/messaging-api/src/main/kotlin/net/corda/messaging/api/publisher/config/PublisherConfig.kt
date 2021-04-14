package net.corda.messaging.api.publisher.config

/**
 * Class to store the required params to create a Publisher.
 * [clientId] is an identifier of the source of a record.
 * [instanceId] required for transactional publishing where order is importany. Leave null for idempotent producer
 * [topic] to publish to
 */
data class PublisherConfig (val clientId: String,
                            val instanceId: Int?,
                            val eventTopic: String)