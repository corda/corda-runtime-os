package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.v5.base.util.contextLogger

abstract class ConfigurationAwareLeafTile<C>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val configurationReaderService: ConfigurationReadService,
    private val key: String,
    private val configFactory: (Config) -> C
) :
    DominoTile(coordinatorFactory) {

    companion object {
        private val logger = contextLogger()
    }

    @Volatile
    private var lastConfiguration: C? = null

    protected val resources = ResourcesHolder()

    private inner class Handler : ConfigurationHandler {
        override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
            if (changedKeys.contains(key)) {
                val newConfiguration = config[key]
                if (newConfiguration != null) {
                    callFromCoordinator {
                        applyNewConfiguration(newConfiguration)
                    }
                }
            }
        }
    }

    @Volatile
    private var registration: AutoCloseable? = null

    private fun applyNewConfiguration(newConfiguration: Config) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val configuration = configFactory(newConfiguration)
            logger.info("Got configuration $name")
            if (configuration == lastConfiguration) {
                logger.info("Configuration had not changed $name")
                return
            } else {
                applyNewConfiguration(configuration, lastConfiguration)
                lastConfiguration = configuration
                started()
                logger.info("Reconfigured $name")
            }
        } catch (e: Throwable) {
            gotError(e)
        }
    }

    abstract fun applyNewConfiguration(newConfiguration: C, oldConfiguration: C?)

    override fun startTile() {
        if (registration == null) {
            registration = configurationReaderService.registerForUpdates(Handler())
        }
    }

    override fun stopTile(dueToError: Boolean) {
        resources.close()
        if (!dueToError) {
            lastConfiguration = null
            registration?.close()
            registration = null
        }
    }
}
