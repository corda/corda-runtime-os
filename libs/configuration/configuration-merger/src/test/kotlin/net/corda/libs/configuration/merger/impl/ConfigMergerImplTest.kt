package net.corda.libs.configuration.merger.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class ConfigMergerImplTest {
    private val busConfigMerger = mock<BusConfigMerger>()
    private val configMerger = ConfigMergerImpl(busConfigMerger)
    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    @Test
    fun `merger correctly merges messaging config with boot config using messaging as fallback`() {
        val messagingConfig = SmartConfigImpl.empty()
        val bootConfig = smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "boot.param.a" to "111",
                    "boot.param.b" to "222",
                )
            )
        )

        val mergedMessagingConfig = bootConfig.withFallback(messagingConfig)
        whenever(busConfigMerger.getMessagingConfig(eq(bootConfig), eq(messagingConfig))).thenReturn(mergedMessagingConfig)

        val result = configMerger.getMessagingConfig(bootConfig, messagingConfig)

        assertThat(result.getString("boot.param.a")).isEqualTo("111")
        assertThat(result.getString("boot.param.b")).isEqualTo("222")
    }

    @Test
    fun `merger correctly merges boot config using existing config as fallback`() {
        val bootConfig = smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "section.key.param.a" to "X",
                )
            )
        )

        val messagingConfig = smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "key.param.b" to "222",
                )
            )
        )

        val result = configMerger.getConfig(bootConfig, "section", messagingConfig)
        assertThat(result.getString("key.param.a")).isEqualTo("X")
        assertThat(result.getString("key.param.b")).isEqualTo("222")
    }
}
