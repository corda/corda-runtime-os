package net.corda.e2etest.utilities.config

import com.fasterxml.jackson.databind.JsonNode
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.toJsonString
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class SingleClusterTestConfigManager(
    private val clusterInfo: ClusterInfo = DEFAULT_CLUSTER
) : TestConfigManager {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val flattenedOverrides: MutableMap<String, Map<String, Any?>> = ConcurrentHashMap()
    private var originalConfigs: MutableMap<String, JsonNode> = ConcurrentHashMap()

    override fun load(section: String, props: Map<String, Any?>): TestConfigManager {
        props.forEach { (k, v) -> load(section, k, v) }
        return this
    }

    override fun load(section: String, prop: String, value: Any?): TestConfigManager {
        logger.info("Loading test config $value for property $prop in section $section for cluster ${clusterInfo.name}.")

        // If the input value is a map, flatten it to a standardised form for merging with previously loaded configs.
        val propsAsFlattenedTree = mutableMapOf<String, Any?>().also {
            if (value is Map<*, *>) {
                flatten(value, it, prop)
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

            val newConfig = toJsonString(configOverride)
            logger.info("Applying config $newConfig for section $section on cluster ${clusterInfo.name}.")

            if(newConfig != previousSourceConfig) {
                updateConfig(newConfig, section)

                eventually {
                    with(getConfig(section)) {
                        Assertions.assertThat(version).isNotEqualTo(previousVersion)
                        Assertions.assertThat(sourceConfig).isNotEqualTo(previousSourceConfig)
                    }
                }
            }
        }
        return this
    }

    override fun revert(): TestConfigManager {
        originalConfigs.forEach { (section, originalConfig) ->
            val previousVersion = getConfig(section).version
            val preTestConfig = originalConfig.sourceConfig

            logger.info("Reverting test config for section $section to $preTestConfig for cluster ${clusterInfo.name}.")
            updateConfig(preTestConfig.ifBlank { "{}" }, section)

            eventually {
                val newConfig = getConfig(section)
                Assertions.assertThat(newConfig.version).isNotEqualTo(previousVersion)
                Assertions.assertThat(newConfig.sourceConfig).isEqualTo(preTestConfig)
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

    private fun flatten(
        map: Map<*, *>,
        targetMap: MutableMap<String, Any?>,
        prefix: String
    ) {
        map.forEach { (k, v) ->
            if (v is Map<*, *>) {
                flatten(v, targetMap, "$prefix.$k")
            } else {
                targetMap["$prefix.$k"] = v
            }
        }
    }

    private fun toJsonString(source: Map<String, Any?>): String {
        return mutableMapOf<String, Any?>().also { output ->
            source.forEach { (k, v) ->
                var targetMap: MutableMap<String, Any?> = output
                val splitKey = k.split('.')
                splitKey.dropLast(1).forEach {
                    if (targetMap.contains(it)) {
                        @Suppress("unchecked_cast")
                        targetMap = targetMap[it] as MutableMap<String, Any?>
                    } else {
                        val newMap = mutableMapOf<String, Any?>()
                        targetMap[it] = newMap
                        targetMap = newMap
                    }
                }
                targetMap[splitKey.last()] = v
            }
        }.toJsonString()
    }
}