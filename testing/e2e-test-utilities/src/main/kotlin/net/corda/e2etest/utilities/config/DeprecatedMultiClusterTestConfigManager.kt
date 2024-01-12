package net.corda.e2etest.utilities.config

import net.corda.e2etest.utilities.ClusterInfo

class DeprecatedMultiClusterTestConfigManager(
    clusterInfos: Collection<ClusterInfo>
): DeprecatedTestConfigManager, AutoCloseable {
    private val configManagers: MutableSet<DeprecatedTestConfigManager> = mutableSetOf()

    init {
        configManagers.addAll(clusterInfos.map { DeprecatedSingleClusterTestConfigManager(it) })
    }

    override fun load(section: String, props: Map<String, Any?>): DeprecatedTestConfigManager {
        configManagers.forEach { it.load(section, props) }
        return this
    }
    override fun load(section: String, prop: String, value: Any?): DeprecatedTestConfigManager {
        configManagers.forEach { it.load(section, prop, value) }
        return this
    }
    override fun apply(): DeprecatedTestConfigManager {
        configManagers.forEach { it.apply() }
        return this
    }
    override fun revert(): DeprecatedTestConfigManager {
        configManagers.forEach { it.revert() }
        return this
    }
    override fun close() = configManagers.forEach { it.close() }
}