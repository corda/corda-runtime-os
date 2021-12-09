package net.corda.processors.db.internal

import net.corda.libs.configuration.SmartConfig
import net.corda.processors.db.DBProcessor
import net.corda.processors.db.internal.config.writer.ConfigEntity
import net.corda.processors.db.internal.config.writer.ConfigWriteService
import net.corda.processors.db.internal.db.DBWriter
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** The processor for a `DBWorker`. */
@Suppress("Unused")
@Component(service = [DBProcessor::class])
class DBProcessorImpl @Activate constructor(
    @Reference(service = ConfigWriteService::class)
    private val configWriteService: ConfigWriteService,
    @Reference(service = DBWriter::class)
    private val dbWriter: DBWriter
) : DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        dbWriter.start()
        dbWriter.bootstrapConfig(config, setOf(ConfigEntity::class.java))

        configWriteService.start()
        // TODO - Joel - Pass in the topic prefix and use it.
        configWriteService.bootstrapConfig(config, instanceId)
    }

    override fun stop() {
        dbWriter.stop()
        configWriteService.stop()
    }
}