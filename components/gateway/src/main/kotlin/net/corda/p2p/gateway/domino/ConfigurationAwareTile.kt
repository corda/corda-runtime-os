package net.corda.p2p.gateway.domino

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class ConfigurationAwareTile<C>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    private val configFactory: (Config)->C
) :
    LeafTile(coordinatorFactory),
    ConfigurationHandler {

    companion object {
        const val CONFIG_KEY = "p2p.gateway"
        private val logger = contextLogger()
    }

    private var configurationHolder = AtomicReference<C>()

    private val canReceiveConfigurations = AtomicBoolean(false)
    private val registration = configurationReaderService.registerForUpdates(this)

    override fun close() {
        registration.close()
        super.close()
    }

    override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
        if (changedKeys.contains(CONFIG_KEY)) {
            val newConfiguration = config[CONFIG_KEY]
            if (newConfiguration != null) {
                applyNewConfiguration(newConfiguration)
            }
        }
    }

    private fun applyNewConfiguration(newConfiguration: Config) {
        val configuration = configFactory(newConfiguration)
        logger.info("Got configuration")
        val oldConfiguration = configurationHolder.getAndSet(configuration)
        if (oldConfiguration == configuration) {
            logger.info("Configuration had not changed")
            return
        } else if (canReceiveConfigurations.get()) {
            logger.info("Reconfiguring gateway $name")
            @Suppress("TooGenericExceptionCaught")
            try {
                applyNewConfiguration(configuration, oldConfiguration)
                updateState(State.Started)
                logger.info("Gateway reconfigured $name")
            } catch (e: Throwable) {
                gotError(e)
            }
        }
    }

    abstract fun applyNewConfiguration(newConfiguration: C, oldConfiguration: C?)

    override fun createResources() {
        if (configurationHolder.get() != null) {
            @Suppress("TooGenericExceptionCaught")
            try {
                applyNewConfiguration(configurationHolder.get(), null)
                updateState(State.Started)
            } catch (e: Throwable) {
                gotError(e)
            }
        }
        canReceiveConfigurations.set(true)
        executeBeforeStop {
            canReceiveConfigurations.set(false)
        }
    }
}
