package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.v5.base.util.contextLogger

abstract class ConfigurationAwareTile<C>(
    private val configurationReaderService: ConfigurationReadService,
    private val key: String,
    private val configFactory: (Config) -> C
) {
    companion object {
        private val logger = contextLogger()
    }

    @Volatile
    private var lastConfiguration: C? = null

    @Volatile
    private var configSet = false

    private inner class Handler : ConfigurationHandler {
        override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
            if (changedKeys.contains(key)) {
                val newConfiguration = config[key]
                if (newConfiguration != null) {
                    callFromCoordinator {
                        applyNewConfiguration(newConfiguration)
                        configSet = true
                        return@callFromCoordinator configSet
                    }
                }
            }
        }
    }

    private fun applyNewConfiguration(newConfiguration: Config) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val configuration = configFactory(newConfiguration)
            logger.info("Got configuration $name")
            if (configuration == lastConfiguration) {
                logger.info("Configuration had not changed $name")
                return
            } else {
                applyNewConfiguration(configuration, lastConfiguration, resources)
                lastConfiguration = configuration
                logger.info("Reconfigured $name")
            }
        } catch (e: Throwable) {
            gotError(e)
        }
    }

    abstract fun applyNewConfiguration(newConfiguration: C, oldConfiguration: C?, resources: ResourcesHolder)

}
