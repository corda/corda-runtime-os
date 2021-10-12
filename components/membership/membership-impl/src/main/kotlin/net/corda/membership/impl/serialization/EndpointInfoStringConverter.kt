package net.corda.membership.impl.serialization

import net.corda.membership.impl.EndpointInfoImpl
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.KeyValueStore
import net.corda.v5.membership.identity.StringObjectConverter

class EndpointInfoStringConverter: StringObjectConverter<EndpointInfo> {
    override fun convert(stringProperties: KeyValueStore, clazz: Class<out EndpointInfo>): EndpointInfo {
        return EndpointInfoImpl(
            stringProperties["connectionURL"]
                ?: throw IllegalStateException("Object creation failed, url property was null."),
            stringProperties["protocolVersion"]?.toInt()
                ?: throw IllegalStateException("Object creation failed, protocolVersion property was null.")
        )
    }
}