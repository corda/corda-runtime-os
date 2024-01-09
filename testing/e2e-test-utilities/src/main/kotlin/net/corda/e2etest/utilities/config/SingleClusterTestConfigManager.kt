package net.corda.e2etest.utilities.config

import com.fasterxml.jackson.databind.JsonNode
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import net.corda.e2etest.utilities.ClusterInfo
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class SingleClusterTestConfigManager(
    private val clusterInfo: ClusterInfo = DEFAULT_CLUSTER
) : TestConfigManager {

    private data class LockKey(
        val clusterKey: String,
        val configurationSection: String,
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val GET_CONFIG_TIMEOUT_SECONDS = 30L
        private val lock = ConcurrentHashMap<LockKey, Lock>()
    }

    private val originalConfigs: MutableMap<String, Config> = ConcurrentHashMap()
    private val overrides: MutableMap<String, Config> = ConcurrentSkipListMap()

    override fun load(section: String, props: Map<String, Any?>): TestConfigManager {
        overrides.compute(section) { _, v ->
            ConfigFactory.parseMap(props).withFallback(v ?: ConfigFactory.empty())
        }
        return this
    }

    override fun load(section: String, prop: String, value: Any?): TestConfigManager {
        logger.info(
            "Loading test config \"$value\" for property \"$prop\" in section \"$section\" for cluster \"${clusterInfo.name}\" " +
                    "into TestConfigManager."
        )

        overrides.compute(section) { _, v ->
            ConfigFactory.parseString("$prop=$value").withFallback(v ?: ConfigFactory.empty())
        }

        return this
    }
    private fun apply(section: String, configOverride: Config) {
        val currentConfig = getConfig(section)

        val (previousVersion, previousSourceConfig) = with(currentConfig) {
            version to sourceConfig
        }
        val previousConfig = previousSourceConfig.takeIf {
            it.isNotBlank()
        }?.let {
            ConfigFactory.parseString(it)
        } ?: ConfigFactory.empty()

        // Store original config for later revert.
        originalConfigs.computeIfAbsent(section) { previousConfig }

        val mergedConfig = configOverride.withFallback(previousConfig).root().render(ConfigRenderOptions.concise())

        logger.info(
            "Updating from config \"$previousSourceConfig\" to \"$mergedConfig\" for section \"$section\" on " +
                    "cluster \"${clusterInfo.name}\"."
        )

        if(mergedConfig != previousSourceConfig) {
            updateConfig(mergedConfig, section)

            eventually(duration = Duration.ofSeconds(GET_CONFIG_TIMEOUT_SECONDS)) {
                with(getConfig(section)) {
                    assertThat(version).isNotEqualTo(previousVersion)
                    assertThat(sourceConfig).isEqualTo(mergedConfig)
                }
            }
        }
    }

    override fun <T> applyWithoutRevert(block: () -> T): T {
        val locks = overrides.keys.map { section ->
            lock.computeIfAbsent(
                LockKey(
                    clusterInfo.name,
                    section,
                )
            ) { ReentrantLock() }
        }

        return try {
            locks.forEach {
                it.lock()
            }
            overrides.forEach { (section, configOverride) ->
                apply(section, configOverride)
            }
            block()
        } finally {
            locks.forEach {
                it.unlock()
            }
        }
    }

    override fun <T> apply(block: () -> T): T {
        return applyWithoutRevert {
            try {
                block()
            } finally {
                revert()
            }
        }
    }

    private fun revert(): TestConfigManager {
        originalConfigs.forEach { (section, originalConfig) ->
            val (previousVersion, previousSourceConfig) = with(getConfig(section)) {
                version to sourceConfig
            }
            val preTestConfig = originalConfig.root().render(ConfigRenderOptions.concise())

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
        originalConfigs.clear()
        return this
    }

    private fun getConfig(section: String) = clusterInfo.getConfig(section)

    private fun updateConfig(config: String, section: String) = clusterInfo.updateConfig(config, section)

    private val JsonNode.version: Int
        get() = get("version").intValue()

    private val JsonNode.sourceConfig: String
        get() = get("sourceConfig").textValue()
}