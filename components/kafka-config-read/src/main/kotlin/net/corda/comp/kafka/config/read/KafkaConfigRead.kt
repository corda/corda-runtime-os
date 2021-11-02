package net.corda.comp.kafka.config.read

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.exceptions.CordaRuntimeException
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
    @Reference(service = ConfigReaderFactory::class)
    private val readServiceFactory: ConfigReaderFactory
) : Lifecycle {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    private var receivedSnapshot = false

    private var configReader: ConfigReader? = null
    private var sub: AutoCloseable? = null
    private var bootstrapConfig: SmartConfig? = null

    override val isRunning: Boolean
        get() = receivedSnapshot

    fun start(bootstrapConfig: SmartConfig) {
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        if(bootstrapConfig != null){
            configReader = readServiceFactory.createReader(bootstrapConfig!!)
            val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig> ->
                logger.info("----------New configuration has been posted----------")
                for (key in changedKeys) {
                    logger.info("$key -> ${currentConfigurationSnapshot[key]}")
                }

                receivedSnapshot = true
            }
            sub = configReader!!.registerCallback(lister)
            configReader!!.start()
        } else {
            throw CordaRuntimeException("Use the other start method available and pass in the bootstrap configuration")
        }
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}
