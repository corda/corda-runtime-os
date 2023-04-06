package net.corda.libs.external.messaging.test

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.external.messaging.ExternalMessagingConfigDefaults
import net.corda.libs.external.messaging.ExternalMessagingConfigProviderImpl
import net.corda.libs.external.messaging.entities.InactiveResponseType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExternalMessagingConfigProviderTest {

    @Test
    fun `ensure the smart config is processed correctly`() {
        val expectedConfigDefaults =
            ExternalMessagingConfigDefaults("user.123.a.b.c.receive", false, InactiveResponseType.IGNORE)

        val defaultConfig = genRouteDefaultConfig(expectedConfigDefaults)

        val configProvider = ExternalMessagingConfigProviderImpl(defaultConfig)
        assertThat(configProvider.getDefaults()).isEqualTo(expectedConfigDefaults)
    }

    private fun genRouteDefaultConfig(expectedConfigDefaults: ExternalMessagingConfigDefaults) =
        SmartConfigFactory.createWithoutSecurityServices()
            .create(ConfigFactory.parseString(genRouteDefaultConfigJsonStr(expectedConfigDefaults)))

    private fun genRouteDefaultConfigJsonStr(expectedConfigDefaults: ExternalMessagingConfigDefaults) =
        """
            {
                "routeDefaults": {
                    "receiveTopicPattern": "${expectedConfigDefaults.receiveTopicPattern}",
                    "active": "${expectedConfigDefaults.isActive}",
                    "inactiveResponseType": "${expectedConfigDefaults.inactiveResponseType}"
                }
            }
        """.trimIndent()
}