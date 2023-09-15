package net.corda.libs.statemanager.impl.configuration

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.SmartConfigImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StateManagerConfigMergerImplTest {

    private val merger = StateManagerConfigMergerImpl()
    private val smartConfigFactory = SmartConfigFactory.createWithoutSecurityServices()

    @Test
    fun `merge boot config with empty config`() {
        val messagingConfig = SmartConfigImpl.empty()
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
            "stateManager.database.user" to "aaa",
            "stateManager.database.pass" to "bbb",
        )))
        val result = merger.getStateManagerConfig(bootConfig, messagingConfig)

        assertThat(result.getString("stateManager.database.user")).isEqualTo("aaa")
        assertThat(result.getString("stateManager.database.pass")).isEqualTo("bbb")
    }

    @Test
    fun `merge boot config overwrites existing messaging config`() {
        val messagingConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
            "stateManager.database.user" to "111",
            "stateManager.database.pass" to "222",
        )))
        val bootConfig = smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
            "stateManager.database.user" to "aaa",
            "stateManager.database.pass" to "bbb",
        )))
        val result = merger.getStateManagerConfig(bootConfig, messagingConfig)

        assertThat(result.getString("stateManager.database.user")).isEqualTo("aaa")
        assertThat(result.getString("stateManager.database.pass")).isEqualTo("bbb")
    }

}