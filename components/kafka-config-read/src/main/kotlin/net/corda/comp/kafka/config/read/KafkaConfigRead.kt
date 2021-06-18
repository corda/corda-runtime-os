package net.corda.comp.kafka.config.read

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.LifeCycle
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

/**
 * Used in conjunction with the KafkaConfigReader application to sanity check the implementation of the
 * configuration read logic
 */

@Component(immediate = true, service = [KafkaConfigRead::class])
class KafkaConfigRead @Activate constructor(
    @Reference(service = ConfigReadServiceFactory::class)
    private val readServiceFactory: ConfigReadServiceFactory
) : ConfigListener, LifeCycle {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    private var receivedSnapshot = false

    private val configReadService = readServiceFactory.createReadService()
    private var sub: AutoCloseable? = null

    override fun onUpdate(changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config>) {
        logger.info("----------New configuration has been posted----------")
        for (key in changedKeys) {
            logger.info("$key -> ${currentConfigurationSnapshot[key]}")
        }

        receivedSnapshot = true
    }

    override val isRunning: Boolean
        get() = receivedSnapshot

    override fun start() {
        sub = configReadService.registerCallback(this)
        configReadService.start()
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}
