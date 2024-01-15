package net.corda.e2etest.utilities.config

import net.corda.e2etest.utilities.ClusterInfo

class MultiClusterTestConfigManager(
    clusterInfos: Collection<ClusterInfo>
): TestConfigManager {
    private val configManagers = clusterInfos.map {
        SingleClusterTestConfigManager(it)
    }

    override fun load(section: String, props: Map<String, Any?>): TestConfigManager {
        configManagers.forEach { it.load(section, props) }
        return this
    }
    override fun load(section: String, prop: String, value: Any?): TestConfigManager {
        configManagers.forEach { it.load(section, prop, value) }
        return this
    }

    override fun <T> apply(block: () -> T): T {
        return configManagers.iterator().apply(block)
    }

    override fun <T> applyWithoutRevert(block: () -> T): T {
        return configManagers.iterator().applyWithoutRevert(block)
    }

    private fun <T> Iterator<TestConfigManager>.apply(block: () -> T): T {
        return if(!hasNext()) {
            block()
        } else {
            next().apply {
                this.apply(block)
            }
        }
    }

    private fun <T> Iterator<TestConfigManager>.applyWithoutRevert(block: () -> T): T {
        return if(!hasNext()) {
            block()
        } else {
            next().applyWithoutRevert {
                this.applyWithoutRevert(block)
            }
        }
    }

}