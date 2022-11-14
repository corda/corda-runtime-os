package net.corda.applications.workers.smoketest

import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MEMBERSHIP_CONFIG
import net.corda.schema.configuration.MembershipConfig.MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test

@Order(40)
@Suppress("FunctionName")
class ConfigTests {

    @Test
    fun `get config includes defaults`() {
        val defaultedConfigValues = getConfig(MEMBERSHIP_CONFIG).configWithDefaultsNode()
        MEMBERSHIP_CONFIG::class.java.declaredFields
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
        val newValue = (currentValue * 2)
        updateConfig(mapOf(MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES to newValue).toJsonString(), MEMBERSHIP_CONFIG)

        try {
            currentValue = getCurrentReconConfigValue()
            assertThat(currentValue).isEqualTo(newValue)
        } finally {
            // Be a good neighbour and rollback the configuration change back to what it was
            updateConfig(mapOf(MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES to currentValue).toJsonString(), MEMBERSHIP_CONFIG)
        }
    }

    private fun getCurrentReconConfigValue(): Int {
        val currentConfig = getConfig(MEMBERSHIP_CONFIG)
        val currentConfigJSON = currentConfig.sourceConfigNode()
        println("currentConfig: ${currentConfigJSON.toPrettyString()}")
        return currentConfigJSON[MAX_DURATION_BETWEEN_SYNC_REQUESTS_MINUTES].asInt()
    }
}
