package net.corda.processors.db.internal

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.write.ConfigWriteService
import net.corda.libs.configuration.SmartConfig
import net.corda.processors.db.DBProcessor
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
        // A `Config` object containing the database defaults.
        private val dbDefaultsConfig = ConfigFactory.empty()
            .withValue(CONFIG_DB_DRIVER, ConfigValueFactory.fromAnyRef(CONFIG_DB_DRIVER_DEFAULT))
            .withValue(CONFIG_JDBC_URL, ConfigValueFactory.fromAnyRef(CONFIG_JDBC_URL_DEFAULT))
            .withValue(CONFIG_DB_USER, ConfigValueFactory.fromAnyRef(CONFIG_DB_USER_DEFAULT))
            .withValue(CONFIG_DB_PASS, ConfigValueFactory.fromAnyRef(CONFIG_DB_PASS_DEFAULT))
    }

    override fun start(instanceId: Int, topicPrefix: String, config: SmartConfig) {
        val augmentedConfig = augmentConfig(config, topicPrefix)

        configWriteService.start()
        configWriteService.startProcessing(augmentedConfig, instanceId)
    }

    override fun stop() {
        configWriteService.stop()
    }

    /** Augments the existing [config] with the topic prefix as a value, and the database defaults as a fallback. */
    private fun augmentConfig(config: SmartConfig, topicPrefix: String) = config
        .withValue(CONFIG_MESSAGING_TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(topicPrefix))
        .withFallback(dbDefaultsConfig)
}