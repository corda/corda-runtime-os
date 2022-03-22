package net.corda.messaging.api.publisher.config

/**
 * Class to store the required params to create a Publisher.
 * @property clientId Is an identifier of the source of a record.
 * @property instanceId Unique id required for publishing records as a transactions.
 * If it is a transaction the publisher will wait until all records previously sent by this publisher have completed their send.
 * If this is null then transactions are not used. Defaults to null.
 */
data class PublisherConfig (val clientId: String)
