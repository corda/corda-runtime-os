package net.corda.processors.db.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.db.DBProcessor
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = DBConfigManager::class)
    private val dbConfigManager: DBConfigManager
): DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, config: SmartConfig) {
        dbConfigManager.coordinator.postEvent(StartListentingEvent(instanceId, config))
    }

    override fun stop() {
        dbConfigManager.coordinator.stop()
    }
}

