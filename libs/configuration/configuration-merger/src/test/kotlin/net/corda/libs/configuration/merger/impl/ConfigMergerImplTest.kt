package net.corda.libs.configuration.merger.impl

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messagebus.api.configuration.BusConfigMerger
import net.corda.messagebus.api.configuration.StateManagerConfigMerger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

class ConfigMergerImplTest {
    private val busConfigMerger = mock<BusConfigMerger>()
    private val stateManagerConfigMerger = mock<StateManagerConfigMerger>()
    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    private val merger = ConfigMergerImpl(busConfigMerger, stateManagerConfigMerger)

    @Test
    fun `merger correctly merges messaging config with state manager config using fallback`() {
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
        val stateManagerConfig = smartConfigFactory.create(
            ConfigFactory.parseMap(
                mapOf(
                    "stateManager.database.user" to "aaa",
                    "stateManager.database.pass" to "bbb",
                )
            )
        )

        whenever(busConfigMerger.getMessagingConfig(eq(bootConfig), eq(messagingConfig))).thenReturn(mergedMessagingConfig)
        whenever(stateManagerConfigMerger.getStateManagerConfig(eq(bootConfig), eq(messagingConfig))).thenReturn(stateManagerConfig)

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)

        assertThat(result.getString("boot.param.a")).isEqualTo("111")
        assertThat(result.getString("boot.param.b")).isEqualTo("222")
        assertThat(result.getString("stateManager.database.user")).isEqualTo("aaa")
        assertThat(result.getString("stateManager.database.pass")).isEqualTo("bbb")
    }

    @Test
    fun `merger correctly merges messaging config with empty state manager config using fallback`() {
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
        val stateManagerConfig = smartConfigFactory.create(ConfigFactory.empty())

        whenever(busConfigMerger.getMessagingConfig(eq(bootConfig), eq(messagingConfig))).thenReturn(mergedMessagingConfig)
        whenever(stateManagerConfigMerger.getStateManagerConfig(eq(bootConfig), eq(messagingConfig))).thenReturn(stateManagerConfig)

        val result = merger.getMessagingConfig(bootConfig, messagingConfig)

        assertThat(result.getString("boot.param.a")).isEqualTo("111")
        assertThat(result.getString("boot.param.b")).isEqualTo("222")
    }

}