package net.corda.applications.workers.smoketest

import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.config.configWithDefaultsNode
import net.corda.e2etest.utilities.config.getConfig
import net.corda.e2etest.utilities.config.managedConfig
import net.corda.e2etest.utilities.config.sourceConfigNode
import net.corda.e2etest.utilities.config.waitForConfigurationChange
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ReconciliationConfig
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CONFIG_INTERVAL_MS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import java.time.Duration

@Order(40)
@Suppress("FunctionName")
class ConfigTests : ClusterReadiness by ClusterReadinessChecker() {

    @BeforeEach
    fun setupEach() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))
    }

    @Test
    fun `get config includes defaults`() {
        val defaultedConfigValues = getConfig(RECONCILIATION_CONFIG).configWithDefaultsNode()
        ReconciliationConfig::class.java.declaredFields
            .map { it.get(null) }
            .filterIsInstance<String>()
            .also { assertThat(it.isNotEmpty()) }
            .forEach {
                assertThat(defaultedConfigValues[it].asText())
                    .isNotBlank
                    .withFailMessage("missing $it configuration key")
            }
    }

    @Test
    fun `can update config`() {
        val initialValue = getReconConfigValue(defaults = true)
        val newValue = (initialValue * 2)

        managedConfig { configManager ->
            configManager.load(RECONCILIATION_CONFIG, RECONCILIATION_CONFIG_INTERVAL_MS, newValue).apply()
            waitForConfigurationChange(RECONCILIATION_CONFIG, RECONCILIATION_CONFIG_INTERVAL_MS, newValue.toString(), false)

            val updatedValue = getReconConfigValue(defaults = false)
            assertThat(updatedValue).isEqualTo(newValue)
        }
    }

    private fun getReconConfigValue(defaults: Boolean): Int {
        val currentConfig = getConfig(RECONCILIATION_CONFIG)
        val configJSON = if (defaults) { currentConfig.configWithDefaultsNode() } else { currentConfig.sourceConfigNode() }
        return configJSON[RECONCILIATION_CONFIG_INTERVAL_MS].asInt()
    }
}
