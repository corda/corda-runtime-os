package net.corda.applications.workers.smoketest

import net.corda.e2etest.utilities.configWithDefaultsNode
import net.corda.e2etest.utilities.getConfig
import net.corda.e2etest.utilities.sourceConfigNode
import net.corda.e2etest.utilities.toJsonString
import net.corda.e2etest.utilities.updateConfig
import net.corda.e2etest.utilities.waitForConfigurationChange
import net.corda.schema.configuration.ConfigKeys.RECONCILIATION_CONFIG
import net.corda.schema.configuration.ReconciliationConfig
import net.corda.schema.configuration.ReconciliationConfig.RECONCILIATION_CONFIG_INTERVAL_MS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(40)
@Suppress("FunctionName")
class ConfigTests {

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
        updateConfig(mapOf(RECONCILIATION_CONFIG_INTERVAL_MS to newValue).toJsonString(), RECONCILIATION_CONFIG)
        waitForConfigurationChange(RECONCILIATION_CONFIG, RECONCILIATION_CONFIG_INTERVAL_MS, newValue.toString(), false)

        try {
            val updatedValue = getReconConfigValue(defaults = false)
            assertThat(updatedValue).isEqualTo(newValue)
        } finally {
            // Be a good neighbour and rollback the configuration change back to what it was
            updateConfig(mapOf(RECONCILIATION_CONFIG_INTERVAL_MS to initialValue).toJsonString(), RECONCILIATION_CONFIG)
            waitForConfigurationChange(RECONCILIATION_CONFIG, RECONCILIATION_CONFIG_INTERVAL_MS, initialValue.toString(), false)
        }
    }

    private fun getReconConfigValue(defaults: Boolean): Int {
        val currentConfig = getConfig(RECONCILIATION_CONFIG)
        val configJSON = if (defaults) { currentConfig.configWithDefaultsNode() } else { currentConfig.sourceConfigNode() }
        return configJSON[RECONCILIATION_CONFIG_INTERVAL_MS].asInt()
    }
}
