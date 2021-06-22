package net.corda.comp.kafka.config.read

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.LifeCycle
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
    @Reference(service = ConfigReadServiceFactory::class)
    private val readServiceFactory: ConfigReadServiceFactory
) : LifeCycle {

    private companion object {
        private val logger: Logger = contextLogger()
    }

    private var receivedSnapshot = false

    private var configReadService: ConfigReadService? = null
    private var sub: AutoCloseable? = null
    private var bootstrapConfig: Config? = null

    override val isRunning: Boolean
        get() = receivedSnapshot

    fun start(bootstrapConfig: Config) {
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        if(bootstrapConfig != null){
            configReadService = readServiceFactory.createReadService(bootstrapConfig!!)
            val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
                logger.info("----------New configuration has been posted----------")
                for (key in changedKeys) {
                    logger.info("$key -> ${currentConfigurationSnapshot[key]}")
                }

                receivedSnapshot = true
            }
            sub = configReadService!!.registerCallback(lister)
            configReadService!!.start()
        } else {
            throw CordaRuntimeException("Use the other start method available and pass in the bootstrap configuration")
        }
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}
