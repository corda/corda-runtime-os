package net.corda.membership.impl.serialization

import net.corda.membership.impl.EndpointInfoImpl
import net.corda.v5.application.node.EndpointInfo
import net.corda.v5.application.node.StringObjectConverter
import net.corda.v5.cipher.suite.KeyEncodingService

class EndpointInfoStringConverter(val encodingService: KeyEncodingService): StringObjectConverter<EndpointInfo> {
    override val keyEncodingService: KeyEncodingService get() = encodingService
    override fun convert(stringProperties: Map<String, String>): EndpointInfo {
        return EndpointInfoImpl(
            stringProperties["connectionURL"]
                ?: throw IllegalStateException("Object creation failed, url property was null."),
            stringProperties["protocolVersion"]?.toInt()
                ?: throw IllegalStateException("Object creation failed, protocolVersion property was null.")
        )
    }
}