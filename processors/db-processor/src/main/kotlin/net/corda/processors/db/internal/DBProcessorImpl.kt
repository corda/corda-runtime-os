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
    @Reference(service = ConfigWriter::class)
    private val configWriter: ConfigWriter
): DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        configWriter.coordinator.postEvent(ConfigProvidedEvent(config, instanceId))
    }

    override fun stop() {
        configWriter.coordinator.stop()
    }
}