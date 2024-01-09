package net.corda.e2etest.utilities.config

import net.corda.e2etest.utilities.ClusterInfo

class MultiClusterTestConfigManager(
    clusterInfos: Collection<ClusterInfo>
): TestConfigManager, AutoCloseable {
    private val configManagers: MutableSet<TestConfigManager> = mutableSetOf()

    init {
        configManagers.addAll(clusterInfos.map { SingleClusterTestConfigManager(it) })
    }

    override fun load(section: String, props: Map<String, Any?>): TestConfigManager {
        configManagers.iterator().apply {  }
        configManagers.forEach { it.load(section, props) }
        return this
    }
    override fun load(section: String, prop: String, value: Any?): TestConfigManager {
        configManagers.forEach { it.load(section, prop, value) }
        return this
    }
    override fun apply(): TestConfigManager {
        configManagers.forEach { it.apply() }
        return this
    }
    override fun revert(): TestConfigManager {
        configManagers.forEach { it.revert() }
        return this
    }
    override fun close() = configManagers.forEach { it.close() }
}