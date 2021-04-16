package net.corda.messaging.api.publisher.config

/**
 * Class to store the required params to create a Publisher.
 * @property clientId Is an identifier of the source of a record.
 * @property instanceId Required for transactional publishing where order is important.
 * @property topic To events publish to.
 */
data class PublisherConfig (val clientId: String,
                            val instanceId: Int,
                            val topic: String)