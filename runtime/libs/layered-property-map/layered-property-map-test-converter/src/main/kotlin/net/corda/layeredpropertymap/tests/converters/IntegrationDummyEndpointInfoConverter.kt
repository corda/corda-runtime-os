package net.corda.layeredpropertymap.tests.converters

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import org.osgi.service.component.annotations.Component

@Component(service = [CustomPropertyConverter::class])
class IntegrationDummyEndpointInfoConverter: CustomPropertyConverter<IntegrationDummyEndpointInfo> {
    companion object {
        private const val URL_KEY = "url"
        private const val PROTOCOL_VERSION_KEY = "protocolVersion"
    }

    override val type: Class<IntegrationDummyEndpointInfo>
        get() = IntegrationDummyEndpointInfo::class.java

    override fun convert(context: ConversionContext): IntegrationDummyEndpointInfo {
        return IntegrationDummyEndpointInfo(
            context.value(URL_KEY)
                ?: throw IllegalArgumentException("$URL_KEY cannot be null."),
            context.value(PROTOCOL_VERSION_KEY)?.toInt()
                ?: throw IllegalArgumentException("$PROTOCOL_VERSION_KEY cannot be null.")
        )
    }
}