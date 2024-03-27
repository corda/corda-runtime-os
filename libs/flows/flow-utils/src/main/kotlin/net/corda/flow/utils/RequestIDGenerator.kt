package net.corda.flow.utils

import net.corda.v5.application.serialization.SerializationService

/**
 * These methods allow us to generate a unique request ID for a persistence operation which is deterministic based on details
 * of the flow making the persistence request, and also parameters passed to the persistence request. This is used to ensure
 * that events replayed from the flow engine won't be reprocessed on the consumer side.
 *
 * There is also a facility to "improve" an existing request ID if we learn more about the nature of the entities after it
 * was generated. This improvement is only possible at the consumer side of the request, and also only possible under
 * circumstances where we can extract a unique deterministic entity id from the entities in the request. Where an entity
 * id is available it is more reliable that the request parameters, because it's the source of uniqueness enforced at the
 * db level already. This is particularly important where parameters passed to the request are nondeterministic which typically
 * occurs where one of them is a timestamp.
 *
 * The format of the request ID is <flowId>-<suspendCount>-<entity_based_hash> where entity_based_hash is either based on
 * the parameters to the request or the entity's known unique id.
 */
object RequestIDGenerator {
    // Choose a hyphen as a delimiter because it is not a Base64 character
    private const val DELIMITER = "-"

    /**
     * Called by the client side, this generates a request id which is unique to the parameters passed to the request
     * to persist.
     */
    fun requestId(
        requestParameters: Any,
        flowId: String,
        suspendCount: Int,
        serializationService: SerializationService,
        hashFunction: (ByteArray) -> String
    ): String {
        val hashedInput = hashFunction(serializationService.serialize(requestParameters).bytes)
        return listOf(flowId, suspendCount, hashedInput)
            .joinToString(DELIMITER)
    }

    /**
     * Called by the consumer side, this allows the replacement of the request ID hash component (which was previously
     * based on the parameter to the persistence request) with something more deterministic if that is known.
     */
    fun replaceRequestIdHash(
        requestId: String,
        deterministic: Any,
        serializationService: SerializationService,
        hashFunction: (ByteArray) -> String
    ): String {
        val hashedInput = hashFunction(serializationService.serialize(deterministic).bytes)
        return requestId.substringBeforeLast(DELIMITER) + DELIMITER + hashedInput
    }
}
