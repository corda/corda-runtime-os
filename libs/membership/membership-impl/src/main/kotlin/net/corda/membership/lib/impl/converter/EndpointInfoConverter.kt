package net.corda.membership.lib.impl.converter

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.membership.lib.impl.EndpointInfoImpl
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.membership.EndpointInfo
import org.osgi.service.component.annotations.Component

/**
 * Converter class, converting from String to [EndpointInfo] object.
 */
@Component(service = [CustomPropertyConverter::class])
class EndpointInfoConverter : CustomPropertyConverter<EndpointInfo> {
    companion object {
        private const val URL_KEY = "connectionURL"
        private const val PROTOCOL_VERSION_KEY = "protocolVersion"
    }

    override val type: Class<EndpointInfo>
        get() = EndpointInfo::class.java

    override fun convert(context: ConversionContext): EndpointInfo =
        EndpointInfoImpl(
            context.value(URL_KEY)
                ?: throw ValueNotFoundException("'$URL_KEY' is null or absent."),
            context.value(PROTOCOL_VERSION_KEY)?.toInt()
                ?: throw ValueNotFoundException("'$PROTOCOL_VERSION_KEY'is null or absent.")
        )
}