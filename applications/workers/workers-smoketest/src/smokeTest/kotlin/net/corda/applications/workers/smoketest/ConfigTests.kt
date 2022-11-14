package net.corda.applications.workers.smoketest

import net.corda.schema.configuration.ConfigKeys
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
            .filter { it.name != "INSTANCE" }
            .map { it.get(ConfigKeys) as String }
            .forEach {
                assertThat(defaultedConfigValues[it].asText())
                    .isNotBlank
                    .withFailMessage("missing $it configuration key")
            }
    }

    @Test
    fun `can update config`() {
        var currentValue = getCurrentReconConfigValue()
        val newValue = (currentValue * 1.5).toInt()
        updateConfig(mapOf(RECONCILIATION_CONFIG_INTERVAL_MS to newValue).toJsonString(), RECONCILIATION_CONFIG)

        try {
            currentValue = getCurrentReconConfigValue()
            assertThat(currentValue).isEqualTo(newValue)
        } finally {
            // Be a good neighbour and rollback the configuration change back to what it was
            updateConfig(mapOf(RECONCILIATION_CONFIG_INTERVAL_MS to currentValue).toJsonString(), RECONCILIATION_CONFIG)
        }
    }

    private fun getCurrentReconConfigValue(): Int {
        val currentConfig = getConfig(RECONCILIATION_CONFIG)
        val currentConfigJSON = currentConfig.sourceConfigNode()
        println("currentConfig: ${currentConfigJSON.toPrettyString()}")
        return currentConfigJSON[RECONCILIATION_CONFIG_INTERVAL_MS].asInt()
    }
}
