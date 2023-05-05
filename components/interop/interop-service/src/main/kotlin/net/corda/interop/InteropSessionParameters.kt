package net.corda.interop

import net.corda.data.KeyValuePairList

class InteropSessionParameters(
    val facadeId: String,
    val facadeMethod: String,
    val interopGroupId: String
) {
    companion object {
        private const val INTEROP_GROUP_ID = "INTEROP_GROUP_ID"
        private const val INTEROP_FACADE_ID = "INTEROP_FACADE_ID"
        private const val INTEROP_FACADE_METHOD = "INTEROP_FACADE_METHOD"

        @Suppress("ThrowsCount")
        fun fromContextUserProperties(properties: KeyValuePairList): InteropSessionParameters {
            val facadeId = properties.items.find { it.key == INTEROP_FACADE_ID }?.value ?: throw InteropProcessorException(
                "Message without facadeId.", null)

            val facadeMethod = properties.items.find { it.key == INTEROP_FACADE_METHOD }?.value ?: throw InteropProcessorException(
                "Message without facadeMethod.", null)

            val interopGroupId = properties.items.find { it.key == INTEROP_GROUP_ID }?.value ?: throw InteropProcessorException(
                "Message without interop group id.", null)

            return InteropSessionParameters(facadeId, facadeMethod, interopGroupId)
        }
    }
}
