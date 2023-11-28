package net.corda.libs.external.messaging

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.schema.configuration.ExternalMessagingConfig

class ExternalMessagingConfigProviderImpl(private val externalMessagingConfigDefaults: ExternalMessagingConfigDefaults) :
    ExternalMessagingConfigProvider {

    private companion object {
        fun toExternalMessagingConfigDefaults(defaultConfig: SmartConfig): ExternalMessagingConfigDefaults =
            ExternalMessagingConfigDefaults(
                defaultConfig.getString(ExternalMessagingConfig.EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN),
                defaultConfig.getBoolean(ExternalMessagingConfig.EXTERNAL_MESSAGING_ACTIVE),
                defaultConfig.getEnum(
                    InactiveResponseType::class.java,
                    ExternalMessagingConfig.EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE
                )
            )
    }

    constructor(defaultConfig: SmartConfig) :
        this(toExternalMessagingConfigDefaults(defaultConfig))

    override fun getDefaults(): ExternalMessagingConfigDefaults {
        return externalMessagingConfigDefaults
    }
}
