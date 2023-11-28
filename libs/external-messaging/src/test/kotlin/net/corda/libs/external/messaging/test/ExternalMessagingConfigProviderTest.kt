package net.corda.libs.external.messaging.test

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.external.messaging.ExternalMessagingConfigDefaults
import net.corda.libs.external.messaging.ExternalMessagingConfigProviderImpl
import net.corda.libs.external.messaging.entities.InactiveResponseType
import net.corda.schema.configuration.ExternalMessagingConfig.EXTERNAL_MESSAGING_ACTIVE
import net.corda.schema.configuration.ExternalMessagingConfig.EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE
import net.corda.schema.configuration.ExternalMessagingConfig.EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExternalMessagingConfigProviderTest {

    @Test
    fun `ensure the smart config is processed correctly`() {
        val expectedConfigDefaults =
            ExternalMessagingConfigDefaults("user.123.a.b.c.receive", false, InactiveResponseType.IGNORE)

        val defaultConfig = expectedConfigDefaults.toSmartConfig()

        val configProvider = ExternalMessagingConfigProviderImpl(defaultConfig)
        assertThat(configProvider.getDefaults()).isEqualTo(expectedConfigDefaults)
    }

    private fun ExternalMessagingConfigDefaults.toSmartConfig() =
        SmartConfigFactory.createWithoutSecurityServices()
            .create(toConfig())

    private fun ExternalMessagingConfigDefaults.toConfig() =
        ConfigFactory.parseMap(
            mapOf<String, Any>(
                EXTERNAL_MESSAGING_RECEIVE_TOPIC_PATTERN to receiveTopicPattern,
                EXTERNAL_MESSAGING_ACTIVE to isActive,
                EXTERNAL_MESSAGING_INTERACTIVE_RESPONSE_TYPE to inactiveResponseType.toString()
            )
        )
}
