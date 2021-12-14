package net.corda.processors.db.internal

import net.corda.configuration.write.ConfigWriteService
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
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService
) : DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        configWriteService.start()
        // TODO - Joel - Use atKey/atPath to stuff topic prefix into config.
        configWriteService.bootstrapConfig(config, instanceId)
    }

    override fun stop() {
        configWriteService.stop()
    }
}