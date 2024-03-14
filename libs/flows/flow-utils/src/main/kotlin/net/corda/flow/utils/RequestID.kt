package net.corda.flow.utils

import net.corda.v5.application.serialization.SerializationService

/**
 * This class allows us to generate a unique request ID which is deterministic based on the parameters passed.
 *
 * This is used to ensure that events replayed from the flow engine won't be reprocessed on the consumer side.
 */
object RequestID {
    // - is not a Base64 character
    private const val DELIMITER = "-"

    fun generateRequestId(
        parameters: Any,
        flowId: String,
        suspendCount: Int,
        serializationService: SerializationService,
        hashFunction: (ByteArray) -> String
    ): String {
        val hashedInput = deterministicBytesID(serializationService, parameters, hashFunction)

        return listOf(flowId, suspendCount, hashedInput)
            .joinToString(DELIMITER)
    }

    fun replaceHash(
        requestId: String,
        deterministic: Any,
        serializationService: SerializationService,
        hashFunction: (ByteArray) -> String
    ): String {
        val hashedInput = hashFunction(serializationService.serialize(deterministic).bytes)
        return requestId.substringBeforeLast(DELIMITER) + DELIMITER + hashedInput
    }

    private fun deterministicBytesID(
        serializationService: SerializationService,
        parameters: Any,
        hashFunction: (ByteArray) -> String
    ) = hashFunction(serializationService.serialize(parameters).bytes)
}
