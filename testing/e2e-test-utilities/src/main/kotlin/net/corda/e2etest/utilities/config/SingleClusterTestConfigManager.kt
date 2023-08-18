package net.corda.e2etest.utilities.config

import com.fasterxml.jackson.databind.JsonNode
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.expand
import net.corda.e2etest.utilities.flatten
import net.corda.e2etest.utilities.toJsonString
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class SingleClusterTestConfigManager(
    private val clusterInfo: ClusterInfo = DEFAULT_CLUSTER
) : TestConfigManager {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val GET_CONFIG_TIMEOUT_SECONDS = 30L
    }

    private val flattenedOverrides: MutableMap<String, Map<String, Any?>> = ConcurrentHashMap()
    private var originalConfigs: MutableMap<String, JsonNode> = ConcurrentHashMap()

    override fun load(section: String, props: Map<String, Any?>): TestConfigManager {
        props.forEach { (k, v) -> load(section, k, v) }
        return this
    }

    override fun load(section: String, prop: String, value: Any?): TestConfigManager {
        logger.info(
            "Loading test config \"$value\" for property \"$prop\" in section \"$section\" for cluster \"${clusterInfo.name}\" " +
                    "into TestConfigManager."
        )

        // If the input value is a map, flatten it to a standardised form for merging with previously loaded configs.
        val propsAsFlattenedTree = mutableMapOf<String, Any?>().also {
            if (value is Map<*, *>) {
                value.flatten(it, prop)
            } else {
                it[prop] = value
            }
        }

        // Combine with previously loaded overrides with the new properties taking precedence.
        flattenedOverrides.compute(section) { _, v ->
            (v ?: emptyMap()) + propsAsFlattenedTree
        }

        return this
    }

    override fun apply(): TestConfigManager {
        flattenedOverrides.filterValues {
            it.isNotEmpty()
        }.forEach { (section, configOverride) ->
            val currentConfig = getConfig(section)
            // Store original config for later revert.
            originalConfigs.computeIfAbsent(section) { currentConfig }

            val (previousVersion, previousSourceConfig) = with(currentConfig) {
                version to sourceConfig
            }

            val newConfig = configOverride.expand().toJsonString()
            logger.info(
                "Updating from config \"$previousSourceConfig\" to \"$newConfig\" for section \"$section\" on " +
                        "cluster \"${clusterInfo.name}\"."
            )

            if(newConfig != previousSourceConfig) {
                updateConfig(newConfig, section)

                eventually(duration = Duration.ofSeconds(GET_CONFIG_TIMEOUT_SECONDS)) {
                    with(getConfig(section)) {
                        assertThat(version).isNotEqualTo(previousVersion)
                        assertThat(sourceConfig).isEqualTo(newConfig)
                    }
                }
            }
        }
        return this
    }

    override fun revert(): TestConfigManager {
        originalConfigs.forEach { (section, originalConfig) ->
            val (previousVersion, previousSourceConfig) = with(getConfig(section)) {
                version to sourceConfig
            }
            val preTestConfig = originalConfig.sourceConfig.ifBlank { "{}" }

            logger.info(
                "Reverting test config for section \"$section\" from \"$previousSourceConfig\" to \"$preTestConfig\" " +
                    "for cluster \"${clusterInfo.name}\"."
            )
            updateConfig(preTestConfig, section)

            eventually(duration = Duration.ofSeconds(GET_CONFIG_TIMEOUT_SECONDS)) {
                val newConfig = getConfig(section)
                assertThat(newConfig.version).isNotEqualTo(previousVersion)
                assertThat(newConfig.sourceConfig).isEqualTo(preTestConfig)
            }
        }
        return this
    }

    /**
     * Revert back to the original config on close.
     */
    override fun close() {
        revert()
    }

    private fun getConfig(section: String) = clusterInfo.getConfig(section)

    private fun updateConfig(config: String, section: String) = clusterInfo.updateConfig(config, section)

    private val JsonNode.version: Int
        get() = get("version").intValue()

    private val JsonNode.sourceConfig: String
        get() = get("sourceConfig").textValue()
}