package net.corda.comp.kafka.config.read

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
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

) : ConfigListener {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    private var receivedSnapshot = false

    private val configReadService = readServiceFactory.createReadService()

    fun startReader() {
        configReadService.registerCallback(this)
    }

    fun isReady(): Boolean {
        return receivedSnapshot
    }

    private fun snapshotReceived() {
        receivedSnapshot = true
    }

    override fun onSnapshot(currentConfigurationSnapshot: Map<String, Config>) {
        snapshotReceived()
        logger.info("----------List of available configurations----------")
        for(config in currentConfigurationSnapshot) {
            logger.info("${config.key} -> ${config.value}")
        }
    }

    override fun onUpdate(changedKey: String, currentConfigurationSnapshot: Map<String, Config>) {
        with(logger) {
            info("----------New configuration has been posted----------")
            info("$changedKey -> ${currentConfigurationSnapshot[changedKey]}")
        }
    }

}
