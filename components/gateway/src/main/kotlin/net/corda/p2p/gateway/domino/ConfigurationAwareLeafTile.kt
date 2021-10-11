package net.corda.p2p.gateway.domino

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

abstract class ConfigurationAwareLeafTile<C>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    configurationReaderService: ConfigurationReadService,
    private val key: String,
    private val configFactory: (Config) -> C
) :
    LeafTile(coordinatorFactory) {

    companion object {
        private val logger = contextLogger()
    }

    private val configurationHolder = AtomicReference<C>()
    private val canReceiveConfigurations = AtomicBoolean(false)

    private inner class Handler : ConfigurationHandler {
        override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, Config>) {
            if (changedKeys.contains(key)) {
                val newConfiguration = config[key]
                if (newConfiguration != null) {
                    applyNewConfiguration(newConfiguration)
                }
            }
        }
    }

    private val registration = configurationReaderService.registerForUpdates(Handler())

    override fun close() {
        registration.close()
        super.close()
    }

    private fun applyNewConfiguration(newConfiguration: Config) {
        @Suppress("TooGenericExceptionCaught")
        try {
            val configuration = configFactory(newConfiguration)
            logger.info("Got configuration $name")
            val oldConfiguration = configurationHolder.getAndSet(configuration)
            if (oldConfiguration == configuration) {
                logger.info("Configuration had not changed $name")
                return
            } else if ((state == State.StoppedDueToError) || (canReceiveConfigurations.get())) {
                logger.info("Reconfiguring $name")
                applyNewConfiguration(configuration, oldConfiguration)
                canReceiveConfigurations.set(true)
                updateState(State.Started)
                logger.info("Reconfigured $name")
            }
        } catch (e: Throwable) {
            gotError(e)
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
