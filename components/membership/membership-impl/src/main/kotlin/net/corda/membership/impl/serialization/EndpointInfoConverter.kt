package net.corda.membership.impl.serialization

import net.corda.membership.impl.EndpointInfoImpl
import net.corda.membership.impl.MemberContextImpl
import net.corda.v5.membership.identity.EndpointInfo
import net.corda.v5.membership.identity.parser.CustomConversionContext
import net.corda.v5.membership.identity.parser.CustomObjectConverter
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [EndpointInfo] object.
 */
@Component(service = [CustomObjectConverter::class])
class EndpointInfoConverter: CustomObjectConverter {
    companion object {
        private const val URL_KEY = "connectionURL"
        private const val PROTOCOL_VERSION_KEY = "protocolVersion"
    }

    override val type: Class<*>
        get() = EndpointInfo::class.java

    override fun convert(context: CustomConversionContext): EndpointInfo {
        return when(context.storeClass) {
            MemberContextImpl::class.java -> {
                /*return*/ EndpointInfoImpl(
                    context.store.get(URL_KEY)
                            ?: throw IllegalArgumentException("$URL_KEY cannot be null."),
                    context.store.get(PROTOCOL_VERSION_KEY)?.toInt()
                            ?: throw IllegalArgumentException("$PROTOCOL_VERSION_KEY cannot be null.")
                )
            }
            else -> throw IllegalArgumentException("Unknown class '${context.storeClass.name}'.")
        }
    }
}