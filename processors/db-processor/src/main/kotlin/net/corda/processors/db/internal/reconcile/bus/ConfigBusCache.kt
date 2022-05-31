package net.corda.processors.db.internal.reconcile.bus

import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import net.corda.configuration.write.publish.ConfigurationDto
import net.corda.data.config.Configuration as ConfigurationAvro
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.VersionedRecord

/**
 * This not a [Lifecycle]. However, it has a [LifecycleCoordinator] because [ReconcilerImpl] is made to
 * depend on [Lifecycle]s. So [ConfigBusCache] needs to notify [ReconcilerImpl] that it is ready.
 */
class ConfigBusCache(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) : ReconcilerReader<String, ConfigurationDto> {
    private val configCache = ConcurrentHashMap<String, ConfigurationDto>()

    fun add(sectionToConfiguration: Pair<String, ConfigurationAvro?>) {
        val section = sectionToConfiguration.first
        val configAvro = sectionToConfiguration.second
        if (configAvro != null) {
            val configDto = ConfigurationDto.fromAvro(section, configAvro)
            configCache[section] = configDto
        } else {
            configCache.remove(section)
        }
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<String, ConfigurationDto>>? =
        configCache.values
            .toList()
            .stream().map {
                object : VersionedRecord<String, ConfigurationDto> {
                    override val version = it.version
                    override val isDeleted = false
                    override val key = it.section
                    override val value = it
                }
            }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName.forComponent<ConfigBusCache>()

    private val coordinator =
        lifecycleCoordinatorFactory.createCoordinator(lifecycleCoordinatorName) { event, coordinator ->
            when (event) {
                is StartEvent -> {
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
                // We need to make sure somehow this doesn't leak upon close, i.e. its coordinator.
            }
    }

    init {
        coordinator.start()
    }
}